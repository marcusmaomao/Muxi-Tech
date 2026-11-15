package cn.muxicloud.mop.common.cq;

import java.util.List;
import java.util.Map;

/** 能力问题测试使用的只读图。夹具写入和清理由实现方用固定语句完成。 */
public interface CqGraph {
    List<Map<String, String>> read(String cypher, long tenant);

    void loadFixture(long tenant, List<Node> nodes, List<Edge> edges);

    void clear(long tenant);

    record Node(String type, String pk, Map<String, String> props) {}

    record Edge(String type, String fromType, String fromPk, String toType, String toPk) {}
}
