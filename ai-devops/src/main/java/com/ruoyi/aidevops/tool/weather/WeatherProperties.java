package com.ruoyi.aidevops.tool.weather;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 天气工具配置（open-meteo API 地址 + 超时）。
 *
 * <p>配置前缀 {@code ai-devops.tool.weather}，对应 application.yml：</p>
 * <pre>
 * ai-devops:
 *   tool:
 *     weather:
 *       geocoding-api-url: https://geocoding-api.open-meteo.com/v1/search
 *       forecast-api-url: https://api.open-meteo.com/v1/forecast
 *       timeout: 10s
 * </pre>
 *
 * <p>用 {@code @ConfigurationProperties} 外置，避免在代码里硬编码 URL——
 * 环境差异（内网代理/自建镜像）只改配置不改代码，符合企业级配置外置原则。</p>
 *
 * @author shandongdong
 */
@ConfigurationProperties(prefix = "ai-devops.tool.weather")
public class WeatherProperties
{
    /** 地理编码 API（城市名 → 经纬度），open-meteo 免费无需 key */
    private String geocodingApiUrl = "https://geocoding-api.open-meteo.com/v1/search";

    /** 天气预报 API（经纬度 → 天气），open-meteo 免费无需 key */
    private String forecastApiUrl = "https://api.open-meteo.com/v1/forecast";

    /** HTTP 连接与读取超时，默认 10 秒（外部 API 不可控，必须设上限保护服务线程） */
    private Duration timeout = Duration.ofSeconds(10);

    public String getGeocodingApiUrl()
    {
        return geocodingApiUrl;
    }

    public void setGeocodingApiUrl(String geocodingApiUrl)
    {
        this.geocodingApiUrl = geocodingApiUrl;
    }

    public String getForecastApiUrl()
    {
        return forecastApiUrl;
    }

    public void setForecastApiUrl(String forecastApiUrl)
    {
        this.forecastApiUrl = forecastApiUrl;
    }

    public Duration getTimeout()
    {
        return timeout;
    }

    public void setTimeout(Duration timeout)
    {
        this.timeout = timeout;
    }
}
