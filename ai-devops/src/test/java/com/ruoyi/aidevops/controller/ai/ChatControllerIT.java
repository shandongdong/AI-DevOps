package com.ruoyi.aidevops.controller.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Spring AI Anthropic 对话链路集成测试
 *
 * <p>目的：验证 Spring AI 2.0.1 + Anthropic 接入是否真的能调通大模型，不依赖应用启动、不碰数据库。</p>
 *
 * <p>做法：绕开 Spring 容器，手动 new 出 {@link AnthropicConnectionProperties} / {@link AnthropicChatProperties}，
 * 调用自动配置类 {@link AnthropicChatAutoConfiguration#anthropicChatModel} 拿到真实的 {@link AnthropicChatModel}，
 * 再用 {@link ChatClient#create} 包一层，发起一次真实对话请求。</p>
 *
 * <p>守卫：通过 {@link EnabledIfEnvironmentVariable} 检查 ANTHROPIC_AUTH_TOKEN 等环境变量。
 * 缺凭证时本测试自动跳过（不报错、不破坏 CI），只在本地 export 好变量后才会真实联网调用。</p>
 *
 * <p>凭证从环境变量读，与 application-dev.yml 的占位符一致，代码内不含明文 token。</p>
 *
 * @author shandongdong
 */
@DisplayName("Spring AI Anthropic 对话链路集成测试")
class ChatControllerIT
{
    /** 环境变量名，与 application-dev.yml 占位符一致 */
    private static final String ENV_BASE_URL = "ANTHROPIC_BASE_URL";
    private static final String ENV_AUTH_TOKEN = "ANTHROPIC_AUTH_TOKEN";
    private static final String ENV_MODEL = "ANTHROPIC_MODEL";

    /**
     * 真实调一次 Anthropic，验证整条链路：ChatClient → AnthropicChatModel → Anthropic API → 回复。
     *
     * <p>触发条件：三个环境变量都就绪。任一缺失则 JUnit 跳过本测试（SKIP）。</p>
     */
    @Test
    @DisplayName("真实调用 Anthropic 返回非空回复")
    @EnabledIfEnvironmentVariable(named = ENV_AUTH_TOKEN, matches = ".+", disabledReason = "未设置 " + ENV_AUTH_TOKEN + "，跳过真实联网测试")
    @EnabledIfEnvironmentVariable(named = ENV_BASE_URL, matches = ".+", disabledReason = "未设置 " + ENV_BASE_URL + "，跳过真实联网测试")
    @EnabledIfEnvironmentVariable(named = ENV_MODEL, matches = ".+", disabledReason = "未设置 " + ENV_MODEL + "，跳过真实联网测试")
    void chat_realCall_shouldReturnNonBlankReply()
    {
        // 0. 兜底守卫：注解层 @EnabledIfEnvironmentVariable 在某些 surefire/JUnit6 组合下可能失效，
        //    方法体内再用 Assumptions.assumeTrue 确认三个变量都就绪，缺一即跳过（不报错、不破坏 CI）
        String authToken = System.getenv(ENV_AUTH_TOKEN);
        String baseUrl = System.getenv(ENV_BASE_URL);
        String model = System.getenv(ENV_MODEL);
        assumeTrue(authToken != null && !authToken.isBlank(),
                "未设置 " + ENV_AUTH_TOKEN + "，跳过真实联网测试");
        assumeTrue(baseUrl != null && !baseUrl.isBlank(),
                "未设置 " + ENV_BASE_URL + "，跳过真实联网测试");
        assumeTrue(model != null && !model.isBlank(),
                "未设置 " + ENV_MODEL + "，跳过真实联网测试");

        // 1. 连接属性：api-key + base-url，全部来自环境变量
        AnthropicConnectionProperties conn = new AnthropicConnectionProperties();
        conn.setApiKey(authToken);
        conn.setBaseUrl(baseUrl);
        conn.setTimeout(Duration.ofSeconds(60));

        // 2. 对话属性：指定模型
        AnthropicChatProperties chat = new AnthropicChatProperties();
        chat.setModel(model);

        // 3. 工具调用管理器：无工具场景用默认实现即可
        DefaultToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        // 4. 可选依赖（ObservationRegistry / MeterRegistry / 自定义 Convention / HttpClient 定制器）全不给
        // ObjectProvider 的方法在 Spring 7 全是 default，用 ObjectFactory lambda 造一个始终返回 null 的实现即可
        ObjectProvider<?> noopProvider = fromObjectFactory(() -> null);

        // 5. 调自动配置工厂方法，拿到真实 AnthropicChatModel
        AnthropicChatAutoConfiguration autoConfig = new AnthropicChatAutoConfiguration();
        AnthropicChatModel chatModel = autoConfig.anthropicChatModel(
                conn, chat, toolCallingManager,
                castProvider(noopProvider), // ObservationRegistry
                castProvider(noopProvider), // MeterRegistry
                castProvider(noopProvider), // ChatModelObservationConvention
                castProvider(noopProvider) // AnthropicHttpClientBuilderCustomizer
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
        System.out.println("===== Anthropic 回复 =====");
        System.out.println(reply);
        System.out.println("=========================");
    }

    /**
     * 由 {@link ObjectFactory} 构造一个始终返回其结果的 {@link ObjectProvider}。
     * Spring 的 {@code ObjectProvider} 在 Spring 7 中所有方法均有默认实现，
     * 这里用最简单的 ObjectFactory lambda（返回 null）即可表达"无此可选依赖"。
     */
    private static <T> ObjectProvider<T> fromObjectFactory(ObjectFactory<T> factory)
    {
        // ObjectProvider 继承 ObjectFactory，Spring 未提供直接的适配器，用匿名实现桥接
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

            // Spring 7 的 ObjectProvider 在 stream() 上默认抛 UnsupportedOperationException，
            // 自动配置内部会用 orderedStream()（依赖 stream()），这里返回空流表示"无此可选依赖"
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
     * 安全地将 {@code ObjectProvider<?>} 强转为指定泛型，绕编译器泛型协变限制。
     * 运行时 provider 始终返回 null，泛型擦除后无实际类型差异。
     */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> castProvider(ObjectProvider<?> provider)
    {
        return (ObjectProvider<T>) provider;
    }
}
