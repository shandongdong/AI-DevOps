package com.ruoyi.aidevops.tool.weather;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * 天气查询执行引擎（不耦合 Spring AI，可被 AI 工具和普通 REST 接口复用）。
 *
 * <p>调用 open-meteo（开源免费、无需 key、无需注册）两步走：</p>
 * <ol>
 *   <li>geocoding：城市名 → 经纬度</li>
 *   <li>forecast：经纬度 → 当前温度 + WMO weather_code</li>
 * </ol>
 *
 * <p>设计要点（企业级，非玩具）：</p>
 * <ul>
 *   <li><b>执行引擎与 AI 适配层分离</b>：本类只负责"查天气"这件业务，不感知 {@code @Tool} 注解，
 *       任何调用方（HTTP 接口、定时任务、AI 工具）都能复用。</li>
 *   <li><b>容错</b>：城市查不到返回 {@code null}（让调用方决定怎么跟用户/模型说），网络异常抛出，
 *       由上层 {@code WeatherTools} 捕获转成给模型的错误字符串——不让 stack trace 污染模型上下文。</li>
 *   <li><b>超时保护</b>：RestClient 带 connect/read timeout，外部 API 卡住不会拖垮服务线程。</li>
 *   <li><b>WMO weather_code 映射</b>：open-meteo 返回的是数字码（0=晴，61=小雨…），本类映射成中文，
 *       模型和用户都能直接读懂。</li>
 * </ul>
 *
 * @author shandongdong
 */
public class WeatherService
{
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final RestClient restClient;
    private final String geocodingApiUrl;
    private final String forecastApiUrl;

    /**
     * @param properties 天气配置（API 地址 + 超时）
     */
    public WeatherService(WeatherProperties properties)
    {
        this.geocodingApiUrl = properties.getGeocodingApiUrl();
        this.forecastApiUrl = properties.getForecastApiUrl();
        Duration timeout = properties.getTimeout();
        this.restClient = RestClient.builder()
                .requestFactory(requestFactoryWithTimeout(timeout))
                .build();
    }

    /**
     * 测试专用构造器：注入自定义 RestClient（配合 MockRestServiceServer 绑定）。
     *
     * <p>package-private：仅同包测试可访问，不污染公共 API。生产构造仍走
     * {@link #WeatherService(WeatherProperties)}，自建带超时的 RestClient。</p>
     *
     * @param properties      天气配置（API 地址）
     * @param restClient      测试注入的 RestClient（已绑定 MockRestServiceServer）
     */
    WeatherService(WeatherProperties properties, RestClient restClient)
    {
        this.geocodingApiUrl = properties.getGeocodingApiUrl();
        this.forecastApiUrl = properties.getForecastApiUrl();
        this.restClient = restClient;
    }

    /**
     * 查询指定城市的当前天气。
     *
     * @param city 城市名称（中英文均可，如"杭州""Beijing"）
     * @return 天气信息；城市查不到返回 {@code null}；网络/解析异常抛出，由调用方处理
     */
    @SuppressWarnings("unchecked")
    public WeatherInfo getCurrentWeather(String city)
    {
        // 1. geocoding：城市名 → 经纬度（scheme/host/path 从配置 URL 解析，http 内网代理同样支持）
        Map<String, Object> geoResponse;
        try
        {
            URI geoBase = URI.create(geocodingApiUrl);
            geoResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme(geoBase.getScheme())
                            .host(geoBase.getHost())
                            .path(geoBase.getPath() == null ? "" : geoBase.getPath())
                            .queryParam("name", city)
                            .queryParam("count", 1)
                            .queryParam("language", "zh")
                            .build())
                    .retrieve()
                    .body(Map.class);
        }
        catch (Exception e)
        {
            log.warn("天气 geocoding 请求失败，city={}", city, e);
            throw new RuntimeException("城市定位失败：" + e.getMessage(), e);
        }

        List<Map<String, Object>> results = geoResponse == null ? null
                : (List<Map<String, Object>>) geoResponse.get("results");
        if (results == null || results.isEmpty())
        {
            log.info("城市未找到：{}", city);
            return null;
        }

        Map<String, Object> location = results.get(0);
        // 防御：open-meteo 正常返回字段齐全，但缺字段时不应 NPE（缺经纬度无法查天气）
        Number latVal = (Number) location.get("latitude");
        Number lonVal = (Number) location.get("longitude");
        if (latVal == null || lonVal == null)
        {
            log.warn("geocoding 响应缺经纬度字段，city={}, location={}", city, location);
            return null;
        }
        double latitude = latVal.doubleValue();
        double longitude = lonVal.doubleValue();
        String resolvedCity = (String) location.getOrDefault("name", city);

        // 2. forecast：经纬度 → 当前天气
        Map<String, Object> forecastResponse;
        try
        {
            URI forecastBase = URI.create(forecastApiUrl);
            forecastResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme(forecastBase.getScheme())
                            .host(forecastBase.getHost())
                            .path(forecastBase.getPath() == null ? "" : forecastBase.getPath())
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,weather_code")
                            .build())
                    .retrieve()
                    .body(Map.class);
        }
        catch (Exception e)
        {
            log.warn("天气 forecast 请求失败，lat={},lon={}", latitude, longitude, e);
            throw new RuntimeException("天气查询失败：" + e.getMessage(), e);
        }

        Map<String, Object> current = forecastResponse == null ? null
                : (Map<String, Object>) forecastResponse.get("current");
        if (current == null)
        {
            return null;
        }

        // 防御：forecast 响应缺 temperature_2m/weather_code 时不 NPE，返回 null
        Number tempVal = (Number) current.get("temperature_2m");
        Number codeVal = (Number) current.get("weather_code");
        if (tempVal == null || codeVal == null)
        {
            log.warn("forecast 响应缺温度或天气码字段，current={}", current);
            return null;
        }
        double temperature = tempVal.doubleValue();
        int weatherCode = codeVal.intValue();
        String condition = mapWeatherCode(weatherCode);

        log.info("天气查询成功 city={} temp={}°C code={} ({})", resolvedCity, temperature, weatherCode, condition);
        return new WeatherInfo(resolvedCity, temperature, condition, condition);
    }

    /**
     * 构造带超时的 ClientHttpRequestFactory。
     *
     * <p>Spring 7（spring-web 7.x）仍保留 {@link org.springframework.http.client.SimpleClientHttpRequestFactory}，
     * 其 {@code setConnectTimeout(Duration)} / {@code setReadTimeout(Duration)} 可直接设超时。
     * 外部 API 不可控，必须设上限保护服务线程，避免 open-meteo 卡住拖垮整个应用。</p>
     */
    private static org.springframework.http.client.ClientHttpRequestFactory requestFactoryWithTimeout(Duration timeout)
    {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    /**
     * WMO 天气码 → 中文描述（open-meteo 官方文档标准码表）。
     * <p>0 晴、1-3 云量渐增、45-48 雾、51-67 雨、71-77 雪、80-82 阵雨、95-99 雷暴。</p>
     */
    private static String mapWeatherCode(int code)
    {
        return switch (code)
        {
            case 0 -> "晴";
            case 1 -> "多云";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61 -> "小雨";
            case 63 -> "中雨";
            case 65 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71 -> "小雪";
            case 73 -> "中雪";
            case 75 -> "大雪";
            case 77 -> "霰";
            case 80, 81 -> "阵雨";
            case 82 -> "强阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "未知天气（代码 " + code + "）";
        };
    }
}
