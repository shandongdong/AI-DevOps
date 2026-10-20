package com.ruoyi.aidevops;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.observation.ObservationRegistry;

/**
 * 第14篇 main 方法：手动构造带对话记忆的 ChatClient，验证记忆生效。
 *
 * <p>核心验证：</p>
 * <ol>
 *   <li>同一 conversationId 的多轮对话，模型能记住前几轮的信息（"我叫东东" → 后面答出"东东"）</li>
 *   <li>不同 conversationId 互不串扰（换会话后答不出前一个会话的信息）</li>
 * </ol>
 *
 * <p>记忆链路 3 步（手动构造，看清原理）：</p>
 * <ol>
 *   <li>ChatMemoryRepository（InMemory）→ ChatMemory（MessageWindowChatMemory 窗口策略）</li>
 *   <li>ChatMemory → MessageChatMemoryAdvisor（before 取历史 / after 写回）</li>
 *   <li>ChatClient.builder(chatModel).defaultAdvisors(advisor).build()（挂记忆）</li>
 * </ol>
 *
 * <p>依赖环境变量 {@code DEEPSEEK_API_KEY}。</p>
 *
 * @author shandongdong
 */
public class MemoryAI
{
    /** 记忆窗口：保留最近 N 轮消息，防 token 爆 */
    private static final int MAX_MESSAGES = 20;

    public static void main(String[] args)
    {
        // 1. 手动构造 ChatModel（复用 HelloAI 的 DeepSeek 构造链路）
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        ChatModel chatModel = createDeepSeekChatModel(apiKey);

        // 2. 手动构造 ChatMemory（看清记忆链路第 1 步：仓库 → 窗口策略）
        //    InMemoryChatMemoryRepository：内存 Map<conversationId, List<Message>>，重启即丢
        //    MessageWindowChatMemory：只保留最近 maxMessages 条，防上下文撑爆 token 限制
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();

        // 3. 构造记忆 advisor（看清记忆链路第 2 步：ChatMemory → advisor）
        //    advisor 在 before 阶段从 ChatMemory.get(convId) 取历史消息塞进请求
        //    在 after 阶段把本轮用户消息 + 模型回复写回 ChatMemory.add(convId, ...)
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        // 4. 构造带记忆的 ChatClient（看清记忆链路第 3 步：挂到 ChatClient）
        //    关键：用 builder() 不用 create()——create 返回预配置实例不能挂 advisor
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();

        System.out.println("===== 第一组：同一会话（demo-session-1），验证记忆生效 =====");
        ask(chatClient, "demo-session-1", "我叫东东，我住在杭州");
        ask(chatClient, "demo-session-1", "我叫什么名字？");      // 应答出"东东"
        ask(chatClient, "demo-session-1", "我住在哪里？");        // 应答出"杭州"

        System.out.println();
        System.out.println("===== 第二组：换会话（other-session），验证会话隔离 =====");
        ask(chatClient, "other-session", "我叫什么名字？");      // 答不出（不同会话，没有东东的记忆）

        System.out.println();
        System.out.println("===== 第三组：回到第一个会话，验证记忆还在 =====");
        ask(chatClient, "demo-session-1", "我前面告诉过你我叫什么，再说一次？");  // 应答出"东东"
    }

    /**
     * 发一轮对话，打印问 + 答。
     *
     * @param client   带记忆的 ChatClient
     * @param convId   会话 ID（同 ID 共享记忆）
     * @param message  用户消息
     */
    static void ask(ChatClient client, String convId, String message)
    {
        String reply = client.prompt()
                .user(message)
                // 传会话 ID：advisor 据此从 ChatMemory 取历史消息 + 写回新消息
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, convId))
                .call()
                .content();
        System.out.println("[" + convId + "] 问：" + message);
        System.out.println("[" + convId + "] 答：" + reply);
        System.out.println("---");
    }

    /**
     * 手动构造 DeepSeek ChatModel（复用 HelloAI 的链路，绕开 Spring 容器）。
     */
    private static ChatModel createDeepSeekChatModel(String apiKey)
    {
        DeepSeekConnectionProperties conn = new DeepSeekConnectionProperties();
        conn.setApiKey(apiKey);
        conn.setBaseUrl("https://api.deepseek.com");

        DeepSeekChatProperties chat = new DeepSeekChatProperties();
        chat.setModel("deepseek-flash");

        DefaultToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        DeepSeekChatAutoConfiguration autoConfig = new DeepSeekChatAutoConfiguration();
        return autoConfig.deepSeekChatModel(
                conn, chat,
                emptyProvider(),  // RestClient.Builder
                emptyProvider(),  // WebClient.Builder
                toolCallingManager,
                emptyProvider(),  // RetryTemplate
                emptyProvider(),  // ResponseErrorHandler
                emptyProvider(),  // ObservationRegistry
                emptyProvider()   // ChatModelObservationConvention
        );
    }

    /** 返回一个"空" ObjectProvider：只需实现 stream() 返回空流（Spring 6.2+ 其余方法走默认实现） */
    private static <T> ObjectProvider<T> emptyProvider()
    {
        return new ObjectProvider<>()
        {
            @Override
            public java.util.stream.Stream<T> stream()
            {
                return java.util.stream.Stream.empty();
            }
        };
    }
}
