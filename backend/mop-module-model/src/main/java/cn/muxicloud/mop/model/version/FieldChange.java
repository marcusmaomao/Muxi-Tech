package cn.muxicloud.mop.model.version;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一个 JSON 字段的差异，路径用 RFC 6901。
 */
public record FieldChange(String op, String path, JsonNode before, JsonNode after) {}
