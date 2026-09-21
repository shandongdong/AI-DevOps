package com.ruoyi.aidevops.controller.ai;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * ai-devops AI 对话接口（多模型路由）
 *
 * <p>支持在同一接口下按 {@code model} 参数切换不同大模型厂商，默认走 Anthropic。
 * 各模型的 {@link ChatClient} bean 由 {@link com.ruoyi.aidevops.config.AiClientConfig} 统一管理，
 * 本 Controller 持有两个带 {@code @Qualifier} 的 {@link ChatClient} 字段，按 model 参数路由。</p>
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
 */
@RestController
@RequestMapping("/aidevops/ai")
public class ChatController
{
    /** 默认模型标识，未传 model 参数时走它 */
    private static final String DEFAULT_MODEL = "anthropic";

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
     * {@link ChatRequest} body 无需改）。不传或传空 → 默认 {@value DEFAULT_MODEL}；
     * 非法值 → 抛 {@link IllegalArgumentException} 被 catch 捕获，返回 error。</p>
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
            // 解析 model：空值回退默认；统一小写便于 switch 匹配
            String resolvedModel = (model == null || model.isBlank())
                    ? DEFAULT_MODEL : model.toLowerCase();
            // 路由到对应 ChatClient；非法值抛异常，走下方 catch 返回 error
            ChatClient client = switch (resolvedModel)
            {
                case "anthropic" -> anthropicChatClient;
                case "deepseek" -> deepSeekChatClient;
                default -> throw new IllegalArgumentException(
                        "不支持的模型: " + model + "，支持: anthropic, deepseek");
            };
            String reply = client.prompt()
                    .user(request.message())
                    .call()
                    .content();
            return AjaxResult.success(Map.of("reply", reply, "model", resolvedModel));
        }
        catch (Exception e)
        {
            return AjaxResult.error("调用大模型失败：" + e.getMessage());
        }
    }
}
