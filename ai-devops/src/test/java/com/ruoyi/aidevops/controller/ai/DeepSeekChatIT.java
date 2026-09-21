package com.ruoyi.aidevops.controller.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring AI DeepSeek 对话链路集成测试
 *
 * <p>目的：验证 Spring AI 2.0.1 原生 DeepSeek 模块接入是否真的能调通 DeepSeek 大模型，
 * 不依赖应用启动、不碰数据库。与 {@link ChatControllerIT}（Anthropic 链路）并列，演示同一个项目里
 * 多个模型并存的写法。</p>
 *
 * <p>做法：绕开 Spring 容器，手动 new 出 {@link DeepSeekConnectionProperties} / {@link DeepSeekChatProperties}，
 * 调用自动配置类 {@link DeepSeekChatAutoConfiguration#deepSeekChatModel} 拿到真实的 {@link DeepSeekChatModel}，
 * 再用 {@link ChatClient#create} 包一层发起真实请求。</p>
 *
 * <p>DeepSeek 走 OpenAI 兼容协议（默认 base_url {@code https://api.deepseek.com}），
 * Spring AI 有原生 {@code spring-ai-starter-model-deepseek} 模块，无需套 OpenAI/Anthropic 适配器。</p>
 *
 * <p>守卫：检查环境变量 {@code DEEPSEEK_API_KEY} 是否就绪，缺失则跳过本测试，不破坏 CI。</p>
 *
 * <p>认证方式：Bearer Token（{@code Authorization: Bearer $DEEPSEEK_API_KEY}），由 Spring AI 自动注入。</p>
 *
 * @author shandongdong
 */
@DisplayName("Spring AI DeepSeek 对话链路集成测试")
class DeepSeekChatIT
{
    /** DeepSeek API Key 环境变量名 */
    private static final String ENV_API_KEY = "DEEPSEEK_API_KEY";

    /**
     * 真实调一次 DeepSeek，验证整条链路：ChatClient → DeepSeekChatModel → DeepSeek API → 回复。
     *
     * <p>触发条件：环境变量 {@code DEEPSEEK_API_KEY} 就绪。缺失则 JUnit 跳过本测试（SKIP）。</p>
     */
    @Test
    @DisplayName("真实调用 DeepSeek 返回非空回复")
    @EnabledIfEnvironmentVariable(named = ENV_API_KEY, matches = ".+",
            disabledReason = "未设置 " + ENV_API_KEY + "，跳过真实联网测试")
    void chat_realCall_shouldReturnNonBlankReply()
    {
        // 0. 兜底守卫：注解层之外再用 Assumptions.assumeTrue 确认环境变量就绪
        String apiKey = System.getenv(ENV_API_KEY);
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "未设置 " + ENV_API_KEY + "，跳过真实联网测试");

        // 1. 连接属性：api-key + base-url
        //    DeepSeekConnectionProperties 默认 base_url 就是 https://api.deepseek.com（OpenAI 兼容端点）
        //    DeepSeek 还提供 Anthropic 兼容端点 https://api.deepseek.com/anthropic，但走原生 DeepSeek 模块用默认即可
        //    注意：DeepSeek 的连接属性不像 Anthropic 那样有 setTimeout，超时由底层 RestClient 控制
        DeepSeekConnectionProperties conn = new DeepSeekConnectionProperties();
        conn.setApiKey(apiKey);
        // base_url 保持默认（https://api.deepseek.com）；如需指向 Anthropic 兼容端点：
        //   conn.setBaseUrl("https://api.deepseek.com/anthropic");（但那应改用 Anthropic 模块）
        conn.setBaseUrl("https://api.deepseek.com");

        // 2. 对话属性：指定模型
        //    deepseek-v4-pro 是 DeepSeek 当前主力对话模型（见 DeepSeek 官方文档 api-docs.deepseek.com）
        DeepSeekChatProperties chat = new DeepSeekChatProperties();
        chat.setModel("deepseek-v4-pro");

        // 3. 工具调用管理器：无工具场景用默认实现
        DefaultToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        // 4. 可选依赖（RestClient.Builder / WebClient.Builder / RetryTemplate / ResponseErrorHandler /
        //    ObservationRegistry / ChatModelObservationConvention）全不给，用空 ObjectProvider 表示"无"
        ObjectProvider<RestClient.Builder> restClientProvider = fromObjectFactory(RestClient::builder);
        ObjectProvider<WebClient.Builder> webClientProvider = fromObjectFactory(WebClient::builder);
        ObjectProvider<?> noopProvider = fromObjectFactory(() -> null);

        // 5. 调自动配置工厂方法，拿到真实 DeepSeekChatModel
        DeepSeekChatAutoConfiguration autoConfig = new DeepSeekChatAutoConfiguration();
        DeepSeekChatModel chatModel = autoConfig.deepSeekChatModel(
                conn, chat,
                restClientProvider,                       // RestClient.Builder（同步请求）
                webClientProvider,                        // WebClient.Builder（流式请求）
                toolCallingManager,
                castProvider(noopProvider),               // RetryTemplate
                castProvider(noopProvider),               // ResponseErrorHandler
                castProvider(noopProvider),               // ObservationRegistry
                castProvider(noopProvider)                // ChatModelObservationConvention
        );

        // 6. 用 ChatClient 包一层（与 ChatController 的用法对齐），发起真实请求
        ChatClient chatClient = ChatClient.create(chatModel);
        String reply = chatClient.prompt()
                .user("用一句话介绍你自己")
                .call()
                .content();

        // 断言：回复非空且非空白
        assertNotNull(reply, "大模型回复不应为 null");
        assertFalse(reply.trim().isEmpty(), "大模型回复不应为空白");
        System.out.println("===== DeepSeek 回复 =====");
        System.out.println(reply);
        System.out.println("=========================");
    }

    /**
     * 由 {@link ObjectFactory} 构造一个 {@link ObjectProvider}，{@code getIfAvailable} 返回工厂结果，
     * 其余可选项返回空。Spring 7 的 {@code ObjectProvider} 默认 {@code stream()} 抛
     * {@link UnsupportedOperationException}，这里补上空流避免自动配置内部调用 {@code orderedStream()} 崩溃。
     */
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

    /**
     * 安全地将 {@code ObjectProvider<?>} 强转为指定泛型。
     * 运行时 provider 始终返回 null，泛型擦除后无实际类型差异。
     */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> castProvider(ObjectProvider<?> provider)
    {
        return (ObjectProvider<T>) provider;
    }
}
