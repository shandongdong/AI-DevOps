package com.ruoyi.aidevops.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ruoyi.aidevops.tool.shell.ShellExecutor;
import com.ruoyi.aidevops.tool.shell.ShellProperties;
import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherProperties;
import com.ruoyi.aidevops.tool.weather.WeatherService;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

/**
 * AI 工具配置：注册天气查询和 Shell 命令执行两套工具的执行引擎与适配层 bean。
 *
 * <p>分层装配（对应企业级分层设计）：</p>
 * <ol>
 *   <li>{@link WeatherProperties} / {@link ShellProperties}：配置（@ConfigurationProperties 外置）。</li>
 *   <li>{@link WeatherService} / {@link ShellExecutor}：执行引擎（真正逻辑，不耦合 Spring AI）。</li>
 *   <li>{@link WeatherTools} / {@link ShellTools}：AI 适配层（@Tool 薄适配器，注入给 ChatClient）。</li>
 * </ol>
 *
 * <p>{@code @EnableConfigurationProperties} 把带 {@code @ConfigurationProperties} 的配置类注册成 bean，
 * 此后 Service/Tools bean 通过构造注入拿到配置。</p>
 *
 * @author shandongdong
 * @see com.ruoyi.aidevops.controller.ai.ToolController 工具调用入口
 */
@Configuration
@EnableConfigurationProperties({ WeatherProperties.class, ShellProperties.class })
public class ToolConfig
{
    /** 天气查询执行引擎 */
    @Bean
    public WeatherService weatherService(WeatherProperties weatherProperties)
    {
        return new WeatherService(weatherProperties);
    }

    /** 天气查询 AI 适配层 */
    @Bean
    public WeatherTools weatherTools(WeatherService weatherService)
    {
        return new WeatherTools(weatherService);
    }

    /** Shell 命令执行引擎（含五重护栏） */
    @Bean
    public ShellExecutor shellExecutor(ShellProperties shellProperties)
    {
        return new ShellExecutor(shellProperties);
    }

    /** Shell 命令执行 AI 适配层 */
    @Bean
    public ShellTools shellTools(ShellExecutor shellExecutor)
    {
        return new ShellTools(shellExecutor);
    }
}
