package com.ruoyi.aidevops;

import java.time.Duration;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;

import com.ruoyi.aidevops.tool.shell.ShellExecutor;
import com.ruoyi.aidevops.tool.shell.ShellProperties;
import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherProperties;
import com.ruoyi.aidevops.tool.weather.WeatherService;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;

/**
 * MCP（Model Context Protocol）工具调用冒烟验证：连外部 MCP server 调其工具。
 *
 * <p>承接 {@link AIToolCallingSmokeTest}（第11篇，自写 @Tool 工具），本类演示第12篇的核心——
 * <b>连外部 MCP server 调其工具</b>，而不是自己写工具。</p>
 *
 * <p>核心差异：</p>
 * <ul>
 *   <li>第11篇 {@link AIToolCallingSmokeTest}：自己写 {@code @Tool} 方法（WeatherTools/ShellTools），工具逻辑跑在本进程。</li>
 *   <li>本篇：连外部 MCP server（Playwright MCP），工具逻辑跑在 MCP server 子进程里，
 *       本侧只做"桥接"——把 MCP server 的工具转成 Spring AI 的 {@code ToolCallback}。</li>
 * </ul>
 *
 * <p>沿用第10/11篇的"绕开容器手动构造"风格，刻意展示 MCP 客户端的构造细节——
 * 手动造传输层（StdioClientTransport）→ 造 McpSyncClient → 桥接成 ToolCallbackProvider → 喂给 ChatClient。
 * 接口环境（McpController）用容器自动配置，本类展示手动构造链路，循序渐进。</p>
 *
 * <p>依赖环境变量 {@code DEEPSEEK_API_KEY}。运行前先 export。
 * 还需 Node.js 18+（Playwright MCP 跑在 npx 子进程里）。</p>
 *
 * @author shandongdong
 */
public class McpAI
{
    public static void main(String[] args)
    {
        // 1. 手动构造第11篇的本地工具（天气 + Shell），与 MCP 工具并存注册
        WeatherTools weatherTools = new WeatherTools(new WeatherService(new WeatherProperties()));

        ShellProperties shellProps = new ShellProperties();
        shellProps.setAllowedCommands(List.of("ls", "pwd", "date", "echo", "df", "whoami"));
        shellProps.setCommandTimeout(Duration.ofSeconds(10));
        ShellTools shellTools = new ShellTools(new ShellExecutor(shellProps));

        // 2. 手动构造 MCP 客户端，连 Playwright MCP server（stdio 传输）
        //    这是本篇的核心——连外部 MCP server，复用其工具，而不是自己写
        //    ⚠️ createStdioMcpClient 会起 npx 子进程，必须放进 try 块——若构造失败
        //    （如 npx 不存在/握手失败），playwrightMcp 赋值前就抛异常，原写在 try 外
        //    会导致 finally 里 close() NPE，且已起的子进程泄漏。移进 try 后异常时
        //    playwrightMcp=null，finally 判空 close（修复资源泄漏 #6/#7）。
        McpSyncClient playwrightMcp = null;
        try
        {
            playwrightMcp = createStdioMcpClient(
                    "npx", List.of("@playwright/mcp@latest"));

            // 3. 桥接：McpSyncClient → ToolCallbackProvider
            //    SyncMcpToolCallbackProvider 实现 ToolCallbackProvider，getToolCallbacks() 返回 ToolCallback[]
            //    含 Playwright MCP server 暴露的全部工具（browser_navigate/click/screenshot 等）
            SyncMcpToolCallbackProvider mcpTools = new SyncMcpToolCallbackProvider(List.of(playwrightMcp));

            // 4. 手动构造 ChatClient（DeepSeek 链路，沿用 AIToolCallingSmokeTest 套路）
            ChatClient chatClient = createChatClient();

            // 5. 集中注册：本地工具 + MCP 工具并存，模型自主路由全部工具
            //    呼应第11篇 6.2 集中注册最佳实践——路由决策交给模型，不是我们替它选
            testLocalToolWithMcp(chatClient, weatherTools, shellTools, mcpTools);
            testPlaywrightMcp(chatClient, weatherTools, shellTools, mcpTools);
        }
        finally
        {
            // McpSyncClient 实现了 AutoCloseable，用完必须 close——否则子进程（npx）会残留。
            // 判空 close：createStdioMcpClient 抛异常时 playwrightMcp 仍为 null，直接 close 会 NPE。
            if (playwrightMcp != null)
            {
                playwrightMcp.close();
            }
        }

        System.out.println("\n===== 验证结束 =====");
        System.out.println("================================================");
    }

