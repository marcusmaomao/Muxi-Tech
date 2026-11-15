package cn.muxicloud.mop.runtime.graph;

import cn.muxicloud.mop.common.cq.CqCypherPolicy;
import cn.muxicloud.mop.common.cq.CqGraph;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.tenant.TenantCypherGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionConfig;
import org.springframework.stereotype.Component;

import static org.neo4j.driver.Values.parameters;

@Component
public class CqGraphStore implements CqGraph {
    private static final String CLEAR = """
            MATCH (n:MopObject)
            WHERE n._t = $tenant
            DETACH DELETE n
            """;
    private final Driver driver;
    private final ObjectMapper json = new ObjectMapper();

    public CqGraphStore(Driver driver) {
        this.driver = driver;
    }

    @Override
    public List<Map<String, String>> read(String cypher, long tenant) {
        CqCypherPolicy.check(cypher);
        TransactionConfig timeout = TransactionConfig.builder().withTimeout(Duration.ofSeconds(10)).build();
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(cypher, parameters("tenant", tenant));
                List<Map<String, String>> rows = new ArrayList<>();
                while (result.hasNext() && rows.size() < 100) {
                    var record = result.next();
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String key : record.keys()) {
                        row.put(key, record.get(key).toString().replace("\"", ""));
                    }
                    rows.add(row);
                }
                return rows;
            }, timeout);
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "Cypher 执行失败", null);
        }
    }

    @Override
    public void loadFixture(long tenant, List<Node> nodes, List<Edge> edges) {
        try (Session session = driver.session()) {
            if (nodes != null && !nodes.isEmpty()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Node node : nodes) {
                    TenantCypherGuard.requireApiName(node.type());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", node.type());
                    row.put("pk", node.pk());
                    row.put("rid", "fixture:" + node.type() + ":" + node.pk());
                    row.put("src", "FIXTURE");
                    row.put("propsJson", json.writeValueAsString(node.props() == null ? Map.of() : node.props()));
                    rows.add(row);
                }
                session.run(TenantCypherGuard.MERGE_OBJECTS, parameters("tenant", tenant, "rows", rows)).consume();
            }
            if (edges != null && !edges.isEmpty()) {
                List<Map<String, Object>> links = new ArrayList<>();
                for (Edge edge : edges) {
                    TenantCypherGuard.requireApiName(edge.type());
                    TenantCypherGuard.requireApiName(edge.fromType());
                    TenantCypherGuard.requireApiName(edge.toType());
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("linkType", edge.type());
                    link.put("fromType", edge.fromType());
                    link.put("fromPk", edge.fromPk());
                    link.put("toType", edge.toType());
                    link.put("toPk", edge.toPk());
                    link.put("key", edge.fromPk() + "\u001f" + edge.toPk());
                    link.put("src", "FIXTURE");
                    links.add(link);
                }
                session.run(TenantCypherGuard.MERGE_LINKS, parameters("tenant", tenant, "links", links)).consume();
            }
        } catch (IllegalArgumentException ex) {
            throw new MopException(ErrorCode.VALIDATION, "夹具里的类型名不合法", null);
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.VALIDATION, "夹具写入失败", null);
        }
    }

    @Override
    public void clear(long tenant) {
        try (Session session = driver.session()) {
            session.run(CLEAR, parameters("tenant", tenant)).consume();
        }
    }
}
