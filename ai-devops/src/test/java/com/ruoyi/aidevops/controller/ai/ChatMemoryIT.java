package com.ruoyi.aidevops.controller.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.observation.ObservationRegistry;

/**
 * 对话记忆链路端到端集成测试（第14篇）
 *
 * <p>目的：验证 {@link ChatController} 接口改造后的记忆链路真正能工作——同一 conversationId 的多轮对话，
 * 模型能记住前几轮的信息。这是对接口端到端行为的直接断言验证，不靠人眼看回复。</p>
 *
 * <p>做法：绕开 Spring 容器（不依赖应用启动、不碰数据库/Redis），手动构造带记忆 advisor 的 ChatClient
 * ——与 {@link com.ruoyi.aidevops.config.AiClientConfig} 的 {@code buildChatClientWithMemory} 方法完全相同的链路。
 * 然后模拟 ChatController 的两轮调用（传相同 conversationId），断言第二轮回复包含第一轮的关键信息。</p>
 *
 * <p>与 {@link com.ruoyi.aidevops.MemoryAI}（main 方法）的区别：MemoryAI 是手动跑、人眼看结果的冒烟验证；
 * 本测试是 JUnit 断言、可重复跑的自动化验证。两者覆盖的记忆链路相同，但本测试能进 CI。</p>
 *
 * <p>守卫：检查环境变量 {@code DEEPSEEK_API_KEY} 是否就绪，缺失则跳过（不破坏 CI）。</p>
 *
 * @author shandongdong
 * @see com.ruoyi.aidevops.config.AiClientConfig 接口侧记忆 advisor 挂载（本测试复用其链路）
 * @see ChatController 接口调用方（本测试模拟其行为）
 */
@DisplayName("对话记忆链路端到端集成测试（第14篇）")
class ChatMemoryIT
{
    /** DeepSeek API Key 环境变量名（与 DeepSeekChatIT 一致） */
    private static final String ENV_API_KEY = "DEEPSEEK_API_KEY";

    /** 记忆窗口大小（与 AiClientConfig.MEMORY_MAX_MESSAGES 一致） */
    private static final int MAX_MESSAGES = 20;

