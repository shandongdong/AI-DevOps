package com.ruoyi.aidevops.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多大模型 ChatClient 配置
 *
 * <p>背景：Spring AI 2.0.1 的 {@code ChatClientAutoConfiguration} 是单模型设计——它的
 * {@code chatClientBuilder(..., ChatModel chatModel, ...)} 直接注入单个 {@link ChatModel}。
 * 当容器里同时存在多个 {@code ChatModel} bean（本项目接了 Anthropic + DeepSeek）时，
 * Spring 无法决定选哪个，启动报 {@code NoUniqueBeanDefinitionException}。</p>
 *
 * <p>解决思路（方案 B）：</p>
 * <ol>
 *   <li>在 {@code application-*.yml} 设 {@code spring.ai.chat.client.enabled=false}，
 *       禁用 {@code ChatClientAutoConfiguration}（其类级 {@code @ConditionalOnProperty}
 *       源码第 70-71 行确认该开关可干净关闭整个自动配置类）。</li>
 *   <li>由本类为每个 {@code ChatModel} 各造一个独立的 {@link ChatClient} bean，
 *       用 {@code @Qualifier} 显式指定上游 {@code ChatModel} bean，Anthropic 那个标
 *       {@link Primary} 作为默认。</li>
 * </ol>
 *
 * <p>这样行为可预测，不依赖 {@code @ConditionalOnMissingBean} 的类型匹配微妙性
 * （方案 A 依赖 {@code ChatClient.Builder} vs {@code ChatClient} 类型匹配，有坑）。</p>
 *
 * <p>新增模型厂商时：在此追加一个 {@code @Bean} 方法 + 在 {@code AiModelRouter#route} 的 switch
 * 里加一个 case（三个 AI 接口共用该路由，只改一处）即可，无需改自动配置或抽象出
 * Map/策略模式（YAGNI，两个模型时字段注入更直接）。</p>
 *
 * @author shandongdong
 * @see ChatController 多模型路由调用方
 */
@Configuration
public class AiClientConfig
{
    /**
     * Anthropic ChatClient（默认模型）。
     *
     * <p>标 {@link Primary}：当别处 {@code @Autowired ChatClient} 不带 {@code @Qualifier} 时拿到它。
     * Controller 走 {@code @Qualifier} 显式取，不依赖此标记，但保留以备未来直接注入默认模型的场景。</p>
     *
     * @param anthropicChatModel 上游 {@code AnthropicChatAutoConfiguration#anthropicChatModel} 造出的 bean
     * @return 绑定 Anthropic 模型的 ChatClient
     */
    @Bean(name = "anthropicChatClient")
    @Primary
    public ChatClient anthropicChatClient(@Qualifier("anthropicChatModel") ChatModel anthropicChatModel)
    {
        return ChatClient.create(anthropicChatModel);
    }

    /**
     * DeepSeek ChatClient。
     *
     * <p>DeepSeek 走 OpenAI 兼容协议（默认 base_url {@code https://api.deepseek.com}），
     * Spring AI 有原生 {@code spring-ai-starter-model-deepseek} 模块，无需套 OpenAI/Anthropic 适配器。</p>
     *
     * @param deepSeekChatModel 上游 {@code DeepSeekChatAutoConfiguration#deepSeekChatModel} 造出的 bean
     * @return 绑定 DeepSeek 模型的 ChatClient
     */
    @Bean(name = "deepSeekChatClient")
    public ChatClient deepSeekChatClient(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel)
    {
        return ChatClient.create(deepSeekChatModel);
    }
}
