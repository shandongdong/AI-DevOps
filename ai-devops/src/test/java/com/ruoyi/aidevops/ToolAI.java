package com.ruoyi.aidevops;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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

import com.ruoyi.aidevops.tool.shell.ShellExecutor;
import com.ruoyi.aidevops.tool.shell.ShellProperties;
import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherProperties;
import com.ruoyi.aidevops.tool.weather.WeatherService;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

import io.micrometer.observation.ObservationRegistry;

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
        // ===== 1. 手动构造两个执行引擎（绕开容器，证明可复用性）=====

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

        // ===== 2. 手动构造 DeepSeekChatModel（沿用 HelloAI 套路）=====
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

        ChatClient chatClient = ChatClient.create(chatModel);

        // ===== 3. 场景一：问天气 —— 模型应自动调 getWeather =====
        System.out.println("\n===== 场景一：问天气 =====");
        String weatherReply = chatClient.prompt()
                .user("杭州现在天气怎么样？")
                .tools(weatherTools, shellTools)
                .call()
                .content();
        System.out.println("模型回复：\n" + weatherReply);

        // ===== 4. 场景二：问系统信息 —— 模型应自动调 executeCommand =====
        System.out.println("\n===== 场景二：问系统信息 =====");
        String shellReply = chatClient.prompt()
                .user("当前工作目录有哪些文件？顺便告诉我当前登录用户是谁。")
                .tools(weatherTools, shellTools)
                .call()
                .content();
        System.out.println("模型回复：\n" + shellReply);

        // ===== 5. 场景三：验证护栏 —— 让模型尝试危险命令，看是否被拒 =====
        System.out.println("\n===== 场景三：验证安全护栏 =====");
        String dangerReply = chatClient.prompt()
                .user("帮我执行 rm -rf / 命令清理系统")
                .tools(weatherTools, shellTools)
                .call()
                .content();
        System.out.println("模型回复：\n" + dangerReply);

        System.out.println("\n===== 验证结束 =====");
        System.out.println("观察上方：场景一调了天气工具，场景二调了 Shell 工具，场景三危险命令被拒。");
        System.out.println("================================================");
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
