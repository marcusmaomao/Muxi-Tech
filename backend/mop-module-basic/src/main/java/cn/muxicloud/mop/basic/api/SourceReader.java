package cn.muxicloud.mop.basic.api;

import java.util.List;
import java.util.Map;

/**
 * 按数据源元数据读取外部表。标识符先校验，值只走参数。
 */
public interface SourceReader {
    List<ForeignKey> foreignKeys(long datasourceId, String table);

    void check(String table, List<String> columns);

    List<Map<String, String>> read(Read request);

    String max(long datasourceId, String table, String column);

    List<String> junctionTargets(long datasourceId, String table, String sourceColumn, String targetColumn, String sourcePk);

    record Read(long datasourceId, String table, List<String> columns, String orderColumn, String watermarkColumn, String watermark, int limit) {}

    record ForeignKey(String column, String targetTable, String targetColumn) {}
}
