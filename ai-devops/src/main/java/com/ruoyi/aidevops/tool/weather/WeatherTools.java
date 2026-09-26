package com.ruoyi.aidevops.tool.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 天气查询工具（AI 适配层）。
 *
 * <p>这是 Spring AI 工具调用的"薄适配器"——用 {@link Tool @Tool} 标注方法供大模型调用，
 * 但<b>不写业务逻辑</b>，只委托给执行引擎 {@link WeatherService}。</p>
 *
 * <p>为什么要分两层（Service + Tools）而不是把逻辑直接塞进 @Tool 方法？</p>
 * <ul>
 *   <li><b>可复用</b>：{@code WeatherService} 不依赖 Spring AI，普通 REST 接口、定时任务都能直接调，
 *       不被"AI 工具"这层绑死。</li>
 *   <li><b>可测试</b>：Service 可独立单测（Mock RestClient），工具层依赖真实模型不测。</li>
 *   <li><b>容错边界</b>：模型不该看到 Java stack trace。本类 catch 所有异常，转成给模型的可读字符串，
 *       让模型知道"工具失败了、为什么失败"，从而能换思路或如实告知用户。</li>
 * </ul>
 *
 * <p>同类参考 {@code com.ruoyi.aidevops.tool.shell.ShellTools}。</p>
 *
 * @author shandongdong
 * @see WeatherService 真正的天气查询执行引擎
 */
public class WeatherTools
{
    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);

    private final WeatherService weatherService;

    public WeatherTools(WeatherService weatherService)
    {
        this.weatherService = weatherService;
    }

    /**
     * 查询指定城市的当前天气。
     *
     * <p>方法签名（参数类型 + {@code @ToolParam} 描述）会被 Spring AI 转成 JSON Schema 发给模型，
     * 模型据此决定：何时调这个工具、传什么参数。描述要写清"什么场景调"和"参数含义"，
     * 模型才能准确路由。</p>
     *
     * @param city 城市名称，如 杭州、北京、上海
     * @return 天气描述字符串（如"杭州：28.0°C，晴"）；城市查不到或查询失败返回可读错误
     */
    @Tool(description = "查询指定城市的当前天气（温度和天气状况）。当用户询问某地天气时调用。")
    public String getWeather(@ToolParam(description = "城市名称，如 杭州、北京、上海") String city)
    {
        try
        {
            WeatherInfo info = weatherService.getCurrentWeather(city);
            if (info == null)
            {
                return "未找到城市：" + city + "，请确认城市名称";
            }
            return info.city() + "：" + info.temperature() + "°C，" + info.condition();
        }
        catch (Exception e)
        {
            // 不抛异常给模型，返回可读错误——模型据此决定换思路或如实告知用户
            log.warn("天气工具调用失败 city={}", city, e);
            return "天气查询失败：" + e.getMessage();
        }
    }
}
