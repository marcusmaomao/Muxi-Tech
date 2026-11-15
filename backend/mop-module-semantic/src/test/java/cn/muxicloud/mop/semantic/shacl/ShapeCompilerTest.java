package cn.muxicloud.mop.semantic.shacl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.semantic.shacl.ShaclEngine.Finding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ShapeCompilerTest {
    private static final String NAMESPACE = "https://example.com/ont/ac#";
    private final ShapeCompiler compiler = new ShapeCompiler();
    private final ShaclEngine engine = new ShaclEngine();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requiredRangeAgreesWithJenaAndPyShacl() throws Exception {
        String shapes = compiler.compile(voltageModel()).turtle();
        assertThat(shapes).contains("NodeShape");
        assertThat(shapes).contains("minCount");
        assertThat(shapes).contains("minInclusive");
        assertThat(shapes).contains("maxInclusive");
        assertThat(shapes).contains("voltage");
        String data = """
                @prefix ex: <https://example.com/ont/ac#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                ex:missing a ex:Transformer .
                ex:ok a ex:Transformer ; ex:voltage "220.0"^^xsd:decimal .
                ex:high a ex:Transformer ; ex:voltage "1500"^^xsd:decimal .
                """;
        Set<String> jena = failingFocus(engine.check(shapes, data).findings());
        assertThat(jena).containsExactlyInAnyOrder(NAMESPACE + "missing", NAMESPACE + "high");
        assertThat(engine.check(shapes, """
                @prefix ex: <https://example.com/ont/ac#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                ex:ok a ex:Transformer ; ex:voltage "220.0"^^xsd:decimal .
                """).conforms()).isTrue();
        JsonNode python = pyShacl(shapes, data);
        assertThat(python.path("conforms").asBoolean()).isFalse();
        assertThat(focus(python)).containsExactlyInAnyOrderElementsOf(jena);
    }

    @Test
    void dataRuleEnumLinkAndGroupCompile() {
        CatalogSnapshot snapshot = new CatalogSnapshot(NAMESPACE, List.of(
                element("tx", "OBJECT_TYPE", "Transformer", null, "{\"displayName\":\"变压器\",\"apiName\":\"Transformer\"}"),
                element("st", "OBJECT_TYPE", "Substation", null, "{\"displayName\":\"变电站\",\"apiName\":\"Substation\"}"),
                element("en", "ENUM_TYPE", "RunState", null, "{\"displayName\":\"运行状态\",\"apiName\":\"RunState\"}"),
                element("ev", "ENUM_VALUE", "Running", "en", "{\"displayName\":\"运行\",\"apiName\":\"Running\",\"ownerUid\":\"en\"}"),
                element("pr", "PROPERTY", "state", "tx", "{\"displayName\":\"状态\",\"apiName\":\"state\",\"ownerUid\":\"tx\",\"valueType\":{\"kind\":\"ENUM\",\"enumUid\":\"en\"}}"),
                element("lk", "LINK_TYPE", "installedAt", null, "{\"displayName\":\"安装于\",\"apiName\":\"installedAt\",\"sourceUid\":\"tx\",\"targetUid\":\"st\",\"cardinality\":\"MANY_TO_ONE\"}"),
                element("rule", "DATA_RULE", "voltageRange", null, "{\"kind\":\"AND\",\"targetObjectUid\":\"tx\",\"conditions\":[{\"kind\":\"RANGE\",\"targetPropertyUid\":\"volts\",\"min\":0,\"max\":1000},{\"kind\":\"PATTERN\",\"targetPropertyUid\":\"code\",\"pattern\":\"A[0-9]+\"}]}"),
                element("volts", "PROPERTY", "ratedVoltage", "tx", "{\"displayName\":\"额定电压\",\"apiName\":\"ratedVoltage\",\"ownerUid\":\"tx\",\"required\":true,\"valueType\":{\"kind\":\"BASIC\",\"basic\":\"DECIMAL\"}}"),
                element("code", "PROPERTY", "assetCode", "tx", "{\"displayName\":\"编码\",\"apiName\":\"assetCode\",\"ownerUid\":\"tx\",\"valueType\":{\"kind\":\"BASIC\",\"basic\":\"STRING\"}}")
        ), 1L);
        String turtle = compiler.compile(snapshot).turtle();
        assertThat(turtle).contains("Running");
        assertThat(turtle).contains("Substation");
        assertThat(turtle).contains("maxCount");
        assertThat(turtle).contains("sh:and");
        assertThat(turtle).contains("minInclusive");
        assertThat(turtle).contains("A[0-9]+");
    }

    @Test
    void invalidCustomShapeIsReported() {
        CatalogSnapshot snapshot = new CatalogSnapshot(NAMESPACE, List.of(
                element("shape", "SHAPE", "extra", null, "{\"turtle\":\"this is not turtle\"}")
        ), 1L);
        assertThat(compiler.compile(snapshot).errors()).anyMatch(error -> error.contains("无法解析"));
    }

    private CatalogSnapshot voltageModel() {
        return new CatalogSnapshot(NAMESPACE, List.of(
                element("tx", "OBJECT_TYPE", "Transformer", null, "{\"displayName\":\"变压器\",\"apiName\":\"Transformer\"}"),
                element("pr", "PROPERTY", "voltage", "tx", "{\"displayName\":\"电压\",\"apiName\":\"voltage\",\"ownerUid\":\"tx\",\"required\":true,\"minimum\":0,\"maximum\":1000,\"valueType\":{\"kind\":\"BASIC\",\"basic\":\"DECIMAL\"}}")
        ), 1L);
    }

    private static Set<String> failingFocus(List<Finding> findings) {
        return findings.stream().map(Finding::focusNode).collect(Collectors.toSet());
    }

    private JsonNode pyShacl(String shapes, String data) throws Exception {
        Path dir = Files.createTempDirectory("mop-shacl");
        Path shapesFile = dir.resolve("shapes.ttl");
        Path dataFile = dir.resolve("data.ttl");
        Path script = dir.resolve("compare_pyshacl.py");
        Files.writeString(shapesFile, shapes);
        Files.writeString(dataFile, data);
        try (InputStream in = ShapeCompilerTest.class.getResourceAsStream("/shacl/compare_pyshacl.py")) {
            Files.write(script, in.readAllBytes());
        }
        Process process = new ProcessBuilder("python3", script.toString(), shapesFile.toString(), dataFile.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertThat(code).as(output).isZero();
        return objectMapper.readTree(output);
    }

    private static Set<String> focus(JsonNode python) {
        Set<String> values = new java.util.HashSet<>();
        python.path("focus").forEach(node -> values.add(node.asText()));
        return values;
    }

    private static CatalogElement element(String uid, String type, String apiName, String ownerUid, String payload) {
        return new CatalogElement(uid, type, apiName, ownerUid, payload);
    }
}
