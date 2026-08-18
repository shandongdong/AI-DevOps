package com.ruoyi.aidevops.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * ai-devops 模块健康检查
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/aidevops")
public class HealthController
{
    /**
     * 健康检查接口（匿名可访问，用于验证模块创建成功）
     */
    @Anonymous
    @GetMapping("/health")
    public AjaxResult health()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("module", "ai-devops");
        data.put("status", "UP");
        data.put("timestamp", System.currentTimeMillis());
        return AjaxResult.success(data);
    }
}
