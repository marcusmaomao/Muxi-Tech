package cn.muxicloud.mop.audit;

/**
 * 审计留痕的入口。哈希链在 M4 补上，M0 先把登录结果记到登录日志，并打一条结构化日志。
 */
public interface AuditRecorder {
    void record(String category, String action, String target, String result, String detail);
}
