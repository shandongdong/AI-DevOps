package com.ruoyi.aidevops.controller.ai;

/**
 * AI 对话请求体
 *
 * @param message        用户输入的消息内容
 * @param conversationId 会话 ID（可选）：同一 ID 的多轮对话共享记忆，模型能记住前几轮说过的话。
 *                       不传时调用方用默认 ID 退化单会话（向后兼容）。生产建议用真实用户/会话 ID 区分会话。
 *
 * @author shandongdong
 */
public record ChatRequest(String message, String conversationId)
{
}
