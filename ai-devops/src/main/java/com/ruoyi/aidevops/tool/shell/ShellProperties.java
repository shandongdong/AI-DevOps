package com.ruoyi.aidevops.tool.shell;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shell 工具配置（工作目录 + 超时 + 输出限制 + 白名单）。
 *
 * <p>配置前缀 {@code ai-devops.tool.shell}，对应 application.yml：</p>
 * <pre>
 * ai-devops:
 *   tool:
 *     shell:
 *       workspace-root: ${java.io.tmpdir}/ai-devops-shell
 *       command-timeout: 30s
 *       max-output-lines: 500
 *       max-output-bytes: 65536
 *       allowed-commands: [ls, cat, pwd, date, df, echo, ...]
 * </pre>
 *
 * <p>所有可调项外置到配置：环境差异（不同机器工作目录、不同安全策略）只改配置不改代码，
 * 符合企业级配置外置原则。白名单尤其重要——它定义了"AI 能干哪些事"的边界。</p>
 *
 * @author shandongdong
 */
@ConfigurationProperties(prefix = "ai-devops.tool.shell")
public class ShellProperties
{
    /**
     * 命令执行的工作目录（启动时自动 mkdirs）。
     * <p>隔离爆炸半径：命令只能在这个目录下读写，不污染系统目录。</p>
     */
    private String workspaceRoot = System.getProperty("java.io.tmpdir") + "/ai-devops-shell";

    /** 单条命令最大执行时间，超时强杀进程。保护服务线程不被死循环命令卡死。 */
    private Duration commandTimeout = Duration.ofSeconds(30);

    /** 输出最大行数，超出截断。防止 {@code find /} 等命令输出几十 MB 撑爆模型上下文窗口。 */
    private int maxOutputLines = 500;

    /** 输出最大字节数，超出截断。与行数限制双保险。 */
    private int maxOutputBytes = 65536;

    /**
     * 允许执行的命令白名单（按命令首个 token 匹配，如 {@code ls -la} 校验 {@code ls}）。
     * <p>默认只放行只读/查看类命令。{@code rm}/{@code mv}/{@code kill} 等危险命令默认不放行。
     * 生产环境应按实际需要收紧或扩展。</p>
     */
    private List<String> allowedCommands = Arrays.asList(
            "ls", "cat", "pwd", "date", "df", "echo",
            "head", "tail", "grep", "find", "wc",
            "whoami", "uptime", "free");

    public String getWorkspaceRoot()
    {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot)
    {
        this.workspaceRoot = workspaceRoot;
    }

    public Duration getCommandTimeout()
    {
        return commandTimeout;
    }

    public void setCommandTimeout(Duration commandTimeout)
    {
        this.commandTimeout = commandTimeout;
    }

    public int getMaxOutputLines()
    {
        return maxOutputLines;
    }

    public void setMaxOutputLines(int maxOutputLines)
    {
        this.maxOutputLines = maxOutputLines;
    }

    public int getMaxOutputBytes()
    {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes)
    {
        this.maxOutputBytes = maxOutputBytes;
    }

    public List<String> getAllowedCommands()
    {
        return allowedCommands;
    }

    public void setAllowedCommands(List<String> allowedCommands)
    {
        this.allowedCommands = allowedCommands;
    }
}
