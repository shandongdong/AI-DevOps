package com.ruoyi.aidevops.tool.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * ShellExecutor 单元测试：验证五重护栏。
 *
 * <p>用真实进程测试（不 Mock），因为护栏的核心是 ProcessBuilder 行为——
 * 超时强杀、输出截断、退出码都是真进程才验得出。每个测试用独立的临时工作目录。</p>
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>白名单内命令成功执行</li>
 *   <li>白名单外命令被拒（blocked=true）</li>
 *   <li>元字符注入被拒（ls; rm、ls | rm）</li>
 *   <li>超时命令被杀（timedOut=true）</li>
 *   <li>大输出被截断（truncated=true）</li>
 * </ul>
 *
 * @author shandongdong
 */
class ShellExecutorTest
{
    @TempDir
    Path tempDir;

    /** 默认测试配置：白名单含常用命令 + 2 秒超时 + 小输出限制便于测截断 */
    private ShellProperties props;

    @BeforeEach
    void setUp()
    {
        props = new ShellProperties();
        props.setWorkspaceRoot(tempDir.toString());
        props.setCommandTimeout(Duration.ofSeconds(2));
        props.setMaxOutputLines(500);
        props.setMaxOutputBytes(65536);
    }

    @Test
    @DisplayName("白名单内命令成功执行：echo hello → exitCode=0，输出含 hello")
    void execute_whitelistedCommand_succeeds()
    {
        ShellExecutor executor = new ShellExecutor(props);
        CommandResult result = executor.execute("echo hello");

        assertFalse(result.blocked(), "echo 在白名单内不应被拒");
        assertFalse(result.timedOut(), "echo 不应超时");
        assertEquals(0, result.exitCode(), "echo 应正常退出码 0");
        assertTrue(result.output().contains("hello"), "输出应含 hello");
    }

    @Test
    @DisplayName("白名单外命令被拒：rm → blocked=true")
    void execute_nonWhitelistedCommand_blocked()
    {
        ShellExecutor executor = new ShellExecutor(props);
        CommandResult result = executor.execute("rm somefile");

        assertTrue(result.blocked(), "rm 不在白名单应被拒");
        assertTrue(result.output().contains("白名单"), "被拒原因应提及白名单");
    }

    @Test
    @DisplayName("元字符注入被拒：ls; rm → blocked=true（防命令拼接绕过）")
    void execute_commandWithSemicolon_blocked()
    {
        ShellExecutor executor = new ShellExecutor(props);
        CommandResult result = executor.execute("ls; rm -rf /");

        assertTrue(result.blocked(), "含分号的命令应被拒，防止拼接第二条命令");
    }

    @Test
    @DisplayName("管道元字符被拒：ls | grep → blocked=true")
    void execute_commandWithPipe_blocked()
    {
        ShellExecutor executor = new ShellExecutor(props);
        CommandResult result = executor.execute("ls | grep foo");

        assertTrue(result.blocked(), "含管道符的命令应被拒");
    }

    @Test
    @DisplayName("超时命令被杀：sleep 10 + 2s 超时 → timedOut=true")
    void execute_timeoutCommand_killed() throws Exception
    {
        // sleep 不在默认白名单，单独放行用于测超时
        props.setAllowedCommands(List.of("sleep"));
        ShellExecutor executor = new ShellExecutor(props);

        long start = System.currentTimeMillis();
        CommandResult result = executor.execute("sleep 10");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.timedOut(), "sleep 10 应被 2s 超时强杀");
        assertFalse(result.blocked(), "sleep 已在白名单不应被拒");
        // 超时应接近 2 秒而非 10 秒（给足宽裕，避免机器慢误判）
        assertTrue(elapsed < 5000, "超时应及时触发，实际耗时 " + elapsed + "ms");
    }

    @Test
    @DisplayName("大输出被截断：maxOutputLines=3 + seq 1 100 → truncated=true")
    void execute_largeOutput_truncated()
    {
        // seq 不在默认白名单，放行用于测截断；限制 3 行
        props.setAllowedCommands(List.of("seq"));
        props.setMaxOutputLines(3);
        props.setMaxOutputBytes(65536);
        ShellExecutor executor = new ShellExecutor(props);

        CommandResult result = executor.execute("seq 1 100");

        assertFalse(result.blocked(), "seq 在白名单不应被拒");
        assertTrue(result.output().contains("截断"), "输出应被截断并提示");
    }

    @Test
    @DisplayName("空命令被拒：空字符串 → blocked=true")
    void execute_emptyCommand_blocked()
    {
        ShellExecutor executor = new ShellExecutor(props);
        CommandResult result = executor.execute("");

        assertTrue(result.blocked(), "空命令应被拒");
    }

    @Test
    @DisplayName("工作目录隔离：pwd 输出应为配置的临时目录")
    void execute_workdirIsolated()
    {
        ShellExecutor executor = new ShellExecutor(props);
        CommandResult result = executor.execute("pwd");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().trim().equals(tempDir.toString())
                        || result.output().contains(tempDir.getFileName().toString()),
                "pwd 输出应指向配置的工作目录，实际: " + result.output());
    }
}
