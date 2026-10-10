package com.ruoyi.aidevops.controller.ai;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
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
 * <p>本地工具与 MCP 工具<b>并存注册</b>：{@code .tools(weatherTools, shellTools)} +
 * {@code .toolCallbacks(mcpTools)}（{@link #mcpToolCallbacksWithFallback()} 获取，缓存 + 失败降级）。
 * 模型自主路由全部工具——问天气调本地 getWeather，问网页操作调 Playwright browser_* 工具，
 * 路由决策权全在模型。呼应第11篇 6.2 集中注册最佳实践。</p>
 *
 * <p>权限：验证阶段标 {@code @Anonymous} 免登录，后续接入业务移除并配 {@code @PreAuthorize}。</p>
 *
 * @author shandongdong
 * @see AIToolController 第11篇自写工具接口（本接口在其基础上加 MCP 工具）
 * @see AiModelRouter 共用的模型路由器
 */
@RestController
@RequestMapping("/aidevops/ai")
public class McpController
{
    private static final Logger log = LoggerFactory.getLogger(McpController.class);

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

    /**
     * MCP 工具回调缓存（成功一次后复用）。
     *
     * <p>Spring AI 每次请求都会调 {@code ToolCallbackProvider.getToolCallbacks()}，对每个外部
     * MCP server 发 listTools 远程调用。工具列表极少变化，缓存后免去每请求的远程往返；
     * 拉取失败时降级（见 {@link #mcpToolCallbacksWithFallback()}），成功前每次请求自动重试。</p>
     */
    private volatile ToolCallback[] cachedMcpToolCallbacks;

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
     * <p>与 {@link AIToolController#tool} 的区别：调用链多了 MCP 工具——
     * {@code .tools(weatherTools, shellTools)} + {@code .toolCallbacks(mcpTools)}。
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
            AiModelRouter.Resolved routed = AiModelRouter.route(model, anthropicChatClient, deepSeekChatClient);
            // 本地工具 + MCP 工具并存注册，模型自主路由全部工具
            String reply = routed.client().prompt()
                    .user(request.message())
                    .tools(weatherTools, shellTools)
                    .toolCallbacks(mcpToolCallbacksWithFallback())
                    .call()
                    .content();
            // Map.of 不接受 null value：模型无回复时 .content() 返回 null，直接 Map.of 会 NPE，故判空兜底
            Map<String, Object> data = new HashMap<>();
            data.put("reply", reply == null ? "" : reply);
            data.put("model", routed.model());
            return AjaxResult.success(data);
        }
        catch (Exception e)
        {
            // 打印完整异常栈定位根因——MCP SDK 的异常链深层原因常被 getMessage() 吞掉
            // （如"Client failed to initialize listing tools"只说"列表工具失败"，不说是哪个 client、什么错）
            log.error("[MCP] 工具调用失败，完整异常栈：", e);
            return AjaxResult.error("MCP 工具调用失败：" + e.getMessage());
        }
    }

    /**
     * 获取 MCP 工具回调（缓存 + 降级）。
     *
     * <p>不做缓存/降级时，任一外部 MCP server 不可用（如钉钉凭据缺失、网络不通）会让
     * {@code getToolCallbacks()} 整体抛异常——本地工具和其余 server 的工具一并不可用。
     * 此处成功一次后缓存复用（工具列表极少变化）；失败降级为空数组，本次请求仅损失
     * 外部 MCP 工具，本地工具照常可用，且下次请求会自动重试拉取。</p>
     *
     * @return MCP server 的全部工具回调；拉取失败时返回空数组（降级）
     */
    private ToolCallback[] mcpToolCallbacksWithFallback()
    {
        ToolCallback[] cached = cachedMcpToolCallbacks;
        if (cached != null)
        {
            return cached;
        }
        try
        {
            cached = mcpToolCallbackProvider.getToolCallbacks();
            cachedMcpToolCallbacks = cached;
            return cached;
        }
        catch (Exception e)
        {
            log.warn("[MCP] 外部 MCP server 工具列表获取失败，本次请求降级为仅本地工具：{}", e.getMessage());
            return new ToolCallback[0];
        }
    }
}