    /**
     * 手动构造 stdio 传输的 MCP 客户端——连一个用命令起的 MCP server。
     *
     * <p>stdio 传输链路（4 步，看清 MCP 客户端的构造细节）：</p>
     * <ol>
     *   <li>{@link ServerParameters}：描述怎么起 server 子进程（command + args）</li>
     *   <li>{@link StdioClientTransport}：传输层，靠标准输入输出与子进程通信，需要 {@link McpJsonMapper} 序列化 JSON-RPC 消息</li>
     *   <li>{@link McpClient#sync}：工厂方法，传 transport 拿 SyncSpec（builder），配超时后 build 出 {@link McpSyncClient}</li>
     *   <li>{@link McpSyncClient#initialize()}：与 server 握手——列出能力、协商版本，必须调，否则工具调不动</li>
     * </ol>
     *
     * <p>接口环境（容器内）这些全由 {@code McpClientAutoConfiguration} 自动造——配 yml 就行。
     * 本方法展示手动构造链路，让读者看清 MCP 客户端的每一层。</p>
     *
     * @param command server 启动命令（如 "npx"）
     * @param args    命令参数（如 ["@playwright/mcp@latest"]）
     * @return 已握手的 McpSyncClient，用完必须 close
     */
    private static McpSyncClient createStdioMcpClient(String command, List<String> args)
    {
        // 1. 描述怎么起 server 子进程
        ServerParameters serverParams = ServerParameters.builder(command)
                .args(args)
                .build();

        // 2. 造 stdio 传输层（需要 JSON 映射器序列化 JSON-RPC 消息）
        //    JacksonMcpJsonMapperSupplier 提供默认 Jackson3 实现的 McpJsonMapper
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
        StdioClientTransport transport = new StdioClientTransport(serverParams, jsonMapper);

        // 3. 造 McpSyncClient（builder 模式，配请求超时）
        McpSyncClient mcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        // 4. 与 server 握手——必须调，否则 listTools/callTool 全失败
        mcpClient.initialize();
        return mcpClient;
    }

    /**
     * 手动构造 DeepSeek ChatClient（绕开 Spring 容器，沿用 AIToolCallingSmokeTest/HelloAI 套路）。
     *
     * @return 绑定 DeepSeek 模型的 ChatClient
     */
    private static ChatClient createChatClient()
    {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        DeepSeekConnectionProperties conn = new DeepSeekConnectionProperties();
        conn.setApiKey(apiKey);
        conn.setBaseUrl("https://api.deepseek.com");

        DeepSeekChatProperties chat = new DeepSeekChatProperties();
        chat.setModel("deepseek-flash");

        DefaultToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        DeepSeekChatAutoConfiguration autoConfig = new DeepSeekChatAutoConfiguration();
        DeepSeekChatModel chatModel = autoConfig.deepSeekChatModel(
                conn, chat,
                emptyProvider(), emptyProvider(),
                toolCallingManager,
                emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider());

        return ChatClient.create(chatModel);
    }

    /**
     * 场景一：验证本地工具与 MCP 工具并存路由——问天气，模型应调本地 getWeather。
     *
     * <p>关键点：虽然注册了 Playwright MCP 的全部浏览器工具，但模型读到"杭州天气怎么样"后，
     * 自己判断该调本地 getWeather（而不是浏览器工具）——这就是第11篇 6.2 说的"模型自主路由"。
     * 本地工具和 MCP 工具在模型眼里一视同仁，都是 ToolCallback，模型按描述选最合适的。</p>
     */
    private static void testLocalToolWithMcp(ChatClient chatClient, WeatherTools weatherTools,
                                             ShellTools shellTools, SyncMcpToolCallbackProvider mcpTools)
    {
        System.out.println("\n===== 场景一：本地工具与 MCP 工具并存路由（问天气）=====");
        String reply = chatClient.prompt()
                .user("杭州现在天气怎么样？")
                .tools(weatherTools, shellTools, mcpTools)   // 本地 + MCP 集中注册
                .call()
                .content();
        System.out.println("模型回复：\n" + reply);
    }

    /**
     * 场景二：验证 MCP 工具——让模型用 Playwright 打开网页截图。
     *
     * <p>模型应自主组合调度链：browser_navigate（打开网页）→ browser_take_screenshot（截图）。
     * 这两步是模型现场决定的，我们没在代码里写"先 navigate 再 screenshot"——
     * 这就是 MCP 工具与 CLI 的核心差异：CLI 你得预先知道有哪些命令、按什么顺序跑；
     * MCP 模型自己看工具列表（list_tools 协议自动发现）决定调啥、传啥参数、调几次。</p>
     *
     * <p>且 Playwright MCP 的浏览器会话在多次工具调用间保持状态——
     * browser_navigate 打开的页面，browser_take_screenshot 能直接截，无需重新打开。
     * 这是有状态的 MCP server，CLI 做不到（每次 CLI 调用独立无状态）。</p>
     */
    private static void testPlaywrightMcp(ChatClient chatClient, WeatherTools weatherTools,
                                          ShellTools shellTools, SyncMcpToolCallbackProvider mcpTools)
    {
        System.out.println("\n===== 场景二：Playwright MCP 浏览器自动化（打开网页截图）=====");
        String reply = chatClient.prompt()
                .user("用浏览器打开 https://example.com 这个网页，然后截个图给我看看页面长什么样")
                .tools(weatherTools, shellTools, mcpTools)   // 本地 + MCP 集中注册
                .call()
                .content();
        System.out.println("模型回复：\n" + reply);
    }

    /** 返回一个"空" ObjectProvider（沿用 HelloAI 实现） */
    private static <T> ObjectProvider<T> emptyProvider()
    {
        return new ObjectProvider<T>()
        {
            @Override
            public java.util.stream.Stream<T> stream()
            {
                return java.util.stream.Stream.empty();
            }
        };
    }
}
