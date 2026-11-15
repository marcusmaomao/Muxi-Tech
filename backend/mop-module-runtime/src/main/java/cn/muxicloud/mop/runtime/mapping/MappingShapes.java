package cn.muxicloud.mop.runtime.mapping;

import java.util.List;

public final class MappingShapes {
    private MappingShapes() {
    }

    public record MappingDefinition(
            String objectUid,
            String table,
            String pkColumn,
            Incremental incremental,
            List<FieldBinding> fields,
            List<LinkBinding> links) {
    }

    public record Incremental(String kind, String column) {
    }

    public record FieldBinding(String propertyUid, String column) {
    }

    public record LinkBinding(
            String kind,
            String linkUid,
            String column,
            String targetObjectUid,
            String table,
            String sourceColumn,
            String targetColumn) {
    }

    public record StoredMapping(
            String objectUid,
            String objectApiName,
            String table,
            String pkColumn,
            Incremental incremental,
            List<StoredField> fields,
            List<StoredLink> links) {
    }

    public record StoredField(String propertyUid, String propertyApiName, String column) {
    }

    public record StoredLink(
            String kind,
            String linkUid,
            String linkApiName,
            String column,
            String targetObjectUid,
            String targetApiName,
            String table,
            String sourceColumn,
            String targetColumn) {
    }
}
