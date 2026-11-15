package cn.muxicloud.mop.basic.api;

/**
 * 读取文件上已经由 Tika 抽出的文本。
 */
public interface DocumentText {
    Extracted read(long fileId);

    record Extracted(long fileId, String filename, String text) {}
}