    /**
     * 核心验证：同一 conversationId 的两轮对话，第二轮答出第一轮的信息。
     *
     * <p>场景：第一轮告诉模型"我叫东东"，第二轮问"我叫什么名字"，断言回复包含"东东"。</p>
     *
     * <p>触发条件：环境变量 {@code DEEPSEEK_API_KEY} 就绪。缺失则 JUnit 跳过（SKIP）。</p>
     */
    @Test
    @DisplayName("同一会话两轮对话：第二轮能记住第一轮的信息")
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+",
            disabledReason = "未设置 " + ENV_API_KEY + "，跳过真实联网测试")
    void chatMemory_sameConversation_shouldRememberPreviousTurn()
    {
        // 0. 兜底守卫
        String apiKey = System.getenv(ENV_API_KEY);
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "未设置 " + ENV_API_KEY + "，跳过真实联网测试");

        // 1. 构造带记忆的 ChatClient（与 AiClientConfig.buildChatClientWithMemory 相同的链路）
        ChatModel chatModel = createDeepSeekChatModel(apiKey);
        ChatClient chatClient = buildChatClientWithMemory(chatModel);

        String conversationId = "test-session-memory-1";

        // 2. 第一轮：告诉模型用户名（模拟 ChatController.chat 的调用）
        String reply1 = chatClient.prompt()
                .user("我叫东东，我住在杭州")
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        assertNotNull(reply1, "第一轮回复不应为 null");
        assertFalse(reply1.trim().isEmpty(), "第一轮回复不应为空白");
        System.out.println("第一轮问：我叫东东，我住在杭州");
        System.out.println("第一轮答：" + reply1);

        // 3. 第二轮：问模型用户名（同 conversationId，记忆应生效）
        String reply2 = chatClient.prompt()
                .user("我叫什么名字？")
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        assertNotNull(reply2, "第二轮回复不应为 null");
        assertFalse(reply2.trim().isEmpty(), "第二轮回复不应为空白");
        System.out.println("第二轮问：我叫什么名字？");
        System.out.println("第二轮答：" + reply2);

        // 4. 断言：第二轮回复包含"东东"——证明记忆生效
        assertTrue(reply2.contains("东东"),
                "记忆应生效——第二轮回复应包含\"东东\"，实际回复：" + reply2);
    }

    /**
     * 会话隔离验证：不同 conversationId 的请求互不串扰。
     *
     * <p>场景：会话 A 告诉模型"我叫东东"，会话 B 问"我叫什么名字"，断言会话 B 答不出"东东"。</p>
     */
    @Test
    @DisplayName("不同会话隔离：会话 B 答不出会话 A 的信息")
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+",
            disabledReason = "未设置 " + ENV_API_KEY + "，跳过真实联网测试")
    void chatMemory_differentConversations_shouldNotCrossTalk()
    {
        String apiKey = System.getenv(ENV_API_KEY);
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "未设置 " + ENV_API_KEY + "，跳过真实联网测试");

        ChatModel chatModel = createDeepSeekChatModel(apiKey);
        ChatClient chatClient = buildChatClientWithMemory(chatModel);

        // 会话 A：告诉模型用户名
        String replyA = chatClient.prompt()
                .user("我叫东东")
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "session-a"))
                .call()
                .content();
        assertNotNull(replyA, "会话A回复不应为 null");
        System.out.println("会话A问：我叫东东");
        System.out.println("会话A答：" + replyA);

        // 会话 B：问用户名（不同 conversationId）
        String replyB = chatClient.prompt()
                .user("我叫什么名字？")
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "session-b"))
                .call()
                .content();
        assertNotNull(replyB, "会话B回复不应为 null");
        System.out.println("会话B问：我叫什么名字？");
        System.out.println("会话B答：" + replyB);

        // 断言：会话 B 不应包含"东东"——隔离生效
        assertFalse(replyB.contains("东东"),
                "会话隔离应生效——会话B回复不应包含\"东东\"，实际回复：" + replyB);
    }

    /**
     * 构造带记忆 advisor 的 ChatClient——与 {@link com.ruoyi.aidevops.config.AiClientConfig#buildChatClientWithMemory}
     * 完全相同的链路，确保测试覆盖的就是接口侧的实际行为。
     */
    private static ChatClient buildChatClientWithMemory(ChatModel chatModel)
    {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        return ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    /**
     * 手动构造 DeepSeekChatModel（复用 {@link DeepSeekChatIT} 的链路）。
     */
    private static ChatModel createDeepSeekChatModel(String apiKey)
    {
        DeepSeekConnectionProperties conn = new DeepSeekConnectionProperties();
        conn.setApiKey(apiKey);
        conn.setBaseUrl("https://api.deepseek.com");

        DeepSeekChatProperties chat = new DeepSeekChatProperties();
        chat.setModel("deepseek-flash");

        DefaultToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        ObjectProvider<RestClient.Builder> restClientProvider = fromObjectFactory(RestClient::builder);
        ObjectProvider<WebClient.Builder> webClientProvider = fromObjectFactory(WebClient::builder);
        ObjectProvider<?> noopProvider = fromObjectFactory(() -> null);

        DeepSeekChatAutoConfiguration autoConfig = new DeepSeekChatAutoConfiguration();
        return autoConfig.deepSeekChatModel(
                conn, chat,
                restClientProvider,
                webClientProvider,
                toolCallingManager,
                castProvider(noopProvider),
                castProvider(noopProvider),
                castProvider(noopProvider),
                castProvider(noopProvider)
        );
    }

    /** 由 ObjectFactory 构造一个 ObjectProvider（复用 DeepSeekChatIT 的实现） */
    private static <T> ObjectProvider<T> fromObjectFactory(ObjectFactory<T> factory)
    {
        return new ObjectProvider<>()
        {
            @Override
            public T getObject()
            {
                try
                {
                    return factory.getObject();
                }
                catch (Exception e)
                {
                    return null;
                }
            }

            @Override
            public T getIfAvailable()
            {
                return getObject();
            }

            @Override
            public T getIfUnique()
            {
                return getObject();
            }

            @Override
            public java.util.stream.Stream<T> stream()
            {
                return java.util.stream.Stream.empty();
            }

            @Override
            public java.util.stream.Stream<T> orderedStream()
            {
                return java.util.stream.Stream.empty();
            }

            @Override
            public java.util.Iterator<T> iterator()
            {
                return java.util.Collections.emptyIterator();
            }
        };
    }

    /** 安全地将 ObjectProvider<?> 强转为指定泛型 */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> castProvider(ObjectProvider<?> provider)
    {
        return (ObjectProvider<T>) provider;
    }
}
