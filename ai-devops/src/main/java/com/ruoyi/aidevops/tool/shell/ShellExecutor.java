package com.ruoyi.aidevops.tool.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shell 命令执行引擎（不耦合 Spring AI，可被 AI 工具和普通接口复用）。
 *
 * <p>这是让大模型"动手"的关键组件——执行模型决定调用的 shell 命令。因为有副作用，
 * 必须加<b>五重护栏</b>防止模型执行危险命令搞垮系统：</p>
 *
 * <ol>
 *   <li><b>白名单</b>：只允许 {@link ShellProperties#getAllowedCommands()} 里的命令（按首个 token 匹配）。</li>
 *   <li><b>元字符拒绝</b>：拒绝含 {@code ; & | ` $ > < \n} 的命令——防 {@code ls; rm -rf /}
 *       这类用合法命令首个 token 绕过白名单再接危险命令的注入。这是白名单模式的关键防线：
 *       只校验首个 token 不够，必须连元字符一起拦。</li>
 *   <li><b>工作目录隔离</b>：命令在 {@code workspace-root} 下执行（启动时 mkdirs），隔离爆炸半径。</li>
 *   <li><b>超时</b>：{@code command-timeout} 内没结束就 {@code destroyForcibly}，防死循环命令卡死线程。</li>
 *   <li><b>输出截断</b>：按 {@code max-output-lines} / {@code max-output-bytes} 截断，
 *       防 {@code find /} 输出几十 MB 撑爆模型上下文窗口。</li>
 * </ol>
 *
 * <p>外加<b>审计日志</b>：每次执行记 INFO 级日志（命令/退出码/耗时/是否超时/是否被拦），
 * 企业合规与可追溯——谁在什么时间让 AI 执行了什么命令、结果如何，都要能查到。</p>
 *
 * <p>设计要点：执行引擎与 AI 适配层分离，本类不感知 {@code @Tool}，任何调用方都能复用这套护栏逻辑。</p>
 *
 * @author shandongdong
 * @see ShellTools AI 适配层
 */
public class ShellExecutor
{
    private static final Logger log = LoggerFactory.getLogger(ShellExecutor.class);

    /**
     * 危险元字符——出现即拒绝。Shell 管道/重定向/命令连接符/命令替换。
     * 拒绝它们后，命令只能是"单条命令 + 参数"，无法拼接第二条命令，注入面收窄到白名单本身的安全性。
     */
    private static final Set<Character> DANGEROUS_CHARS = Set.of(';', '&', '|', '`', '$', '>', '<', '\n', '\r');

    private final ShellProperties properties;

    private final File workspaceDir;

    /**
     * @param properties Shell 配置。构造时创建工作目录（workspace-root）。
     */
    public ShellExecutor(ShellProperties properties)
    {
        this.properties = properties;
        this.workspaceDir = new File(properties.getWorkspaceRoot());
        if (!workspaceDir.exists() && !workspaceDir.mkdirs())
        {
            throw new IllegalStateException("无法创建 Shell 工作目录: " + workspaceDir.getAbsolutePath());
        }
    }

    /**
     * 执行 shell 命令（带五重护栏）。
     *
     * @param command 要执行的命令
     * @return 执行结果（含输出/退出码/超时/截断/拦截标记）
     */
    public CommandResult execute(String command)
    {
        long start = System.currentTimeMillis();

        // 护栏 1+2：白名单 + 元字符拒绝
        String blockReason = checkSafety(command);
        if (blockReason != null)
        {
            long duration = System.currentTimeMillis() - start;
            log.warn("[审计] Shell 命令被拒 command={} reason={} durationMs={}", command, blockReason, duration);
            return new CommandResult("命令被安全策略拒绝：" + blockReason, -1, false, false, true, duration);
        }

        // 护栏 3：工作目录隔离（ProcessBuilder.directory）
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command)
                .directory(workspaceDir)
                .redirectErrorStream(true); // 合并 stderr 到 stdout，一起截断

        Process process;
        try
        {
            process = pb.start();
        }
        catch (IOException e)
        {
            long duration = System.currentTimeMillis() - start;
            log.error("[审计] Shell 启动失败 command={} durationMs={}", command, duration, e);
            return new CommandResult("命令启动失败：" + e.getMessage(), -1, false, false, false, duration);
        }

        // 护栏 4：超时强杀
        boolean timedOut;
        try
        {
            long timeoutMs = properties.getCommandTimeout().toMillis();
            timedOut = !process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            long duration = System.currentTimeMillis() - start;
            return new CommandResult("执行被中断", -1, false, false, false, duration);
        }

        if (timedOut)
        {
            process.destroyForcibly();
            long duration = System.currentTimeMillis() - start;
            log.warn("[审计] Shell 命令超时被杀 command={} timeoutMs={} durationMs={}",
                    command, properties.getCommandTimeout().toMillis(), duration);
            return new CommandResult("命令执行超时（" + properties.getCommandTimeout().getSeconds() + "s），已被终止",
                    -1, true, false, false, duration);
        }

        // 护栏 5：输出截断（截断标记由 readAndTruncate 直接返回，避免用被截断后的 output 长度反推）
        ReadResult readResult = readAndTruncate(process.getInputStream());

        int exitCode = process.exitValue();
        long duration = System.currentTimeMillis() - start;

        log.info("[审计] Shell 执行完成 command={} exitCode={} durationMs={} truncated={}",
                command, exitCode, duration, readResult.truncated());
        return new CommandResult(readResult.output(), exitCode, false, readResult.truncated(), false, duration);
    }

    /**
     * 安全检查：返回 null 表示通过，返回非空字符串表示拒绝原因。
     */
    private String checkSafety(String command)
    {
        if (command == null || command.isBlank())
        {
            return "命令为空";
        }

        // 元字符检查（先于白名单，因为元字符可能让首个 token 看起来合法）
        for (char c : command.toCharArray())
        {
            if (DANGEROUS_CHARS.contains(c))
            {
                return "含禁止字符 '" + c + "'（防止命令拼接/注入）";
            }
        }

        // 白名单检查：取首个 token
        String firstToken = command.trim().split("\\s+", 2)[0];
        if (!properties.getAllowedCommands().contains(firstToken))
        {
            return "命令 '" + firstToken + "' 不在白名单内，允许: " + properties.getAllowedCommands();
        }

        return null;
    }

    /**
     * 读取输出流并按行/字节截断。
     *
     * <p>返回 {@link ReadResult}，把"是否截断"标记直接带出来——而不是让调用方用被截断后的
     * output 长度反推（截断提示文字会污染长度判断）。这是截断逻辑的正确做法。</p>
     */
    private ReadResult readAndTruncate(InputStream inputStream)
    {
        StringBuilder sb = new StringBuilder();
        int maxLines = properties.getMaxOutputLines();
        int maxBytes = properties.getMaxOutputBytes();
        int lineCount = 0;
        boolean truncated = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (lineCount >= maxLines)
                {
                    truncated = true;
                    break;
                }
                if (sb.length() + line.length() + 1 > maxBytes)
                {
                    truncated = true;
                    break;
                }
                if (lineCount > 0)
                {
                    sb.append('\n');
                }
                sb.append(line);
                lineCount++;
            }

            if (truncated)
            {
                sb.append("\n... 输出已截断（限制 ").append(maxLines).append(" 行 / ")
                        .append(maxBytes).append(" 字节）");
            }
        }
        catch (IOException e)
        {
            log.warn("读取命令输出失败", e);
            return new ReadResult("读取输出失败：" + e.getMessage(), false);
        }

        return new ReadResult(sb.toString(), truncated);
    }

    /** readAndTruncate 的返回值：输出文本 + 是否被截断 */
    private record ReadResult(String output, boolean truncated)
    {
    }
}
