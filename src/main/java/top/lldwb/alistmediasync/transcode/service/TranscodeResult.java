package top.lldwb.alistmediasync.transcode.service;

/**
 * 转码结果（DTO）
 *
 * @param sourceFileName 源文件名
 * @param success        是否成功
 * @param error          错误信息（成功时为 null）
 */
public record TranscodeResult(String sourceFileName, boolean success, String error) {}
