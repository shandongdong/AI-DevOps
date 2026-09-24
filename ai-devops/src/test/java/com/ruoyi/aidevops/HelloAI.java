package com.ruoyi.aidevops;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.observation.ObservationRegistry;

/**
 * 冒烟验证类：在 main 里直接调 DeepSeek，验证多模型链路。
 * 依赖环境变量 DEEPSEEK_API_KEY。
 */
public class HelloAI
{
    public static void main(String[] args)
    {
        // 1. 从环境变量获取 apikey
        String apiKey = System.getenv("DEEPSEEK_API_KEY");

        // 2. 连接属性：api-key + base_url（默认就是 https://api.deepseek.com）
        DeepSeekConnectionProperties conn = new DeepSeekConnectionProperties();
        conn.setApiKey(apiKey);
        conn.setBaseUrl("https://api.deepseek.com");

        // 3. 对话属性：指定模型
        DeepSeekChatProperties chat = new DeepSeekChatProperties();
        chat.setModel("deepseek-flash");

        // 4. 工具调用管理器：本示例不调用工具，但 DeepSeekChatModel 构造器强制非空，喂默认空实现占位
        DefaultToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        // 5. 可选依赖全缺省：统一给"空 provider"，源码内置 getIfAvailable 默认回退兜底
        ObjectProvider<RestClient.Builder> restClientProvider = emptyProvider();
        ObjectProvider<WebClient.Builder> webClientProvider = emptyProvider();
        ObjectProvider<RetryTemplate> retryProvider = emptyProvider();
        ObjectProvider<ResponseErrorHandler> errorHandlerProvider = emptyProvider();
        ObjectProvider<ObservationRegistry> observationProvider = emptyProvider();
        ObjectProvider<ChatModelObservationConvention> conventionProvider = emptyProvider();

        // 6. 调自动配置工厂方法，拿到真实 DeepSeekChatModel
        DeepSeekChatAutoConfiguration autoConfig = new DeepSeekChatAutoConfiguration();
        DeepSeekChatModel chatModel = autoConfig.deepSeekChatModel(
                conn, chat,
                restClientProvider,                       // RestClient.Builder（同步请求）
                webClientProvider,                        // WebClient.Builder（流式请求）
                toolCallingManager,
                retryProvider,                            // RetryTemplate
                errorHandlerProvider,                     // ResponseErrorHandler
                observationProvider,                      // ObservationRegistry
                conventionProvider                        // ChatModelObservationConvention
        );

        // 7. 用 ChatClient 包一层，发起真实请求
        ChatClient chatClient = ChatClient.create(chatModel);
        String reply = chatClient.prompt()
                .user("用一句话介绍你自己")
                .call()
                .content();

        System.out.println("===== DeepSeek 回复 =====");
        System.out.println(reply);
        System.out.println("=========================");
    }

    /** 返回一个"空" ObjectProvider：只需实现 stream() 返回空流（Spring 6.2+ 其余方法走默认实现：getIfAvailable/getIfUnique 返回 null） */
    private static <T> ObjectProvider<T> emptyProvider()
    {
        return new ObjectProvider<T>()
        {
            @Override
            public java.util.stream.Stream<T> stream()
            {
                return java.util.stream.Stream.empty();
            }
        };
    }
}