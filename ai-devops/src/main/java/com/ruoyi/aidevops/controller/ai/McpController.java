package com.ruoyi.aidevops.controller.ai;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherTools;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * ai-devops MCP 工具调用接口（多模型路由 + 本地工具 + MCP 工具并存）。
 *
 * <p>承接 {@link AIToolController}（第11篇，自写 @Tool 工具），本接口在第11篇基础上加 MCP 工具——
 * 连外部 MCP server（Playwright 浏览器自动化 / 钉钉云文档），让模型从"调自己写的工具"
 * 升级到"调外部生态的工具"。这是"工具调用→智能体生态"的关键一步。</p>
 *
 * <p>与 {@link AIToolController} 的核心差异：</p>
 * <ul>
 *   <li>第11篇：只注入自写的 {@link WeatherTools} / {@link ShellTools}（{@code @Tool} 方法）</li>
 *   <li>本接口：额外注入 {@link ToolCallbackProvider}（Spring AI 自动配置造的 bean，
 *       含所有 application.yml 配的 MCP server 工具——Playwright 的 browser_navigate/click/screenshot、
 *       钉钉云文档工具等）。这是 MCP 的核心便利——配 yml 就有工具，不用自己写</li>
 * </ul>
 *
 * <p>本地工具与 MCP 工具<b>并存注册</b>：{@code .tools(weatherTools, shellTools, mcpToolCallbackProvider)}。
 * 模型自主路由全部工具——问天气调本地 getWeather，问网页操作调 Playwright browser_* 工具，
 * 路由决策权全在模型。呼应第11篇 6.2 集中注册最佳实践。</p>
 *
 * <p>权限：验证阶段标 {@code @Anonymous} 免登录，后续接入业务移除并配 {@code @PreAuthorize}。</p>
 *
 * @author shandongdong
 * @see AIToolController 第11篇自写工具接口（本接口在其基础上加 MCP 工具）
 */
@RestController
@RequestMapping("/aidevops/ai")
public class McpController
{
    /** 默认模型标识，未传 model 参数时走它 */
    private static final String DEFAULT_MODEL = "anthropic";

    private final ChatClient anthropicChatClient;
    private final ChatClient deepSeekChatClient;
    private final WeatherTools weatherTools;
    private final ShellTools shellTools;

    /**
     * MCP 工具提供者——Spring AI 自动配置造的 bean。
     *
     * <p>当 application.yml 配了 {@code spring.ai.mcp.client.*} 后，
     * {@code McpToolCallbackAutoConfiguration} 自动造 {@code SyncMcpToolCallbackProvider}
     * （实现 {@link ToolCallbackProvider}），含所有 connections 配的 MCP server 工具。
     * 直接注入用即可——这是 MCP 的核心便利，配 yml 就有工具。</p>
     */
    private final ToolCallbackProvider mcpToolCallbackProvider;

    public McpController(@Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
                         @Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient,
                         WeatherTools weatherTools,
                         ShellTools shellTools,
                         // 第13篇新增 aiDevopsToolCallbacks（含本地天气/Shell）后，容器里有两个
                         // ToolCallbackProvider bean：mcpToolCallbacks（client 端 autoconfig 造，含
                         // Playwright/钉钉等外部 MCP server 工具）+ aiDevopsToolCallbacks（第13篇造）。
                         // 按类型注入会 NoUniqueBeanDefinitionException，必须 @Qualifier 明确要哪个。
                         // 本接口要调外部 MCP server 工具，故注入 mcpToolCallbacks。
                         @Qualifier("mcpToolCallbacks") ToolCallbackProvider mcpToolCallbackProvider)
    {
        this.anthropicChatClient = anthropicChatClient;
        this.deepSeekChatClient = deepSeekChatClient;
        this.weatherTools = weatherTools;
        this.shellTools = shellTools;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    /**
     * MCP 工具调用接口：接收用户消息，注入本地工具 + MCP 工具，返回模型回复。
     *
     * <p>与 {@link AIToolController#tool} 的区别：调用链多了 {@code mcpToolCallbackProvider}——
     * {@code .tools(weatherTools, shellTools, mcpToolCallbackProvider)}。
     * 模型据此决定调本地工具还是 MCP 工具——问天气调本地 getWeather，
     * 问网页操作调 Playwright browser_navigate + browser_take_screenshot，
     * 路由决策权在模型。</p>
     *
     * @param model   模型标识（query param，可选，默认 anthropic）：anthropic / deepseek
     * @param request 包含 message 字段的请求体（复用 {@link ChatRequest}）
     * @return AjaxResult 包含模型回复内容
     */
    @Anonymous
    @PostMapping("/mcp")
    public AjaxResult mcp(@RequestParam(required = false) String model,
                          @RequestBody ChatRequest request)
    {
        if (request.message() == null || request.message().trim().isEmpty())
        {
            return AjaxResult.error("消息内容不能为空");
        }
        try
        {
            String resolvedModel = (model == null || model.isBlank())
                    ? DEFAULT_MODEL : model.toLowerCase();
            ChatClient client = switch (resolvedModel)
            {
                case "anthropic" -> anthropicChatClient;
                case "deepseek" -> deepSeekChatClient;
                default -> throw new IllegalArgumentException(
                        "不支持的模型: " + model + "，支持: anthropic, deepseek");
            };
            // 本地工具 + MCP 工具并存注册，模型自主路由全部工具
            // mcpToolCallbackProvider.getToolCallbacks() 返回所有 MCP server 工具（Playwright browser_*、钉钉文档等）
            String reply = client.prompt()
                    .user(request.message())
                    .tools(weatherTools, shellTools, mcpToolCallbackProvider)
                    .call()
                    .content();
            // Map.of 不接受 null value：模型无回复时 .content() 返回 null，直接 Map.of 会 NPE，故判空兜底
            Map<String, Object> data = new HashMap<>();
            data.put("reply", reply == null ? "" : reply);
            data.put("model", resolvedModel);
            return AjaxResult.success(data);
        }
        catch (Exception e)
        {
            return AjaxResult.error("MCP 工具调用失败：" + e.getMessage());
        }
    }
}
