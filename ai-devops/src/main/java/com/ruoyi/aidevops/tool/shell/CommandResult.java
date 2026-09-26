package com.ruoyi.aidevops.tool.shell;

/**
 * Shell 命令执行结果（结构化，可被 AI 工具和普通接口复用）。
 *
 * <p>用 record 封装执行的全部信息——不仅给模型看，也用于审计日志和单测断言。
 * 一个字段都不省：企业级 Shell 执行必须能回答"执行了什么、成功没、耗时多久、有没有被截断/超时"。</p>
 *
 * @author shandongdong
 *
 * @param output       命令标准输出（stderr 已合并），可能被截断
 * @param exitCode     退出码；超时或被拦截时为 -1
 * @param timedOut     是否因超时被强杀
 * @param truncated    输出是否被截断（超过 maxOutputLines/maxOutputBytes）
 * @param blocked      是否被安全策略（白名单/元字符）拒绝执行
 * @param durationMs   执行耗时（毫秒）
 */
public record CommandResult(String output, int exitCode, boolean timedOut, boolean truncated, boolean blocked,
        long durationMs)
{
}
