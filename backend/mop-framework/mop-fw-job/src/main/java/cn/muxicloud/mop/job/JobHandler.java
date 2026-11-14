package cn.muxicloud.mop.job;

/**
 * 作业处理器。队列、锁和调度在后续里程碑接入，M0 只固定这个扩展点。
 */
public interface JobHandler {
    String type();
}
