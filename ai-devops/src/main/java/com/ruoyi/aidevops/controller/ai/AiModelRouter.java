package com.ruoyi.aidevops.controller.ai;

import org.springframework.ai.chat.client.ChatClient;

/**
 * AI 接口共用的模型路由器：按 {@code model} 参数解析目标 {@link ChatClient}。
 *
 * <p>ChatController / AIToolController / McpController 三个接口共用此路由，
 * 新增模型厂商时只需在 {@link #route} 的 switch 里加一个 case（配合
 * {@code AiClientConfig} 追加对应 ChatClient bean），避免 N 个接口 N 份路由拷贝。</p>
 *
 * @author shandongdong
 */
final class AiModelRouter
{
    /** 默认模型标识，未传 model 参数时走它 */
    static final String DEFAULT_MODEL = "anthropic";

    /** 路由结果：规范化后的模型名 + 对应的 ChatClient */
    record Resolved(String model, ChatClient client)
    {
    }

    /**
     * 解析模型参数并路由到对应 ChatClient。
     *
     * @param model     原始 model 参数（null/空白回退默认 {@value DEFAULT_MODEL}，大小写不敏感）
     * @param anthropic Anthropic ChatClient（AiClientConfig 注册）
     * @param deepSeek  DeepSeek ChatClient（AiClientConfig 注册）
     * @return 路由结果（模型名 + ChatClient）
     * @throws IllegalArgumentException model 非法（不在支持列表），由调用方 catch 转错误响应
     */
    static Resolved route(String model, ChatClient anthropic, ChatClient deepSeek)
    {
        // 解析 model：空值回退默认；统一小写便于 switch 匹配
        String resolved = (model == null || model.isBlank())
                ? DEFAULT_MODEL : model.toLowerCase();
        ChatClient client = switch (resolved)
        {
            case "anthropic" -> anthropic;
            case "deepseek" -> deepSeek;
            default -> throw new IllegalArgumentException(
                    "不支持的模型: " + model + "，支持: anthropic, deepseek");
        };
        return new Resolved(resolved, client);
    }

    private AiModelRouter()
    {
    }
}
