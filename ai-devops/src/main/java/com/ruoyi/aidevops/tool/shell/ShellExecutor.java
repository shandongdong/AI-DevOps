package com.ruoyi.aidevops.tool.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
 *   <li><b>白名单</b>：只允许 {@link ShellProperties#getAllowedCommands()} 里的命令（按首个 token 匹配）。
 *       只放行只读命令——含写原语子参数的命令（如 {@code find -exec}/{@code -delete}）不得进白名单。</li>
 *   <li><b>元字符拒绝</b>：拒绝含 {@code ; & | ` $ > < ( ) { } + \n} 的命令——防 {@code ls; rm -rf /}
 *       这类用合法命令首个 token 绕过白名单再接危险命令的注入。这是白名单模式的关键防线：
 *       只校验首个 token 不够，必须连元字符一起拦。{@code ( ) { } +} 是纵深防御——
 *       如 {@code find -exec cmd {} +}（POSIX 批量模式，无需分号）。</li>
 *   <li><b>工作目录锚定</b>：命令以 {@code workspace-root} 为 cwd 执行（启动时 mkdirs），相对路径落在工作目录内。
 *       <b>注意这不是沙箱</b>——绝对路径仍可越出工作目录，真正的边界靠白名单只放行只读命令来约束。</li>
 *   <li><b>超时</b>：{@code command-timeout} 内没结束就 {@code destroyForcibly}，防死循环命令卡死线程。</li>
 *   <li><b>输出截断</b>：读流与 {@code waitFor} 并发进行（后台线程边读边截断），达上限后继续读完剩余输出
 *       （drain 模式）——防止输出超过 OS 管道缓冲区（约 64KB）时子进程阻塞在 write() 形成双方互等死锁，
 *       同时按 {@code max-output-lines} / {@code max-output-bytes} 截断，防大输出撑爆模型上下文窗口。</li>
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
     * {@code ( ) { } +} 为纵深防御：子 shell 括号、花括号命令组、以及 {@code find -exec cmd {} +}
     * 这类无需分号的批量执行语法。拒绝它们后，命令只能是"单条命令 + 普通参数"，
     * 无法拼接或批量执行第二条命令，注入面收窄到白名单本身的安全性。
     */
    private static final Set<Character> DANGEROUS_CHARS = Set.of(';', '&', '|', '`', '$', '>', '<',
            '(', ')', '{', '}', '+', '\n', '\r');

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

        // 护栏 3：工作目录锚定（ProcessBuilder.directory 设 cwd；非沙箱，见类注释）
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

        // 护栏 4+5：读流必须与 waitFor 并发——子进程 stdout 是 OS 管道（缓冲约 64KB），
        // 若先 waitFor 后读流，输出超过管道缓冲区的命令会写满管道阻塞在 write()、永不退出，
        // waitFor 只能等超时 → 误报"超时"且输出全丢（Java Process 经典死锁）。
        // 后台线程边读边截断（drain 模式：达上限后继续读完但不累积），主线程限时等进程退出。
        CompletableFuture<ReadResult> readFuture = new CompletableFuture<>();
        Thread readerThread = new Thread(() ->
        {
            try
            {
                readFuture.complete(readAndTruncate(process.getInputStream()));
            }
            catch (Throwable t)
            {
                readFuture.completeExceptionally(t);
            }
        }, "shell-output-reader");
        readerThread.setDaemon(true);
        readerThread.start();

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
            ReadResult partial = awaitReader(readFuture);
            long duration = System.currentTimeMillis() - start;
            return new CommandResult("执行被中断", -1, false, partial.truncated(), false, duration);
        }

        if (timedOut)
        {
            process.destroyForcibly();
            // 进程被杀后管道关闭、读线程随即 EOF 收尾，尽量带上已捕获的部分输出
            ReadResult partial = awaitReader(readFuture);
            long duration = System.currentTimeMillis() - start;
            log.warn("[审计] Shell 命令超时被杀 command={} timeoutMs={} durationMs={}",
                    command, properties.getCommandTimeout().toMillis(), duration);
            String output = "命令执行超时（" + properties.getCommandTimeout().toMillis() + "ms），已被终止";
            if (!partial.output().isBlank())
            {
                output += "\n超时前已捕获的部分输出：\n" + partial.output();
            }
            return new CommandResult(output, -1, true, partial.truncated(), false, duration);
        }

        // 进程已正常退出，读线程读到 EOF 即收尾，短暂等待拿全量结果
        ReadResult readResult = awaitReader(readFuture);

        int exitCode = process.exitValue();
        long duration = System.currentTimeMillis() - start;

        log.info("[审计] Shell 执行完成 command={} exitCode={} durationMs={} truncated={}",
                command, exitCode, duration, readResult.truncated());
        return new CommandResult(readResult.output(), exitCode, false, readResult.truncated(), false, duration);
    }

    /**
     * 等待读流线程收尾（进程退出/被杀后管道 EOF，正常情况瞬间完成），最多等 5 秒兜底防挂死。
     */
    private static ReadResult awaitReader(CompletableFuture<ReadResult> readFuture)
    {
        try
        {
            return readFuture.get(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return new ReadResult("", false);
        }
        catch (Exception e)
        {
            return new ReadResult("读取输出失败：" + e.getMessage(), false);
        }
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
     * 读取输出流并按行/字节截断（drain 模式）。
     *
     * <p>达到上限后<b>继续读完剩余输出但不累积</b>——若直接停止读取，子进程会因管道写满
     * 阻塞在 write() 而无法退出（死锁），drain 模式保证子进程能自然跑完，由超时护栏兜底。</p>
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
                // drain 模式：已达上限，继续读完剩余输出（防管道写满阻塞子进程），但不再累积
                if (truncated)
                {
                    continue;
                }
                if (lineCount >= maxLines)
                {
                    truncated = true;
                    continue;
                }
                if (sb.length() + line.length() + 1 > maxBytes)
                {
                    truncated = true;
                    continue;
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
