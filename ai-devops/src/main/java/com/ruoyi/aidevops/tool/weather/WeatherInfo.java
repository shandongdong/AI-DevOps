package com.ruoyi.aidevops.tool.weather;

/**
 * 天气查询结果（结构化，可被 AI 工具和普通 REST 接口复用）。
 *
 * <p>用 record 而非可变 POJO：天气是只读快照，无状态变更需求，record 语义最贴切，
 * 且自带 equals/hashCode/toString，便于断言与日志。</p>
 *
 * @author shandongdong
 *
 * @param city        城市名称（open-meteo geocoding 返回的中文/英文名）
 * @param temperature 当前温度（摄氏度）
 * @param condition   天气状况（WMO weather_code 映射后的中文描述，如"晴""多云""小雨"）
 * @param description 补充说明（如"体感较冷"，目前与 condition 一致，预留扩展）
 */
public record WeatherInfo(String city, double temperature, String condition, String description)
{
}
