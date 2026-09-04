package com.ruoyi.aidevops.domain.calc;

/**
 * 计算请求入参（RESTful：compute 用 POST + JSON body，不用 GET 路径参数）
 *
 * @author shandongdong
 *
 * @param operator 运算符（+ - * /）
 * @param first    第一个数
 * @param second   第二个数
 */
public record CalcComputeRequest(String operator, Double first, Double second)
{
}
