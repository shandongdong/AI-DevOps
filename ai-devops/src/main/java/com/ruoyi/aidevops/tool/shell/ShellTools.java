package com.ruoyi.aidevops.tool.shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Shell 命令执行工具（AI 适配层）。
 *
 * <p>Spring AI 工具调用的"薄适配器"——用 {@link Tool @Tool} 标注方法供大模型调用，
 * 委托给执行引擎 {@link ShellExecutor}。不写业务逻辑，五重护栏全在 ShellExecutor 里。</p>
 *
 * <p>容错：执行引擎本身不抛异常（被拦/超时/失败都返回 {@link CommandResult}），
 * 本类把 {@link CommandResult} 拼成给模型的字符串。退出码、超时、截断、拦截都如实告知模型，
 * 模型据此判断"命令成功了吗""输出完整吗""这个命令被禁止了吗"，从而决定下一步。</p>
 *
 * @author shandongdong
 * @see ShellExecutor 真正的执行引擎（含五重护栏）
 */
public class ShellTools
{
    private static final Logger log = LoggerFactory.getLogger(ShellTools.class);

    private final ShellExecutor shellExecutor;

    public ShellTools(ShellExecutor shellExecutor)
    {
        this.shellExecutor = shellExecutor;
    }

    /**
     * 在服务器上执行 shell 命令并返回输出。
     *
     * <p>受白名单 + 元字符拒绝 + 工作目录隔离 + 超时 + 截断五重护栏保护。
     * 模型应优先用此工具查询系统信息（文件、磁盘、进程），而非破坏性操作。</p>
     *
     * @param command 要执行的 shell 命令（仅限白名单命令，禁止管道/重定向等元字符）
     * @return 命令输出 + 退出码 + 状态提示
     */
    @Tool(description = "在服务器上执行 shell 命令并返回输出。当需要查看文件、查看磁盘、查看系统信息时调用。"
            + "仅支持白名单内的只读命令（如 ls/cat/pwd/date/df/echo/grep），不支持管道和重定向。")
    public String executeCommand(@ToolParam(description = "要执行的 shell 命令，如 ls、df -h、cat filename") String command)
    {
        log.info("模型请求执行命令: {}", command);
        CommandResult result = shellExecutor.execute(command);

        StringBuilder sb = new StringBuilder();
        if (result.blocked())
        {
            sb.append("命令被安全策略拒绝。");
        }

        sb.append(result.output());

        if (!result.blocked())
        {
            if (result.timedOut())
            {
                sb.append("\n[命令超时被终止]");
            }
            else if (result.exitCode() != 0)
            {
                sb.append("\n[退出码: ").append(result.exitCode()).append("]");
            }
        }

        return sb.toString();
    }
}
