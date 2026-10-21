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

import com.ruoyi.aidevops.tool.shell.ShellExecutor;
import com.ruoyi.aidevops.tool.shell.ShellProperties;
import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherProperties;
import com.ruoyi.aidevops.tool.weather.WeatherService;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

import io.micrometer.observation.ObservationRegistry;

/**
 * 第15篇 main 方法：记忆 + 工具 + 反思循环 = 真·Agent。
 *
 * <p>核心验证：同一个任务跑三遍，对比「加 Agent 能力前后」的差异：</p>
 * <ol>
 *   <li>{@code baseline()} —— 第14篇的单轮对话（带记忆、无工具）：模型只能动嘴，查不了天气也查不了系统，
 *       只能凭空编一句回答。这是「没长手」的 Agent。</li>
 *   <li>{@code innerLoop()} —— 记忆 + 工具，一次 {@code .call()}：框架的 {@code ToolCallingManager}
 *       在内部自动多步调工具（查天气→看结果→再查系统时间内存→综合答复）。这是「长手但黑盒」的 Agent。</li>
 *   <li>{@code outerLoop()} —— 记忆 + 工具 + 外层反思循环：代码用 {@code while} 显式驱动，每步可见，
 *       模型先 THINK 再 ACT 或 FINAL，带步数兜底。这是「长手又透明、能反思」的真·Agent。</li>
 * </ol>
 *
 * <p>三段共用同一个任务、同一套工具、同一种记忆链路，唯一变量是「循环驱动在哪一层」。
 * 对比意义：baseline 暴露「无手」的硬伤，innerLoop 展示 Spring AI 原生已能多步，outerLoop 展示
 * 反思循环让 Agent 可观测、可控、可兜底。</p>
 *
 * <h2>为什么这是第一个在 main 里挂工具的 demo</h2>
 * <p>前几篇 main（HelloAI/MemoryAI）只验证对话和记忆，没挂工具——工具的演示一直在 controller 层走
 * Spring 容器注入。本篇是第一次在 main 里手搓工具链路，所以把「工具怎么搭起来」也讲透：
 * {@code Properties → Service/Executor → Tools} 三件套手动 new，和 AIToolConfig 里 @Bean 装配等价，
 * 只是绕开容器看得见原理。</p>
 *
 * <p>依赖环境变量 {@code DEEPSEEK_API_KEY}。天气工具连 open-meteo（免费无 key），Shell 工具用
 * {@link ShellProperties} 默认白名单（含 date/df/free 等只读命令）。</p>
 *
 * @author shandongdong
 */
public class AgentAI
{
    /** 记忆窗口：保留最近 N 轮消息，防 token 爆。外层循环每步都写回记忆，窗口自动裁剪老消息 */
    private static final int MAX_MESSAGES = 20;

    /** 外层循环最大步数兜底：防模型陷入死循环，到了就强制停 */
    private static final int MAX_STEPS = 8;

    /** 三段对比共用的任务：需要查天气（户外决策）+ 查系统时间内存（附加信息），天然要多步 */
    private static final String TASK = "查一下杭州当前天气，如果适合户外运动，"
            + "再查一下当前系统时间，最后给我一句话总结。";

    public static void main(String[] args)
    {
        // 1. 手动构造 ChatModel（复用 HelloAI 的 DeepSeek 链路）
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        ChatModel chatModel = createDeepSeekChatModel(apiKey);

        // 2. 手动构造工具链路：Properties → Service/Executor → Tools
        //    和 AIToolConfig 里 @Bean 装配等价，这里绕开容器看原理
        WeatherTools weatherTools = new WeatherTools(new WeatherService(new WeatherProperties()));
        ShellTools shellTools = new ShellTools(new ShellExecutor(new ShellProperties()));

        // 3. baseline：带记忆、无工具（复用第14篇链路，只是不挂 tools）
        baseline(chatModel);

        System.out.println("\n\n");

        // 4. innerLoop：记忆 + 工具，一次 call 内部自动多步
        innerLoop(chatModel, weatherTools, shellTools);

        System.out.println("\n\n");

        // 5. outerLoop：记忆 + 工具 + 外层反思循环
        outerLoop(chatModel, weatherTools, shellTools);
    }

    // ==================== 第一段：baseline（无工具）====================

