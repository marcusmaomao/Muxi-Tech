package cn.muxicloud.mop.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OwlExportIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("mop.jwt.secret", () -> "integration-test-jwt-secret-32bytes");
        registry.add("mop.demo.seed", () -> "true");
        registry.add("mop.demo.admin-username", () -> "admin");
        registry.add("mop.demo.admin-password", () -> "demo-admin");
        registry.add("mop.captcha.expose", () -> "true");
        registry.add("mop.datasource.secret", () -> "integration-datasource-secret");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportRoundTripsAndKeepsIrisAfterAnotherCommit() throws Exception {
        String token = token();
        long projectId = id(postJson(token, "/api/v1/projects", objectMapper.createObjectNode()
                .put("code", "owl")
                .put("name", "导出")
                .put("namespace", "https://muxi.example/ont/grid#")));
        long branchId = objectMapper.readTree(mvc.perform(get("/api/v1/projects/" + projectId + "/branches").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        String root = "/api/v1/branches/" + branchId;
        String cqUid = uid(postJson(token, root + "/cqs", objectMapper.createObjectNode().put("displayName", "有哪些设备").put("apiName", "whichAssets")));
        ObjectNode type = objectMapper.createObjectNode().put("displayName", "变压器").put("apiName", "Transformer");
        type.putArray("cqUids").add(cqUid);
        String typeUid = uid(postJson(token, root + "/object-types", type));
        ObjectNode property = objectMapper.createObjectNode()
                .put("displayName", "额定电压")
                .put("apiName", "ratedVoltage")
                .put("ownerUid", typeUid)
                .put("minCount", 1)
                .put("maxCount", 1);
        property.putObject("valueType").put("kind", "BASIC").put("basic", "DECIMAL");
        postJson(token, root + "/properties", property);

        byte[] first = export(token, root, "ttl");
        String iri = "https://muxi.example/ont/grid#Transformer";
        assertThat(classIris(first)).contains(iri);
        assertThat(new String(first)).doesNotContain(typeUid);
        OWLOntology reloaded = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new ByteArrayInputStream(first));
        assertThat(reloaded.classesInSignature().map(item -> item.getIRI().toString())).contains(iri);
        assertThat(reloaded.axioms().anyMatch(axiom -> axiom.toString().contains("Cardinality"))).isTrue();
        Model jena = ModelFactory.createDefaultModel();
        RDFDataMgr.read(jena, new ByteArrayInputStream(first), Lang.TURTLE);
        assertThat(jena.containsResource(jena.createResource(iri))).isTrue();

        postJson(token, root + "/terms", objectMapper.createObjectNode().put("displayName", "变压器").put("apiName", "transformerTerm"));
        byte[] second = export(token, root, "ttl");
        assertThat(classIris(second)).contains(iri);
        assertThat(classIris(second)).containsAll(classIris(first));

        byte[] rdf = export(token, root, "rdfxml");
        Model rdfModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(rdfModel, new ByteArrayInputStream(rdf), Lang.RDFXML);
        assertThat(rdfModel.containsResource(rdfModel.createResource(iri))).isTrue();
        OWLOntology owl = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(new ByteArrayInputStream(export(token, root, "owl")));
        assertThat(owl.classesInSignature().map(item -> item.getIRI().toString())).contains(iri);
    }

    private byte[] export(String token, String root, String format) throws Exception {
        return mvc.perform(get(root + "/export").param("format", format).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private MvcResult postJson(String token, String path, JsonNode body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn();
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String uid(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("uid").asText();
    }

    private static Set<String> classIris(byte[] turtle) throws Exception {
        OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new ByteArrayInputStream(turtle));
        return ontology.classesInSignature().map(type -> type.getIRI().toString()).collect(Collectors.toSet());
    }

    private String token() throws Exception {
        MvcResult captcha = mvc.perform(get("/api/v1/auth/captcha")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(captcha.getResponse().getContentAsString()).path("data");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", "admin");
        payload.put("password", "demo-admin");
        payload.put("captchaId", body.path("captchaId").asText());
        payload.put("captchaCode", body.path("debugCode").asText());
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
