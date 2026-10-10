package com.ruoyi.aidevops.controller.ai;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
 * ai-devops 工具调用接口（多模型路由 + 工具注入）。
 *
 * <p>承接 {@link ChatController}（第10篇，纯对话），本接口在第10篇基础上加 {@code .tools()} 注入工具，
 * 让模型从"动嘴"升级到"动手"——能自己决定调天气查询或 Shell 命令执行工具，拿到结果再回复。</p>
 *
 * <p>路由风格与 {@link ChatController} 一致：按 {@code model} 参数切换 ChatClient，默认走 Anthropic，
 * 路由逻辑由 {@link AiModelRouter} 统一处理。工具对象（{@link WeatherTools} / {@link ShellTools}）
 * 由容器注入，每个请求都注入同一组工具。</p>
 *
 * <p>权限：验证阶段标 {@code @Anonymous} 免登录，后续接入业务移除并配 {@code @PreAuthorize}。</p>
 *
 * @author shandongdong
 * @see ChatController 第10篇纯对话接口（本接口在其基础上加工具）
 * @see AiModelRouter 共用的模型路由器
 * @see WeatherTools 天气查询工具
 * @see ShellTools Shell 命令执行工具
 */
@RestController
@RequestMapping("/aidevops/ai")
public class AIToolController
{
    private static final Logger log = LoggerFactory.getLogger(AIToolController.class);

    private final ChatClient anthropicChatClient;
    private final ChatClient deepSeekChatClient;
    private final WeatherTools weatherTools;
    private final ShellTools shellTools;

    public AIToolController(@Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
                         @Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient,
                         WeatherTools weatherTools,
                         ShellTools shellTools)
    {
        this.anthropicChatClient = anthropicChatClient;
        this.deepSeekChatClient = deepSeekChatClient;
        this.weatherTools = weatherTools;
        this.shellTools = shellTools;
    }

    /**
     * 工具调用接口：接收用户消息，注入工具，返回模型回复。
     *
     * <p>与 {@link ChatController#chat} 的唯一区别：调用链多了 {@code .tools(weatherTools, shellTools)}。
     * 模型据此决定是否调工具——不调工具时行为与第10篇纯对话完全一致，调工具时框架自动执行
     * "模型决定调工具→执行→结果喂回→模型继续"的循环，调用方无需关心。</p>
     *
     * @param model   模型标识（query param，可选，默认 anthropic）：anthropic / deepseek
     * @param request 包含 message 字段的请求体（复用 {@link ChatRequest}）
     * @return AjaxResult 包含模型回复内容
     */
    @Anonymous
    @PostMapping("/tool")
    public AjaxResult tool(@RequestParam(required = false) String model,
                           @RequestBody ChatRequest request)
    {
        if (request.message() == null || request.message().trim().isEmpty())
        {
            return AjaxResult.error("消息内容不能为空");
        }
        try
        {
            AiModelRouter.Resolved routed = AiModelRouter.route(model, anthropicChatClient, deepSeekChatClient);
            // 与 ChatController 的唯一区别：注入工具，让模型能"动手"
            String reply = routed.client().prompt()
                    .user(request.message())
                    .tools(weatherTools, shellTools)
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
            // 完整异常栈进日志（e.getMessage() 可能为 null，如 NPE，仅靠响应信息无法排障）
            log.error("[tool] 工具调用失败 model={}", model, e);
            return AjaxResult.error("工具调用失败：" + e.getMessage());
        }
    }
}