    /**
     * baseline：第14篇的单轮带记忆对话，但不挂工具。
     *
     * <p>模型只能动嘴——查不了天气也查不了系统时间，多半会凭空编一句「杭州今天晴，适合户外」。
     * 这正是「没长手」的硬伤：没有工具，模型对实时信息两眼一抹黑。</p>
     */
    static void baseline(ChatModel chatModel)
    {
        ChatClient client = buildClientWithMemory(chatModel, /* tools= */ null);

        System.out.println("===== 第一段 baseline：带记忆、无工具（模型只能动嘴）=====");
        System.out.println("[任务] " + TASK);
        String reply = client.prompt()
                .system("你是一个助手。")
                .user(TASK)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "agent-baseline"))
                .call()
                .content();
        System.out.println("[回复] " + reply);
        System.out.println("[对比] 模型没工具，查不了天气/时间，回答多半是编的。");
    }

    // ==================== 第二段：innerLoop（内部循环）====================

    /**
     * innerLoop：记忆 + 工具，一次 {@code .call()}。
     *
     * <p>关键：工具通过 {@code .defaultTools()} 挂到 ChatClient。{@code .call()} 内部由
     * {@code ToolCallingManager} 自动多步：模型调天气工具→拿结果→判断还需查时间→再调 date→综合答复。
     * 这一切在 {@code .call()} 内部黑盒完成，只拿到最终答案。</p>
     *
     * <p>本段证明：Spring AI 原生已支持多步工具调用，不需要外层编排。
     * 代价是中间过程不可见、不可控。</p>
     */
    static void innerLoop(ChatModel chatModel, WeatherTools weatherTools, ShellTools shellTools)
    {
        ChatClient client = buildClientWithMemory(chatModel, new Object[] { weatherTools, shellTools });

        System.out.println("===== 第二段 innerLoop：记忆 + 工具，一次 call 内部自动多步 =====");
        System.out.println("[任务] " + TASK);
        String reply = client.prompt()
                .system("你是一个助手，可以调用工具查天气和执行只读 shell 命令。"
                        + "一步步思考：先查天气，看结果决定是否查系统时间，最后综合答复。")
                .user(TASK)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "agent-inner"))
                .call()
                .content();   // 内部自动：查天气→看结果→查时间→综合
        System.out.println("[最终答复] " + reply);
        System.out.println("[对比] 一次 call 内部多步完成，中间过程黑盒不可见。");
    }

    // ==================== 第三段：outerLoop（外层反思循环）====================

    /**
     * outerLoop：记忆 + 工具 + 外层 {@code while} 反思循环。
     *
     * <p>每步显式驱动，模型输出带 THINK/ACT/FINAL 标记：
     * THINK 表示还在反思、要调工具；FINAL 表示信息够了、直接出答案。
     * 代码据此判停，{@code MAX_STEPS} 兜底防死循环。</p>
     *
     * <p>关键设计：每步用同一个 conversationId，记忆 advisor 把每轮 user/assistant 写回。
     * 多步累积下对话历史会膨胀，靠窗口策略（{@value MAX_MESSAGES} 条）自动裁剪老消息——
     * 这正是第14篇记忆机制在外层循环里的用武之地。</p>
     */
    static void outerLoop(ChatModel chatModel, WeatherTools weatherTools, ShellTools shellTools)
    {
        ChatClient client = buildClientWithMemory(chatModel, new Object[] { weatherTools, shellTools });
        String convId = "agent-outer";

        System.out.println("===== 第三段 outerLoop：记忆 + 工具 + 外层反思循环 =====");
        System.out.println("[任务] " + TASK);

        int step = 0;
        String finalAnswer = null;
        while (step < MAX_STEPS)
        {
            step++;
            System.out.println("\n----- 第 " + step + " 步 / 最多 " + MAX_STEPS + " 步 -----");

            // 每步把「当前第 N 步、反思已掌握信息、够了就 FINAL」喂给模型，引导它显式自检。
            // 注意：框架内部 ToolCallingManager 仍可能在一步内连调多个工具（模型一次返回多个 tool call），
            // 外层 while 的「步」是反思粒度，不等同于工具调用粒度——这是两层循环的区别。
            String turnPrompt = "当前是第 " + step + " 步（最多 " + MAX_STEPS + " 步）。\n"
                    + "任务：" + TASK + "\n"
                    + "按以下格式回答（第一行必须是标记）：\n"
                    + "  THINK —— 反思已掌握信息是否足够，说明还缺什么、打算调哪个工具\n"
                    + "  FINAL —— 信息已足够，直接给出最终答案（不再调工具）\n"
                    + "先反思，再决定下一步。";

            String output = client.prompt()
                    .system("你是一个会反思的 Agent。每一步【最多只调用一个工具】，"
                            + "调用后先反思已掌握信息是否足够，再决定继续调工具（THINK）"
                            + "还是给出最终答案（FINAL）。"
                            + "可以调用查天气和执行只读 shell 命令的工具。")
                    .user(turnPrompt)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, convId))
                    .call()
                    .content();

            System.out.println("[模型输出]\n" + output);

            if (output != null && output.contains("FINAL"))
            {
                finalAnswer = output;
                System.out.println(">>> 模型判定信息已足够，停止循环");
                break;
            }
            System.out.println(">>> 模型还在调工具/思考中，进入下一步");
        }

        System.out.println("\n===== 外层循环结束 =====");
        System.out.println("[最终结果] " + (finalAnswer != null ? finalAnswer : "(达到最大步数未完成)"));
        System.out.println("[对比] 每步可见、可反思、可兜底，但代码复杂度高于 innerLoop。");
    }

    // ==================== 公共构造方法 ====================

    /**
     * 构造带记忆的 ChatClient，可选挂工具。
     *
     * <p>tools 传 null 表示不挂工具（baseline 用）；传 Object 数组表示挂工具（innerLoop/outerLoop 用）。
     * 关键：用 {@link ChatClient#builder(ChatModel)} 而非 {@code create()}——builder 才能挂 advisor。</p>
     */
    private static ChatClient buildClientWithMemory(ChatModel chatModel, Object[] tools)
    {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        ChatClient.Builder builder = ChatClient.builder(chatModel).defaultAdvisors(memoryAdvisor);
        if (tools != null && tools.length > 0)
        {
            builder.defaultTools(tools);
        }
        return builder.build();
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
