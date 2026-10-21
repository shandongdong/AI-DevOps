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

import com.ruoyi.aidevops.tool.shell.ShellExecutor;
import com.ruoyi.aidevops.tool.shell.ShellProperties;
import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherProperties;
import com.ruoyi.aidevops.tool.weather.WeatherService;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

import io.micrometer.observation.ObservationRegistry;

/**
 * Agent 反思循环链路端到端集成测试（第15篇）
 *
 * <p>目的：验证 {@link AgentController} 接口改造后的反思循环链路真正能工作——
 * 外层 {@code while} 循环驱动，模型 THINK/FINAL 判停，MAX_STEPS 兜底，带记忆 + 工具。</p>
 *
 * <p>做法：绕开 Spring 容器（不依赖应用启动、不碰数据库/Redis），手动构造带记忆 advisor + 工具的 ChatClient
 * ——与 {@link com.ruoyi.aidevops.config.AiClientConfig} 的记忆链路 + {@link AgentController#agent}
 * 的反思循环逻辑完全相同。然后模拟 {@link AgentController#agent} 的反思循环调用，断言能拿到最终答复。</p>
 *
 * <p>与 {@link com.ruoyi.aidevops.AgentAI}（main 方法三段对比）的区别：AgentAI 是手动跑、人眼看结果的冒烟验证；
 * 本测试是 JUnit 断言、可重复跑的自动化验证。两者覆盖的反思循环链路相同，但本测试能进 CI。</p>
 *
 * <p>守卫：检查环境变量 {@code DEEPSEEK_API_KEY} 是否就绪，缺失则跳过（不破坏 CI）。</p>
 *
 * @author shandongdong
 * @see AgentController 接口调用方（本测试模拟其反思循环逻辑）
 * @see com.ruoyi.aidevops.config.AiClientConfig 记忆 advisor 挂载（本测试复用其链路）
 */
@DisplayName("Agent 反思循环链路端到端集成测试（第15篇）")
class AgentIT
{
    /** DeepSeek API Key 环境变量名（与 DeepSeekChatIT 一致） */
    private static final String ENV_API_KEY = "DEEPSEEK_API_KEY";

    /** 记忆窗口大小（与 AiClientConfig.MEMORY_MAX_MESSAGES 一致） */
    private static final int MAX_MESSAGES = 20;

    /** 外层循环最大步数兜底（与 AgentController.MAX_STEPS 一致） */
    private static final int MAX_STEPS = 8;

    /** 测试用任务（与 AgentAI.TASK 同构：需要查天气 + 查系统时间，天然要多步） */
    private static final String TASK = "查一下杭州当前天气，如果适合户外运动，"
            + "再查一下当前系统时间，最后给我一句话总结。";

    /**
     * 核心验证：外层反思循环能拿到最终答复。
     *
     * <p>场景：模型带记忆 + 工具跑反思循环，断言最终拿到含 FINAL 标记的答复，且步数不超过 MAX_STEPS。</p>
     *
     * <p>触发条件：环境变量 {@code DEEPSEEK_API_KEY} 就绪。缺失则 JUnit 跳过（SKIP）。</p>
     */
    @Test
    @DisplayName("反思循环：能拿到最终答复且步数不超限")
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+",
            disabledReason = "未设置 " + ENV_API_KEY + "，跳过真实联网测试")
    void agent_reflectionLoop_shouldReturnFinalAnswer()
    {
        // 0. 兜底守卫
        String apiKey = System.getenv(ENV_API_KEY);
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "未设置 " + ENV_API_KEY + "，跳过真实联网测试");

        // 1. 构造带记忆 + 工具的 ChatClient（与 AgentController 注入的 ChatClient 链路相同）
        ChatModel chatModel = createDeepSeekChatModel(apiKey);
        WeatherTools weatherTools = new WeatherTools(new WeatherService(new WeatherProperties()));
        ShellTools shellTools = new ShellTools(new ShellExecutor(new ShellProperties()));
        ChatClient chatClient = buildClientWithMemoryAndTools(chatModel, weatherTools, shellTools);

        String conversationId = "agent-test-session-1";

        // 2. 跑外层反思循环（模拟 AgentController.agent 的核心逻辑）
        int step = 0;
        String finalAnswer = null;
        while (step < MAX_STEPS)
        {
            step++;
            String turnPrompt = "当前是第 " + step + " 步（最多 " + MAX_STEPS + " 步）。\n"
                    + "任务：" + TASK + "\n"
                    + "按以下格式回答（第一行必须是标记）：\n"
                    + "  THINK —— 反思已掌握信息是否足够，说明还缺什么、打算调哪个工具\n"
                    + "  FINAL —— 信息已足够，直接给出最终答案（不再调工具）\n"
                    + "先反思，再决定下一步。";

            String output = chatClient.prompt()
                    .system("你是一个会反思的 Agent。每一步【最多只调用一个工具】，"
                            + "调用后先反思已掌握信息是否足够，再决定继续调工具（THINK）"
                            + "还是给出最终答案（FINAL）。"
                            + "可以调用查天气和执行只读 shell 命令的工具。")
                    .user(turnPrompt)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .tools(weatherTools, shellTools)
                    .call()
                    .content();

            System.out.println("第 " + step + " 步输出：\n" + output);
            System.out.println("---");

            if (output != null && output.contains("FINAL"))
            {
                finalAnswer = output;
                break;
            }
        }

        // 3. 断言
        assertNotNull(finalAnswer, "反思循环应拿到最终答复（未达到步数兜底）");
        assertTrue(finalAnswer.contains("FINAL"),
                "最终答复应含 FINAL 标记，实际：" + finalAnswer);
        assertTrue(step <= MAX_STEPS,
                "步数应不超过 MAX_STEPS=" + MAX_STEPS + "，实际：" + step);
        // 最终答复不应是"无法获取/无法查询"类空话（证明模型调了工具拿到实时信息，不是凭空编的）
        assertFalse(finalAnswer.contains("无法获取") && finalAnswer.contains("无法查询"),
                "最终答复不应是「无法」类空话（应已调工具拿到实时信息），实际：" + finalAnswer);
        System.out.println("===== 最终答复（" + step + " 步完成）=====");
        System.out.println(finalAnswer);
    }

    /**
     * 构造带记忆 advisor 的 ChatClient——与 {@link ChatController} 注入的 ChatClient 链路相同：
     * 记忆由 {@code MessageChatMemoryAdvisor} 提供（第14篇），工具不在此挂（每次 {@code .prompt().tools()} 挂，
     * 与接口侧一致——避免 defaultTools + prompt.tools 重复注册同名工具导致
     * {@code "Multiple tools with the same name"} 异常）。
     */
    private static ChatClient buildClientWithMemoryAndTools(ChatModel chatModel,
                                                            WeatherTools weatherTools,
                                                            ShellTools shellTools)
    {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        // 只挂记忆 advisor，工具留给每次 .prompt().tools() 挂（与 ChatController 注入的 ChatClient 一致）
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
