package com.ruoyi.aidevops.controller.ai;

/**
 * AI 对话请求体
 *
 * @param message 用户输入的消息内容
 *
 * @author shandongdong
 */
public record ChatRequest(String message)
{
}
