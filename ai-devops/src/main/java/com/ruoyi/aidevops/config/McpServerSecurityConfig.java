package com.ruoyi.aidevops.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * MCP 服务端 HTTP 端点安全配置（第13篇修复 #5）。
 *
 * <p>MCP server 的 {@code POST/GET/DELETE /mcp} 端点由
 * {@code McpServerStreamableHttpWebMvcAutoConfiguration} 通过 {@code RouterFunction} 注册
 * （非 {@code @Controller} 方法），而若依的 {@code PermitAllUrlProperties} 只扫
 * {@code RequestMappingHandlerMapping} 里的 {@code @Anonymous} 注解——
 * RouterFunction 不在其中，{@code @Anonymous} 扫不到 {@code /mcp}，外部 client 连过来会被
 * Spring Security 拦截返 401。</p>
 *
 * <p>修法：在 ai-devops 模块加一个<b>独立</b>的 {@link SecurityFilterChain}，{@code @Order(0)}
 * 比 ruoyi-framework 的 {@code SecurityConfig.filterChain}（默认 LOWEST_PRECEDENCE）优先级高，
 * 只匹配 {@code /mcp/**} 并 {@code permitAll}。其他请求不匹配此 chain，回落到 ruoyi 原来的
 * 鉴权 chain——<b>不改上游 ruoyi-framework 的 SecurityConfig</b>（CLAUDE.md 约定）。</p>
 *
 * <p>⚠️ <b>安全警告</b>：{@code /mcp} 端点默认无认证，任何可达端点的 client 都能枚举并调用所有
 * 暴露的工具（getWeather/executeCommand）。验证阶段内网测可接受；生产部署必须加 Spring Security
 * 鉴权（如 Bearer token 校验）或接 <a href="https://github.com/spring-ai-community/mcp-security">mcp-security</a>，
 * 不能直接 permitAll 上生产。</p>
 *
 * <p>与 ruoyi {@code SecurityConfig.filterChain} 的并存语义：Spring Security 多 chain 时按
 * {@code @Order} 升序匹配，请求命中第一个匹配的 chain 后不再走后续 chain。本 chain {@code @Order(0)}
 * 优先级最高，{@code securityMatcher("/mcp/**")} 限定只接管 {@code /mcp/**} 请求并 {@code permitAll}；
 * 其余请求不匹配此 chain，回落到下一个 chain（ruoyi 的 LOWEST_PRECEDENCE）走 JWT 鉴权。
 * 这样既放行 MCP 端点又不影响 ruoyi 原鉴权链。</p>
 *
 * @author shandongdong
 */
@Configuration
public class McpServerSecurityConfig
{
    private static final Logger log = LoggerFactory.getLogger(McpServerSecurityConfig.class);

    /**
     * 放行 MCP server 的 {@code /mcp/**} 端点（RouterFunction 注册，{@code @Anonymous} 扫不到）。
     *
     * <p>{@code @Order(0)} 让此 chain 比 ruoyi 的 {@code SecurityConfig.filterChain}（默认
     * LOWEST_PRECEDENCE）先匹配；{@code securityMatcher("/mcp/**")} 限定只接管 MCP 端点，
     * 其余请求回落到 ruoyi 原 chain 走 JWT 鉴权。</p>
     *
     * @param httpSecurity HttpSecurity 构建器
     * @return 只放行 {@code /mcp/**} 的 SecurityFilterChain
     * @throws Exception HttpSecurity 配置异常
     */
    @Bean
    @Order(0)
    public SecurityFilterChain mcpServerSecurityFilterChain(HttpSecurity httpSecurity) throws Exception
    {
        log.info("[McpServerSecurityConfig] 注册 /mcp SecurityFilterChain, @Order(0)");
        return httpSecurity
                // securityMatcher 用 Ant 风格："/mcp/**" 匹配 /mcp/xxx 但【不匹配】/mcp 本身，
                // 故要同时列 "/mcp" 和 "/mcp/**"，让 POST /mcp（initialize/tools/list 等单端点）也能命中此 chain。
                .securityMatcher("/mcp", "/mcp/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
