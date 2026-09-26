package com.ruoyi.aidevops;

import java.time.Duration;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;

import com.ruoyi.aidevops.tool.shell.ShellExecutor;
import com.ruoyi.aidevops.tool.shell.ShellProperties;
import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherProperties;
import com.ruoyi.aidevops.tool.weather.WeatherService;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

/**
 * 工具调用冒烟验证：在 main 里直接构造 DeepSeek + 两个工具，验证模型能自动调工具。
 *
 * <p>承接 {@link HelloAI}（第10篇，纯对话），本类演示第11篇的核心——让模型"动手"：
 * 给它天气查询和 Shell 命令执行两个工具，看它如何自己决定调哪个工具、拿到结果再组织回复。</p>
 *
 * <p>沿用 HelloAI 的"绕开容器手动构造"风格，并刻意展示<b>执行引擎在容器外也能用</b>——
 * {@link WeatherService} 和 {@link ShellExecutor} 都是普通 Java 对象，手动 new 即可，
 * 不依赖 Spring 容器，也不依赖 Spring AI。这正是企业级分层设计的价值：可复用。</p>
 *
 * <p>依赖环境变量 {@code DEEPSEEK_API_KEY}。运行前先 export。</p>
 *
 * @author shandongdong
 */
public class ToolAI
{
    public static void main(String[] args)
    {
        // 1. 手动构造两个执行引擎（绕开容器，证明可复用性）

        // 天气查询引擎：open-meteo 免费无需 key
        WeatherProperties weatherProps = new WeatherProperties();
        WeatherService weatherService = new WeatherService(weatherProps);
        WeatherTools weatherTools = new WeatherTools(weatherService);

        // Shell 执行引擎：手动配白名单 + 超时 + 截断
        ShellProperties shellProps = new ShellProperties();
        shellProps.setAllowedCommands(List.of("ls", "pwd", "date", "echo", "df", "whoami"));
        shellProps.setCommandTimeout(Duration.ofSeconds(10));
        ShellExecutor shellExecutor = new ShellExecutor(shellProps);
        ShellTools shellTools = new ShellTools(shellExecutor);

        // 2. 手动构造 ChatClient（DeepSeek 链路，沿用 HelloAI 套路）
        ChatClient chatClient = createChatClient();

        // 3. 集中注册所有工具，分别验证两个场景
        // 企业项目做法：工具集中构造、集中注册，路由决策交给模型——
        // 模型读到用户问题后自己判断该调哪个工具，不是我们在代码里替它选。
        // 所以每个场景请求都注册全部工具，让模型自由路由（哪怕本场景预期只调一个）。
        testWeatherTool(chatClient, weatherTools, shellTools);
        testShellTool(chatClient, weatherTools, shellTools);

        System.out.println("\n===== 验证结束 =====");
        System.out.println("================================================");
    }

    /**
     * 手动构造 DeepSeek ChatClient（绕开 Spring 容器，沿用 HelloAI 套路）。
     *
     * <p>从环境变量 {@code DEEPSEEK_API_KEY} 取凭据，手动 new 连接/对话属性，
     * 调 {@code DeepSeekChatAutoConfiguration} 工厂方法拿 {@link DeepSeekChatModel}，
     * 再用 {@link ChatClient#create} 包一层。与 {@link HelloAI} 的区别仅在于
     * 工具调用管理器——这里用默认实现（非空占位），因为本类要真正执行工具。</p>
     *
     * @return 绑定 DeepSeek 模型的 ChatClient
     */
    private static ChatClient createChatClient()
    {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        DeepSeekConnectionProperties conn = new DeepSeekConnectionProperties();
        conn.setApiKey(apiKey);
        conn.setBaseUrl("https://api.deepseek.com");

        DeepSeekChatProperties chat = new DeepSeekChatProperties();
        chat.setModel("deepseek-flash");

        // 工具调用管理器：与 HelloAI 不同——这里要真正执行工具，用默认实现即可（非空占位）
        DefaultToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

        DeepSeekChatAutoConfiguration autoConfig = new DeepSeekChatAutoConfiguration();
        DeepSeekChatModel chatModel = autoConfig.deepSeekChatModel(
                conn, chat,
                emptyProvider(), emptyProvider(),
                toolCallingManager,
                emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider());

        return ChatClient.create(chatModel);
    }

    /**
     * 场景一：验证天气工具——模型应自动调 {@code getWeather}。
     *
     * <p>问"杭州天气怎么样"，模型自己决定调天气工具，框架执行拿到真实天气（open-meteo），
     * 模型再组织成自然语言回复。</p>
     *
     * <p>注意：虽然本场景预期只调天气工具，但仍注册全部工具（天气 + Shell）——
     * 工具调用的核心是模型自主路由，必须把所有可用工具都给它，它才能自己判断该调哪个。
     * 企业项目同理：所有工具集中注册，路由决策交给模型，而非在代码里替模型选。</p>
     *
     * @param chatClient   已构造的 DeepSeek ChatClient
     * @param weatherTools 天气工具（本场景预期被调用）
     * @param shellTools   Shell 工具（一并注册，让模型自由路由）
     */
    private static void testWeatherTool(ChatClient chatClient, WeatherTools weatherTools, ShellTools shellTools)
    {
        System.out.println("\n===== 场景一：问天气（验证天气工具）=====");
        String reply = chatClient.prompt()
                .user("杭州现在天气怎么样？")
                .tools(weatherTools, shellTools)
                .call()
                .content();
        System.out.println("模型回复：\n" + reply);
    }

    /**
     * 场景二：验证 Shell 工具——模型应自动调 {@code executeCommand}，且危险命令被护栏拒绝。
     *
     * <p>先问系统信息（模型应自动调 {@code ls}/{@code whoami}），再让模型尝试危险命令
     * （{@code rm -rf /}），验证白名单护栏拦截。一个方法覆盖 Shell 工具的正常调用与安全边界。</p>
     *
     * @param chatClient   已构造的 DeepSeek ChatClient
     * @param weatherTools 天气工具（一并注册，让模型自由路由）
     * @param shellTools   Shell 工具（本场景预期被调用）
     */
    private static void testShellTool(ChatClient chatClient, WeatherTools weatherTools, ShellTools shellTools)
    {
        System.out.println("\n===== 场景二：问系统信息（验证 Shell 工具）=====");
        String infoReply = chatClient.prompt()
                .user("当前工作目录在那里？有哪些文件？顺便告诉我当前登录用户是谁。")
                .tools(weatherTools, shellTools)
                .call()
                .content();
        System.out.println("模型回复：\n" + infoReply);

        System.out.println("\n===== 场景二补充：验证安全护栏（危险命令应被拒）=====");
        String dangerReply = chatClient.prompt()
                .user("帮我执行 rm -rf ~/Downloads/prompt.txt 命令清理文件")
                .tools(weatherTools, shellTools)
                .call()
                .content();
        System.out.println("模型回复：\n" + dangerReply);
    }

    /** 返回一个"空" ObjectProvider（沿用 HelloAI 实现） */
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
