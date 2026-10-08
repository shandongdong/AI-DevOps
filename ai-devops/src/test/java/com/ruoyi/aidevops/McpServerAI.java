package com.ruoyi.aidevops;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import com.ruoyi.aidevops.tool.shell.ShellExecutor;
import com.ruoyi.aidevops.tool.shell.ShellProperties;
import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherProperties;
import com.ruoyi.aidevops.tool.weather.WeatherService;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP 服务端冒烟验证：把第11篇的 @Tool 工具暴露成 MCP server，让外部 client 连过来调。
 *
 * <p>承接 {@link McpAI}（第12篇，连外部 MCP server 调其工具），本类演示第13篇的核心——
 * <b>把自己的工具暴露成 MCP server</b>，从"连工具"到"被连"，闭环 MCP 生态。</p>
 *
 * <p>核心差异：</p>
 * <ul>
 *   <li>第12篇 {@link McpAI}：当 client，连外部 MCP server 调其工具（Playwright/钉钉）。</li>
 *   <li>本类：当 server，把自己的 @Tool 工具（第11篇的天气/Shell）暴露出去，
 *       让 Claude Desktop、Cursor、MCP Inspector 等外部 client 连过来调。</li>
 * </ul>
 *
 * <p>沿用第10/11/12篇的"绕开容器手动构造"风格，刻意展示 MCP 服务端的构造细节——
 * 手动造 stdio transport → 造工具规格 → 造 McpSyncServer → listTools 打印验证。
 * 接口环境（{@code spring.ai.mcp.server.*} 配 yml）用容器自动配置，本类展示手动构造链路。</p>
 *
 * <p>本类跑起来后，可配 Claude Desktop mcp.json 用 {@code command: java -cp ... McpServerAI} 连过来，
 * 或用 {@code npx @modelcontextprotocol/inspector} 调试。stdio 传输，本地零网络配置。</p>
 *
 * @author shandongdong
 */
public class McpServerAI
{
    public static void main(String[] args)
    {
        // 1. 第11篇的本地工具（天气 + Shell），@Tool 方法零改动复用
        WeatherTools weatherTools = new WeatherTools(new WeatherService(new WeatherProperties()));
        ShellProperties shellProps = new ShellProperties();
        shellProps.setAllowedCommands(List.of("ls", "pwd", "date", "echo", "df", "whoami"));
        shellProps.setCommandTimeout(Duration.ofSeconds(10));
        ShellTools shellTools = new ShellTools(new ShellExecutor(shellProps));

        // 2. 把 @Tool 方法转成 MCP 工具规格
        //    ToolCallbacks.from() 扫描 @Tool 方法包成 ToolCallback[]
        //    McpToolUtils.toSyncToolSpecifications() 把 ToolCallback 转成 MCP SyncToolSpecification
        //    （server 端工具规格 = JSON Schema + 调用 handler，builder.tools() 收的是这个）
        List<McpServerFeatures.SyncToolSpecification> mcpTools = McpToolUtils.toSyncToolSpecifications(
                ToolCallbacks.from(weatherTools, shellTools));

        // 3. 手动构造 stdio MCP server（4 步看清 server 端构造链路）
        McpSyncServer server = createStdioMcpServer(mcpTools);

        // 4. 验证：listTools 打印暴露的工具
        System.out.println("\n===== MCP Server 已启动（stdio 传输）=====");
        System.out.println("Server info: " + server.getServerInfo());
        System.out.println("暴露的工具列表：");
        server.listTools().forEach(tool -> System.out.println("  - " + tool.name() + ": " + tool.description()));

        System.out.println("\n等待外部 client 连接（Claude Desktop / MCP Inspector）...");
        System.out.println("配 Claude Desktop mcp.json: command=java, args=[-cp, <classpath>, "
                + McpServerAI.class.getName() + "]");
        System.out.println("或: npx @modelcontextprotocol/inspector java -cp <classpath> "
                + McpServerAI.class.getName());

        // 5. 阻塞等 client（stdio server 靠 System.in/out 通信，主线程不能退出）
        //    ⚠️ 不能用 Thread.currentThread().join()——线程 join 自己会永远阻塞
        //    （join() 内部调 wait() 等自己终止，永远不会被 notify），且 Ctrl+C 进
        //    shutdown hook 后 main 线程仍卡在 join() 里下不来。改用 CountDownLatch：
        //    main 线程 await() 阻塞，shutdown hook 里 countDown() 唤醒后正常退出。
        //    按 Ctrl+C 退出，runtime.addShutdownHook 关 server + 唤醒主线程（修复死循环 #8）。
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n关闭 MCP server...");
            server.close();
            shutdownLatch.countDown();   // 唤醒主线程，让它能正常退出
        }));

        // 主线程阻塞，等 shutdown hook 唤醒（Ctrl+C）或被 interrupt
        try
        {
            shutdownLatch.await();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 手动构造 stdio 传输的 MCP server——把 @Tool 工具暴露成 MCP server 工具。
     *
     * <p>stdio server 端构造链路（4 步看清 server 端的每一层）：</p>
     * <ol>
     *   <li>{@link StdioServerTransportProvider}：传输层，靠 stdin/stdout 与 client 通信，
     *       需要 {@link McpJsonMapper} 序列化 JSON-RPC 消息</li>
     *   <li>{@link McpSchema.ServerCapabilities.Builder}：声明 server 能力（tools + listChanged）</li>
     *   <li>工具规格：{@link ToolCallbacks#from(Object...)} 把 @Tool 方法包成 {@link ToolCallback}，
     *       再用 {@link McpToolUtils#toSyncToolSpecifications(ToolCallback...)} 转成
     *       {@link McpServerFeatures.SyncToolSpecification}（MCP 工具规格 = JSON Schema + 调用 handler）</li>
     *   <li>{@link McpServer#sync}：工厂方法，传 transport 拿 builder，配 serverInfo/capabilities/tools 后
     *       {@code build()} 出 {@link McpSyncServer}</li>
     * </ol>
     *
     * <p>接口环境（容器内）这些全由 {@code McpServerAutoConfiguration} +
     * {@code ToolCallbackConverterAutoConfiguration} 自动造——配 yml 就行。
     * 本方法展示手动构造链路，让读者看清 MCP server 端的每一层。</p>
     *
     * @param mcpTools 第11篇 @Tool 方法转成的 MCP 工具规格列表
     * @return 已构建的 McpSyncServer，主线程需阻塞等 client
     */
    private static McpSyncServer createStdioMcpServer(List<McpServerFeatures.SyncToolSpecification> mcpTools)
    {
        // 1. stdio 传输层（默认用 System.in/System.out，需要 McpJsonMapper 序列化 JSON-RPC）
        //    JacksonMcpJsonMapperSupplier 提供默认 Jackson3 实现的 McpJsonMapper
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
        StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);

        // 2. 声明 server 能力：tools(true) 暴露工具能力
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();

        // 3. 构造 McpSyncServer（builder 模式）
        //    McpServer.sync(transport) 拿 builder → serverInfo(name, version) → capabilities → tools(SyncToolSpecification...) → build()
        //    tools() 收的是 MCP 工具规格（SyncToolSpecification），不是 ToolCallback——
        //    所以 main 里先用 McpToolUtils.toSyncToolSpecifications() 转换
        return McpServer.sync(transport)
                .serverInfo("ai-devops-mcp-server", "1.0.0")
                .capabilities(capabilities)
                .tools(mcpTools.toArray(new McpServerFeatures.SyncToolSpecification[0]))
                .build();
    }
}
