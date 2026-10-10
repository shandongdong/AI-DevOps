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

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * ai-devops AI 对话接口（多模型路由）
 *
 * <p>支持在同一接口下按 {@code model} 参数切换不同大模型厂商，默认走 Anthropic。
 * 各模型的 {@link ChatClient} bean 由 {@link com.ruoyi.aidevops.config.AiClientConfig} 统一管理，
 * 本 Controller 持有两个带 {@code @Qualifier} 的 {@link ChatClient} 字段，
 * 路由逻辑由 {@link AiModelRouter} 统一处理（与 AIToolController/McpController 共用）。</p>
 *
 * <p>当前支持：</p>
 * <ul>
 *   <li>{@code anthropic}（默认）—— Spring AI 原生 Anthropic 模块</li>
 *   <li>{@code deepseek} —— Spring AI 原生 DeepSeek 模块（OpenAI 兼容协议）</li>
 * </ul>
 *
 * <p>权限说明：当前为验证阶段，接口标 {@code @Anonymous} 免登录，便于直接 curl 验证。
 * 后续接入业务时移除 {@code @Anonymous} 并配 {@code @PreAuthorize} 权限标识。</p>
 *
 * @author shandongdong
 * @see com.ruoyi.aidevops.config.AiClientConfig 多模型 ChatClient bean 定义
 * @see AiModelRouter 共用的模型路由器
 */
@RestController
@RequestMapping("/aidevops/ai")
public class ChatController
{
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient anthropicChatClient;
    private final ChatClient deepSeekChatClient;

    public ChatController(@Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
                         @Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient)
    {
        this.anthropicChatClient = anthropicChatClient;
        this.deepSeekChatClient = deepSeekChatClient;
    }

    /**
     * 对话接口：接收用户消息，返回大模型回复。
     *
     * <p>用 POST + JSON body（原则 VI：对话请求非幂等、消息体可能较长且含敏感信息，不应走 GET 路径参数）。</p>
     *
     * <p>模型路由：{@code model} 走 query param（路由元数据不属于对话内容语义，放 query 更 RESTful，
     * {@link ChatRequest} body 无需改）。路由逻辑见 {@link AiModelRouter#route}——
     * 不传或传空 → 默认 anthropic；非法值 → 抛 {@link IllegalArgumentException} 被 catch 捕获，返回 error。</p>
     *
     * @param model 模型标识（query param，可选，默认 anthropic）：anthropic / deepseek
     * @param request 包含 message 字段的请求体
     * @return AjaxResult 包含模型回复内容
     */
    @Anonymous
    @PostMapping("/chat")
    public AjaxResult chat(@RequestParam(required = false) String model,
                           @RequestBody ChatRequest request)
    {
        if (request.message() == null || request.message().trim().isEmpty())
        {
            return AjaxResult.error("消息内容不能为空");
        }
        try
        {
            AiModelRouter.Resolved routed = AiModelRouter.route(model, anthropicChatClient, deepSeekChatClient);
            String reply = routed.client().prompt()
                    .user(request.message())
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
            log.error("[chat] 调用大模型失败 model={}", model, e);
            return AjaxResult.error("调用大模型失败：" + e.getMessage());
        }
    }
}
