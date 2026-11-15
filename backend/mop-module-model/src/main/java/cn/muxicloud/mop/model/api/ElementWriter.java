package cn.muxicloud.mop.model.api;

import cn.muxicloud.mop.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 把已通过校验的元素写入分支。实现必须走提交服务。
 */
public interface ElementWriter {
    Adopted add(long branchId, String type, JsonNode payload, AuthenticatedUser actor, String message);

    record Adopted(String uid) {}
}
