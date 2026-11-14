package cn.muxicloud.mop.fn.api;

/** 函数调用协议。server 与 function-runner 共用，M3 才会真正执行。 */
public record FunctionInvocation(String functionUid, String version, String payloadJson) {
}
