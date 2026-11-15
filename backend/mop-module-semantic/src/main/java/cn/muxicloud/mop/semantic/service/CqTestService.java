package cn.muxicloud.mop.semantic.service;

import cn.muxicloud.mop.common.cq.CqCypherPolicy;
import cn.muxicloud.mop.common.cq.CqGraph;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.api.BranchCatalog;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.semantic.cq.CqAssertions;
import cn.muxicloud.mop.semantic.cq.CqCoverage;
import cn.muxicloud.mop.semantic.cq.CqCoverage.CaseResult;
import cn.muxicloud.mop.semantic.cq.CqCoverage.Report;
import cn.muxicloud.mop.semantic.cq.SparqlRunner;
import cn.muxicloud.mop.semantic.entity.SemIssueEntity;
import cn.muxicloud.mop.semantic.entity.SemRunEntity;
import cn.muxicloud.mop.semantic.export.OwlExporter;
import cn.muxicloud.mop.semantic.mapper.SemIssueMapper;
import cn.muxicloud.mop.semantic.mapper.SemRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CqTestService {
    private final BranchCatalog catalog;
    private final CqGraph graph;
    private final SemRunMapper runMapper;
    private final SemIssueMapper issueMapper;
    private final OwlExporter exporter = new OwlExporter();
    private final SparqlRunner sparql = new SparqlRunner();
    private final ObjectMapper objectMapper;

    public CqTestService(BranchCatalog catalog, CqGraph graph, SemRunMapper runMapper, SemIssueMapper issueMapper, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.graph = graph;
        this.runMapper = runMapper;
        this.issueMapper = issueMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RunView run(long branchId, String cqUid, String testUid, Double threshold, AuthenticatedUser actor) {
        CatalogSnapshot snapshot = catalog.snapshot(branchId);
        List<CatalogElement> tests = select(snapshot, cqUid, testUid);
        double gateThreshold = threshold == null ? 1.0 : threshold;
        SemRunEntity run = new SemRunEntity();
        run.setTenantId(actor.tenantId());
        run.setProjectId(snapshot.projectId());
        run.setBranchId(branchId);
        run.setKind("CQ");
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);
        long fixtureTenant = -run.getId();
        List<One> executed = new ArrayList<>();
        try {
            for (CatalogElement test : tests) {
                executed.add(execute(snapshot, test, actor.tenantId(), fixtureTenant));
            }
        } finally {
            graph.clear(fixtureTenant);
        }
        List<CaseResult> coverageInput = executed.stream()
                .map(item -> new CaseResult(item.cqUid(), item.passed(), item.query(), item.covers()))
                .toList();
        Report coverage = CqCoverage.calculate(snapshot.namespace(), snapshot.elements(), coverageInput);
        boolean open = coverage.gateOpen(gateThreshold);
        for (One item : executed) {
            if (item.passed()) {
                continue;
            }
            SemIssueEntity issue = new SemIssueEntity();
            issue.setTenantId(actor.tenantId());
            issue.setRunId(run.getId());
            issue.setCheckCode("CQ_FAIL");
            issue.setSeverity("Violation");
            issue.setElementUid(item.testUid());
            issue.setMessage(clip(item.message(), 1000));
            issue.setState("OPEN");
            issueMapper.insert(issue);
        }
        run.setStatus("SUCCEEDED");
        run.setFinishedAt(LocalDateTime.now());
        run.setSummary(write(new Stored(gateThreshold, open, coverage, executed)));
        runMapper.updateById(run);
        return view(run, gateThreshold, open, coverage, executed);
    }

    public RunView get(long runId) {
        SemRunEntity run = runMapper.selectById(runId);
        if (run == null || !"CQ".equals(run.getKind())) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        Stored stored = read(run.getSummary());
        return view(run, stored.threshold(), stored.gateOpen(), stored.coverage(), stored.cases());
    }

    private One execute(CatalogSnapshot snapshot, CatalogElement test, long liveTenant, long fixtureTenant) {
        long started = System.nanoTime();
        JsonNode payload = readTree(test.payload());
        String query = text(payload, "query");
        String language = text(payload, "language");
        Set<String> covers = covers(payload);
        String cqUid = test.ownerUid() == null ? text(payload, "ownerUid") : test.ownerUid();
        try {
            if ("SCHEMA_ONLY".equals(text(payload.path("assert"), "kind"))) {
                String missing = missingSchema(snapshot, query);
                return finish(test, cqUid, query, covers, missing == null, missing == null ? "通过" : missing, started);
            }
            Outcome outcome = "CYPHER".equals(language)
                    ? cypher(snapshot, payload, query, liveTenant, fixtureTenant)
                    : sparql(snapshot, payload, query);
            String failure = CqAssertions.check(payload.get("assert"), outcome.ask(), outcome.askValue(), outcome.rows());
            return finish(test, cqUid, query, covers, failure == null, failure == null ? "通过" : failure, started);
        } catch (MopException ex) {
            return finish(test, cqUid, query, covers, false, ex.getMessage(), started);
        }
    }

    private Outcome sparql(CatalogSnapshot snapshot, JsonNode payload, String query) {
        String tbox = new String(exporter.export(snapshot, "ttl").body(), StandardCharsets.UTF_8);
        String fixture = fixtureTurtle(snapshot, text(payload, "fixtureUid"));
        SparqlRunner.Result result = sparql.run(query, tbox, fixture);
        return new Outcome(result.ask(), result.askValue(), result.rows());
    }

    private Outcome cypher(CatalogSnapshot snapshot, JsonNode payload, String query, long liveTenant, long fixtureTenant) {
        CqCypherPolicy.check(query);
        String fixtureUid = text(payload, "fixtureUid");
        long tenant = liveTenant;
        if (fixtureUid != null) {
            JsonNode fixture = fixturePayload(snapshot, fixtureUid);
            graph.clear(fixtureTenant);
            graph.loadFixture(fixtureTenant, nodes(fixture.get("objects")), edges(fixture.get("links")));
            tenant = fixtureTenant;
        }
        return new Outcome(false, null, graph.read(query, tenant));
    }

    private String fixtureTurtle(CatalogSnapshot snapshot, String fixtureUid) {
        if (fixtureUid == null) {
            return "";
        }
        return text(fixturePayload(snapshot, fixtureUid), "turtle");
    }

    private JsonNode fixturePayload(CatalogSnapshot snapshot, String fixtureUid) {
        CatalogElement fixture = snapshot.elements().stream()
                .filter(element -> fixtureUid.equals(element.uid()) && "FIXTURE".equals(element.type()))
                .findFirst()
                .orElseThrow(() -> new MopException(ErrorCode.VALIDATION, "夹具不存在", null));
        return readTree(fixture.payload());
    }

    private static String missingSchema(CatalogSnapshot snapshot, String query) {
        List<String> missing = new ArrayList<>();
        for (String token : tokens(query)) {
            if (!looksLikeName(token) || ignored(token)) {
                continue;
            }
            boolean known = snapshot.elements().stream().anyMatch(element -> token.equals(element.apiName())
                    && ("OBJECT_TYPE".equals(element.type()) || "PROPERTY".equals(element.type()) || "LINK_TYPE".equals(element.type())));
            if (!known) {
                missing.add(token);
            }
        }
        return missing.isEmpty() ? null : "本体中没有 " + String.join("、", missing);
    }

    private static boolean ignored(String token) {
        return token.equals(token.toUpperCase(Locale.ROOT)) || Set.of(
                "ObjectProperty", "DatatypeProperty", "Class", "Restriction", "Ontology", "Thing", "Nothing").contains(token);
    }

    private static List<String> tokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < query.length(); index++) {
            char ch = query.charAt(index);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                current.append(ch);
            } else if (!current.isEmpty()) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static boolean looksLikeName(String token) {
        return token.length() > 1 && Character.isUpperCase(token.charAt(0));
    }

    private List<CqGraph.Node> nodes(JsonNode values) {
        List<CqGraph.Node> nodes = new ArrayList<>();
        if (values == null || !values.isArray()) {
            return nodes;
        }
        for (JsonNode value : values) {
            Map<String, String> props = new java.util.LinkedHashMap<>();
            JsonNode raw = value.get("props");
            if (raw != null && raw.isObject()) {
                raw.fields().forEachRemaining(field -> props.put(field.getKey(), field.getValue().asText()));
            }
            nodes.add(new CqGraph.Node(text(value, "type"), text(value, "pk"), props));
        }
        return nodes;
    }

    private List<CqGraph.Edge> edges(JsonNode values) {
        List<CqGraph.Edge> edges = new ArrayList<>();
        if (values == null || !values.isArray()) {
            return edges;
        }
        for (JsonNode value : values) {
            edges.add(new CqGraph.Edge(text(value, "type"), text(value, "fromType"), text(value, "fromPk"), text(value, "toType"), text(value, "toPk")));
        }
        return edges;
    }

    private static Set<String> covers(JsonNode payload) {
        Set<String> covers = new HashSet<>();
        JsonNode values = payload.get("covers");
        if (values != null && values.isArray()) {
            values.forEach(value -> {
                if (value.isTextual()) {
                    covers.add(value.asText());
                }
            });
        }
        return covers;
    }

    private static List<CatalogElement> select(CatalogSnapshot snapshot, String cqUid, String testUid) {
        return snapshot.elements().stream()
                .filter(element -> "CQ_TEST".equals(element.type()))
                .filter(element -> testUid == null || testUid.isBlank() || testUid.equals(element.uid()))
                .filter(element -> cqUid == null || cqUid.isBlank() || cqUid.equals(element.ownerUid()))
                .toList();
    }

    private static One finish(CatalogElement test, String cqUid, String query, Set<String> covers, boolean passed, String message, long started) {
        long millis = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        return new One(test.uid(), cqUid, test.apiName(), passed, message, query, covers, millis);
    }

    private RunView view(SemRunEntity run, double threshold, boolean open, Report coverage, List<One> cases) {
        return new RunView(run.getId(), run.getStatus(), open, threshold, coverage, cases.stream()
                .map(item -> new CaseView(item.testUid(), item.cqUid(), item.apiName(), item.passed(), item.message(), item.millis()))
                .toList());
    }

    private String write(Stored stored) {
        try {
            return objectMapper.writeValueAsString(stored);
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "测试结果无法保存", null);
        }
    }

    private Stored read(String summary) {
        try {
            return objectMapper.readValue(summary, Stored.class);
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "测试结果无法读取", null);
        }
    }

    private JsonNode readTree(String payload) {
        try {
            return payload == null || payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }

    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record Outcome(boolean ask, Boolean askValue, List<Map<String, String>> rows) {}

    public record One(String testUid, String cqUid, String apiName, boolean passed, String message, String query, Set<String> covers, long millis) {}

    public record CaseView(String testUid, String cqUid, String apiName, boolean passed, String message, long millis) {}

    public record RunView(Long runId, String status, boolean gateOpen, double threshold, Report coverage, List<CaseView> cases) {}

    public record Stored(double threshold, boolean gateOpen, Report coverage, List<One> cases) {}
}
