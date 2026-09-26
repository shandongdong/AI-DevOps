package com.ruoyi.aidevops.tool.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * WeatherService 单元测试：Mock open-meteo HTTP 响应，验证查询逻辑与容错。
 *
 * <p>用 {@link MockRestServiceServer} 绑定 RestClient，不发真实网络请求——
 * 这样测试快、稳、可重复，且能精确模拟"城市查不到""API 超时""网络异常"等场景。</p>
 *
 * <p>执行引擎可单测（不依赖模型），这正是企业级分层设计的价值：WeatherService 不耦合 Spring AI，
 * 用 MockRestServiceServer 就能独立验证全部业务逻辑。</p>
 *
 * @author shandongdong
 */
class WeatherServiceTest
{
    private WeatherProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private WeatherService weatherService;

    @BeforeEach
    void setUp()
    {
        properties = new WeatherProperties();
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        // 用测试专用构造器注入绑定过 mock server 的 RestClient
        weatherService = new WeatherService(properties, restClientBuilder.build());
    }

    @Test
    @DisplayName("正常查询：杭州 → 返回 WeatherInfo（含温度和天气状况）")
    void getCurrentWeather_validCity_returnsWeatherInfo()
    {
        // 1. mock geocoding 响应：杭州 → 经纬度
        String geoResponse = """
                {"results":[{"name":"杭州","latitude":30.25,"longitude":120.17}]}
                """;
        mockServer.expect(requestTo(URI.create(
                        "https://geocoding-api.open-meteo.com/v1/search?name=%E6%9D%AD%E5%B7%9E&count=1&language=zh")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(geoResponse, MediaType.APPLICATION_JSON));

        // 2. mock forecast 响应：经纬度 → 28°C，weather_code=0（晴）
        String forecastResponse = """
                {"current":{"temperature_2m":28.0,"weather_code":0}}
                """;
        mockServer.expect(requestTo(URI.create(
                        "https://api.open-meteo.com/v1/forecast?latitude=30.25&longitude=120.17&current=temperature_2m,weather_code")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(forecastResponse, MediaType.APPLICATION_JSON));

        WeatherInfo info = weatherService.getCurrentWeather("杭州");

        assertNotNull(info, "杭州应能查到天气");
        assertEquals("杭州", info.city());
        assertEquals(28.0, info.temperature(), 0.01);
        assertEquals("晴", info.condition(), "weather_code=0 应映射为晴");
        mockServer.verify();
    }

    @Test
    @DisplayName("城市查不到：geocoding 返回空 results → 返回 null")
    void getCurrentWeather_unknownCity_returnsNull()
    {
        mockServer.expect(requestTo(URI.create(
                        "https://geocoding-api.open-meteo.com/v1/search?name=%E5%B9%BB%E5%BD%B1%E5%9F%8E%E5%B8%82&count=1&language=zh")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        WeatherInfo info = weatherService.getCurrentWeather("幻影城市");

        assertNull(info, "不存在的城市应返回 null");
        mockServer.verify();
    }

    @Test
    @DisplayName("geocoding 响应无 results 字段 → 返回 null")
    void getCurrentWeather_noResultsField_returnsNull()
    {
        mockServer.expect(requestTo(URI.create(
                        "https://geocoding-api.open-meteo.com/v1/search?name=xyz&count=1&language=zh")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        WeatherInfo info = weatherService.getCurrentWeather("xyz");

        assertNull(info, "无 results 字段应返回 null");
    }

    @Test
    @DisplayName("forecast 接口 500 错误 → 抛 RuntimeException（由上层 WeatherTools 捕获转字符串）")
    void getCurrentWeather_forecastError_throwsRuntimeException()
    {
        // geocoding 成功
        String geoResponse = """
                {"results":[{"name":"杭州","latitude":30.25,"longitude":120.17}]}
                """;
        mockServer.expect(requestTo(URI.create(
                        "https://geocoding-api.open-meteo.com/v1/search?name=%E6%9D%AD%E5%B7%9E&count=1&language=zh")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(geoResponse, MediaType.APPLICATION_JSON));

        // forecast 返回 500
        mockServer.expect(requestTo(URI.create(
                        "https://api.open-meteo.com/v1/forecast?latitude=30.25&longitude=120.17&current=temperature_2m,weather_code")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        // 引擎抛异常，由 WeatherTools 捕获转成给模型的错误字符串
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> weatherService.getCurrentWeather("杭州"));
        assertTrue(ex.getMessage().contains("天气查询失败"), "异常信息应提示天气查询失败");
    }
}
