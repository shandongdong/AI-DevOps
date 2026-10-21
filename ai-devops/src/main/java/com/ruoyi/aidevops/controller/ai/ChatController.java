package com.ruoyi.aidevops.controller.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherTools;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * ai-devops AI 对话接口（多模型路由 + 对话记忆）
 *
 * <p>支持在同一接口下按 {@code model} 参数切换不同大模型厂商，默认走 Anthropic。
 * 各模型的 {@link ChatClient} bean 由 {@link com.ruoyi.aidevops.config.AiClientConfig} 统一管理，
 * 路由逻辑由 {@link AiModelRouter} 统一处理（与 AIToolController/McpController 共用）。</p>
 *
 * <p>当前支持：</p>
 * <ul>
 *   <li>{@code anthropic}（默认）—— Spring AI 原生 Anthropic 模块</li>
 *   <li>{@code deepseek} —— Spring AI 原生 DeepSeek 模块（OpenAI 兼容协议）</li>
 * </ul>
 *
 * <p>权限说明：当前为验证阶段，接口标 {@code @Anonymous} 免登录，便于直接 curl 验证。
 * 后续接入业务时移除 {@code @Anonymous} 并配 {@code @PreAuthorize} 权限标识。</p>
 *
 * <h2>第14篇：加对话记忆</h2>
 *
 * <p>之前每次 {@code .prompt().user(msg).call()} 都是无状态单轮调用——模型答不出前几轮的信息（"失忆"）。
 * 第14篇在 {@link com.ruoyi.aidevops.config.AiClientConfig} 给 ChatClient 挂了 {@code MessageChatMemoryAdvisor}，
 * 调用方只需把 {@code conversationId} 通过 advisor param 传进去，advisor 自动取历史消息塞进请求 +
 * 把这轮对话写回记忆。</p>
 *
 * <p>同一个 {@code conversationId} 的多轮请求共享记忆，模型能记住前几轮说过的话；
 * 不同 {@code conversationId} 互不串扰。不传 {@code conversationId} 时用默认 ID 退化单会话（向后兼容）。</p>
 *
 * <h2>第15篇：Agent 反思循环接口</h2>
 *
 * <p>第15篇在 {@link #chat}（带记忆对话）基础上加 {@link #agent} 接口——记忆 + 工具 + 外层反思循环，
 * 是真·Agent 的形态。与 {@link AIToolController#tool}（第11篇，单次 {@code .call()} 带工具，innerLoop 形态）
 * 的区别：{@code agent} 用 {@code while} 显式驱动反思循环，每步让模型 THINK/FINAL 自检，代码判停 +
 * 步数兜底，中间过程白盒可见（返回每步 trace）。</p>
 *
 * <p>Spring AI 的 Agent 有两层循环：内层 {@code ToolCallingManager}（框架自动，工具调用粒度）+
 * 外层 {@code while}（代码驱动，反思粒度）。外层"一步"里内层可能已调多个工具——两层职责不同、可叠加。</p>
 *
 * @author shandongdong
 * @see com.ruoyi.aidevops.config.AiClientConfig 多模型 ChatClient bean 定义（挂记忆 advisor）
 * @see AiModelRouter 共用的模型路由器
 * @see ChatRequest 请求体（含 conversationId 字段）
 * @see AIToolController 第11篇单次 call 带工具（innerLoop 形态，与 agent 对比）
 */
@RestController
@RequestMapping("/aidevops/ai")
public class ChatController
{
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /** 不传 conversationId 时的默认会话 ID：多个不传的请求共享记忆，退化单会话 */
    private static final String DEFAULT_CONVERSATION_ID = "default";

    /** Agent 外层反思循环最大步数兜底：防模型陷入死循环，到了就强制停（与 AgentAI.MAX_STEPS 一致） */
    private static final int MAX_STEPS = 8;

    private final ChatClient anthropicChatClient;
    private final ChatClient deepSeekChatClient;
    private final WeatherTools weatherTools;
    private final ShellTools shellTools;

    public ChatController(@Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
                         @Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient,
                         WeatherTools weatherTools,
                         ShellTools shellTools)
    {
        this.anthropicChatClient = anthropicChatClient;
        this.deepSeekChatClient = deepSeekChatClient;
        this.weatherTools = weatherTools;
        this.shellTools = shellTools;
    }

    /**
     * 对话接口：接收用户消息，返回大模型回复（带对话记忆）。
     *
     * <p>用 POST + JSON body（原则 VI：对话请求非幂等、消息体可能较长且含敏感信息，不应走 GET 路径参数）。</p>
     *
     * <p>模型路由：{@code model} 走 query param（路由元数据不属于对话内容语义，放 query 更 RESTful，
     * {@link ChatRequest} body 无需改）。路由逻辑见 {@link AiModelRouter#route}——
     * 不传或传空 → 默认 anthropic；非法值 → 抛 {@link IllegalArgumentException} 被 catch 捕获，返回 error。</p>
     *
     * <p>对话记忆：{@code conversationId} 从请求体取，通过
     * {@code .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, convId))} 传给记忆 advisor。
     * 同一 ID 的多轮请求共享记忆，模型能记住前几轮的信息。</p>
     *
     * @param model   模型标识（query param，可选，默认 anthropic）：anthropic / deepseek
     * @param request 包含 message + conversationId 字段的请求体
     * @return AjaxResult 包含模型回复内容 + conversationId
     */
    @Anonymous
    @PostMapping("/chat")
    public AjaxResult chat(@RequestParam(required = false) String model,
                           @RequestBody ChatRequest request)
    {
        if (request.message() == null || request.message().trim().isEmpty())
        {
            return AjaxResult.error("消息内容不能为空");
        }
        // conversationId：客户端传的用它，没传用默认（单会话退化，向后兼容）
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? DEFAULT_CONVERSATION_ID : request.conversationId();
        try
        {
            AiModelRouter.Resolved routed = AiModelRouter.route(model, anthropicChatClient, deepSeekChatClient);
            String reply = routed.client().prompt()
                    .user(request.message())
                    // 传会话 ID：advisor 的 before 阶段据此从 ChatMemory 取历史消息塞进请求，
                    // after 阶段把本轮对话写回 ChatMemory。CONVERSATION_ID 是 ChatMemory 接口的常量。
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            // Map.of 不接受 null value：模型无回复时 .content() 返回 null，直接 Map.of 会 NPE，故判空兜底
            Map<String, Object> data = new HashMap<>();
            data.put("reply", reply == null ? "" : reply);
            data.put("model", routed.model());
            data.put("conversationId", conversationId);  // 回传，方便客户端确认
            return AjaxResult.success(data);
        }
        catch (Exception e)
        {
            // 完整异常栈进日志（e.getMessage() 可能为 null，如 NPE，仅靠响应信息无法排障）
            log.error("[chat] 调用大模型失败 model={} conversationId={}", model, conversationId, e);
            return AjaxResult.error("调用大模型失败：" + e.getMessage());
        }
    }

    /**
     * Agent 接口（第15篇）：接收用户任务，跑外层反思循环，返回最终答复 + 每步过程。
     *
     * <p>与 {@link #chat} 的区别：{@code chat} 是单轮对话（带记忆），{@code agent} 是多步反思循环
     * （带记忆 + 工具 + 外层 {@code while}）。模型每步先反思（THINK），够了给最终答案（FINAL），
     * 代码据此判停；{@code MAX_STEPS} 兜底防死循环。</p>
     *
     * <p>与 {@link AIToolController#tool}（第11篇单次 call 带工具，innerLoop 形态）的区别：
     * {@code tool} 一次 {@code .call()} 内部黑盒多步，{@code agent} 外层 {@code while} 白盒驱动，
     * 每步可见、可判停、可兜底，响应含每步 trace。</p>
     *
     * <p>两层循环：内层 {@code ToolCallingManager}（框架自动，工具调用粒度）+ 外层 {@code while}
     * （代码驱动，反思粒度）。外层"一步"里内层可能已调多个工具——两层职责不同、可叠加。</p>
     *
     * @param model   模型标识（query param，可选，默认 anthropic）：anthropic / deepseek
     * @param request 包含 message（任务）+ conversationId 字段的请求体
     * @return AjaxResult 含 reply（最终答复）、steps（步数）、completed（是否模型主动判停）、trace（每步过程）、conversationId
     */
    @Anonymous
    @PostMapping("/agent")
    public AjaxResult agent(@RequestParam(required = false) String model,
                            @RequestBody ChatRequest request)
    {
        if (request.message() == null || request.message().trim().isEmpty())
        {
            return AjaxResult.error("任务内容不能为空");
        }
        // conversationId：客户端传的用它，没传用默认（与 chat 一致，向后兼容）
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? DEFAULT_CONVERSATION_ID : request.conversationId();
        try
        {
            AiModelRouter.Resolved routed = AiModelRouter.route(model, anthropicChatClient, deepSeekChatClient);
            String task = request.message();

            // 外层反思循环：每步反思一次，模型 FINAL 判停，MAX_STEPS 兜底
            int step = 0;
            String finalAnswer = null;
            List<Map<String, Object>> trace = new ArrayList<>();
            while (step < MAX_STEPS)
            {
                step++;
                // 每步把"当前第 N 步、反思已掌握信息、够了就 FINAL"喂给模型，引导它显式自检。
                // 注意：框架内部 ToolCallingManager 仍可能在一步内连调多个工具（模型一次返回多个 tool call），
                // 外层 while 的"步"是反思粒度，不等同于工具调用粒度——这是两层循环的区别。
                String turnPrompt = "当前是第 " + step + " 步（最多 " + MAX_STEPS + " 步）。\n"
                        + "任务：" + task + "\n"
                        + "按以下格式回答（第一行必须是标记）：\n"
                        + "  THINK —— 反思已掌握信息是否足够，说明还缺什么、打算调哪个工具\n"
                        + "  FINAL —— 信息已足够，直接给出最终答案（不再调工具）\n"
                        + "先反思，再决定下一步。";

                String output = routed.client().prompt()
                        .system("你是一个会反思的 Agent。每一步【最多只调用一个工具】，"
                                + "调用后先反思已掌握信息是否足够，再决定继续调工具（THINK）"
                                + "还是给出最终答案（FINAL）。"
                                + "可以调用查天气和执行只读 shell 命令的工具。")
                        .user(turnPrompt)
                        // 记忆：同 conversationId 贯穿多步，模型记得前几步调了啥
                        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                        // 工具：每步注入，模型自主决定调不调、调哪个
                        .tools(weatherTools, shellTools)
                        .call()
                        .content();

                // 收集每步过程（白盒可观测——outerLoop 相比 innerLoop 的核心价值）
                Map<String, Object> stepTrace = new HashMap<>();
                stepTrace.put("step", step);
                stepTrace.put("output", output == null ? "" : output);
                trace.add(stepTrace);

                if (output != null && output.contains("FINAL"))
                {
                    finalAnswer = output;
                    break;
                }
            }

            // 组装响应
            Map<String, Object> data = new HashMap<>();
            data.put("reply", finalAnswer != null ? finalAnswer : "");
            data.put("model", routed.model());
            data.put("conversationId", conversationId);
            data.put("steps", step);  // 实际跑了多少步
            // completed=true 表示模型主动 FINAL 判停；false 表示达到 MAX_STEPS 兜底（半成品）
            data.put("completed", finalAnswer != null);
            data.put("trace", trace);  // 每步过程
            return AjaxResult.success(data);
        }
        catch (Exception e)
        {
            // 完整异常栈进日志（Agent 多步调用链长，单靠 getMessage() 无法定位是哪步崩的）
            log.error("[agent] Agent 反思循环失败 model={} conversationId={}", model, conversationId, e);
            return AjaxResult.error("Agent 执行失败：" + e.getMessage());
        }
    }
}
