package com.ruoyi.aidevops.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

/**
 * MCP 服务端工具暴露配置（第13篇）：把第11篇的 @Tool 工具暴露成 MCP server 工具。
 *
 * <p>这是第13篇的<b>核心配置</b>——只造一个 {@link ToolCallbackProvider} bean，
 * 框架自动把它桥接成 MCP 工具规格并对外暴露。第11篇的 {@link WeatherTools} / {@link ShellTools}
 * <b>零改动复用</b>，{@code @Tool} 方法体一行都不改。</p>
 *
 * <p>桥接链路（反编译字节码确认）：</p>
 * <ol>
 *   <li>本类造 {@code ToolCallbackProvider} bean（含第11篇的全部 @Tool 方法）</li>
 *   <li>{@code ToolCallbackConverterAutoConfiguration.syncTools()} @Bean 自动收
 *       {@code ObjectProvider<ToolCallbackProvider>}，内部调
 *       {@code McpToolUtils.toSyncToolSpecification(toolCallback, mimeType)}
 *       转成 {@code List<SyncToolSpecification>} bean</li>
 *   <li>{@code McpServerAutoConfiguration.mcpSyncServer()} @Bean 注入该 list，
 *       构建出 {@code McpSyncServer}</li>
 *   <li>{@code McpServerStreamableHttpWebMvcAutoConfiguration} 注册 {@code RouterFunction}，
 *       暴露 {@code POST /mcp} 端点（默认 streamable 协议）</li>
 * </ol>
 *
 * <p>该 @Bean 默认 SYNC 模式自动生效（{@code @ConditionalOnProperty(prefix="spring.ai.mcp.server",
 * name="type", havingValue="SYNC", matchIfMissing=true)}）。配 yml 就有 server，注入即用。</p>
 *
 * <p>与第11篇 {@link AIToolConfig} 的关系：那个注册工具执行引擎 + AI 适配层 bean，
 * 本类把适配层 bean 包成 {@link ToolCallbackProvider} 暴露给 MCP server。<b>不重复注册</b>
 * WeatherTools/ShellTools——它们已在 {@link AIToolConfig} 注册，本类直接注入。</p>
 *
 * @author shandongdong
 * @see AIToolConfig 第11篇工具 bean 注册（WeatherTools/ShellTools 在此注册）
 * @see org.springframework.ai.mcp.McpToolUtils @Tool → MCP 工具规格的底层转换 API
 */
@Configuration
public class McpServerToolConfig
{
    /**
     * 把第11篇的 @Tool 工具暴露成 MCP server 工具。
     *
     * <p>{@link MethodToolCallbackProvider#builder()} 扫描传入对象的 {@code @Tool} 方法，
     * 包成 {@code ToolCallback[]}，再封成 {@link ToolCallbackProvider}。
     * {@code ToolCallbackConverterAutoConfiguration} 自动收此 bean 桥接成 MCP 工具规格。</p>
     *
     * <p>这是"零改动复用"的关键——第11篇的 WeatherTools/ShellTools 一行不改，
     * 这里包一层就同时供 ChatClient.tools()（第11篇）和 MCP server（第13篇）用。</p>
     *
     * @param weatherTools 第11篇的天气查询工具（AIToolConfig 注册的 bean）
     * @param shellTools    第11篇的 Shell 命令执行工具（AIToolConfig 注册的 bean）
     * @return 含第11篇全部 @Tool 方法的 ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider aiDevopsToolCallbacks(WeatherTools weatherTools, ShellTools shellTools)
    {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherTools, shellTools)
                .build();
    }
}
