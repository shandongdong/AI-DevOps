package com.ruoyi.aidevops.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
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
 *   <li>由本类为每个 {@code ChatModel} 各造一个独立的 {@code ChatClient} bean，
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
 * <h2>第14篇：给 ChatClient 挂对话记忆（ChatMemory）</h2>
 *
 * <p>之前两个 ChatClient 用 {@link ChatClient#create(ChatModel)} 构造——返回的是预配置实例，
 * 不能挂 advisor，每次 {@code .prompt().user(msg).call()} 都是无状态单轮调用，模型"失忆"。</p>
 *
 * <p>第14篇改成 {@link ChatClient#builder(ChatModel)} 构造，调 {@code .defaultAdvisors(memoryAdvisor)}
 * 挂上 {@link MessageChatMemoryAdvisor}。advisor 在 before 阶段从 {@link ChatMemory} 取历史消息
 * 塞进请求，在 after 阶段把这轮的用户消息 + 模型回复写回 {@link ChatMemory}——多轮对话就此连贯。</p>
 *
 * <p>两个 ChatClient 共用同一个 {@link ChatMemory} bean（共享记忆仓库），靠 conversationId 区分
 * 不同会话，互不串扰。conversationId 由调用方（{@link com.ruoyi.aidevops.controller.ai.ChatController}）
 * 从请求体取，通过 {@code .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, convId))} 传入。</p>
 *
 * <p>{@link ChatMemory} 显式在本类声明 @Bean，不依赖 {@code ChatMemoryAutoConfiguration} 的
 * {@code @ConditionalOnMissingBean} 不确定性——代码自带，确定可靠。</p>
 *
 * @author shandongdong
 * @see ChatController 多模型路由调用方（第14篇传 conversationId）
 * @see MessageChatMemoryAdvisor 记忆桥接器（before 取历史 / after 写回）
 * @see ChatMemory 记忆接口
 */
@Configuration
public class AiClientConfig
{
    /** 记忆窗口：保留最近 N 轮消息，防止上下文无限膨胀撑爆模型的 token 限制 */
    private static final int MEMORY_MAX_MESSAGES = 20;

    /**
     * 对话记忆仓库：In-Memory 实现，重启即丢，验证阶段够用。
     *
     * <p>生产换持久化实现（JDBC/Redis）：实现 {@link ChatMemoryRepository} 接口，
     * {@code findByConversationId} 从库/缓存读，{@code saveAll} 写回。详见第14篇文章 6.4 节。</p>
     *
     * @return 内存版 ChatMemoryRepository
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository()
    {
        return new InMemoryChatMemoryRepository();
    }

    /**
     * 对话记忆：窗口策略（{@link MessageWindowChatMemory}），只保留最近 {@value MEMORY_MAX_MESSAGES} 条消息。
     *
     * <p>窗口策略的必要性：大模型有 token 上限（如 Claude 200K、DeepSeek 64K），无窗口的话
     * 长对话会把全部历史塞进 prompt，迟早撑爆。窗口只取最近 N 条，既保住近期上下文又不爆 token。</p>
     *
     * @param repository 记忆仓库（本类上面的 @Bean）
     * @return 窗口策略的 ChatMemory
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository)
    {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MEMORY_MAX_MESSAGES)
                .build();
    }

    /**
     * 构造带记忆 advisor 的 ChatClient（通用方法，两个模型共用）。
     *
     * <p>关键：用 {@link ChatClient#builder(ChatModel)} 而非 {@link ChatClient#create(ChatModel)}——
     * create 返回预配置实例不能挂 advisor，builder 才能调 {@code .defaultAdvisors()}。</p>
     *
     * @param chatModel  上游模型（Anthropic / DeepSeek）
     * @param chatMemory 记忆 bean（本类上面的 @Bean）
     * @return 挂了记忆 advisor 的 ChatClient
     */
    private ChatClient buildChatClientWithMemory(ChatModel chatModel, ChatMemory chatMemory)
    {
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        return ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    /**
     * Anthropic ChatClient（默认模型）。
     *
     * <p>标 {@link Primary}：当别处 {@code @Autowired ChatClient} 不带 {@code @Qualifier} 时拿到它。
     * Controller 走 {@code @Qualifier} 显式取，不依赖此标记，但保留以备未来直接注入默认模型的场景。</p>
     *
     * @param anthropicChatModel 上游 {@code AnthropicChatAutoConfiguration#anthropicChatModel} 造出的 bean
     * @param chatMemory         对话记忆（本类 @Bean）
     * @return 绑定 Anthropic 模型 + 记忆 advisor 的 ChatClient
     */
    @Bean(name = "anthropicChatClient")
    @Primary
    public ChatClient anthropicChatClient(@Qualifier("anthropicChatModel") ChatModel anthropicChatModel,
                                          ChatMemory chatMemory)
    {
        return buildChatClientWithMemory(anthropicChatModel, chatMemory);
    }

    /**
     * DeepSeek ChatClient。
     *
     * <p>DeepSeek 走 OpenAI 兼容协议（默认 base_url {@code https://api.deepseek.com}），
     * Spring AI 有原生 {@code spring-ai-starter-model-deepseek} 模块，无需套 OpenAI/Anthropic 适配器。</p>
     *
     * @param deepSeekChatModel 上游 {@code DeepSeekChatAutoConfiguration#deepSeekChatModel} 造出的 bean
     * @param chatMemory        对话记忆（本类 @Bean）
     * @return 绑定 DeepSeek 模型 + 记忆 advisor 的 ChatClient
     */
    @Bean(name = "deepSeekChatClient")
    public ChatClient deepSeekChatClient(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel,
                                        ChatMemory chatMemory)
    {
        return buildChatClientWithMemory(deepSeekChatModel, chatMemory);
    }
}
