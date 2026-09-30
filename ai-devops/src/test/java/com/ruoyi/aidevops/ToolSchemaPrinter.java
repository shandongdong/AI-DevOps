package com.ruoyi.aidevops;

import java.lang.reflect.Method;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import com.ruoyi.aidevops.tool.shell.ShellTools;
import com.ruoyi.aidevops.tool.weather.WeatherTools;

/**
 * 打印任意 {@code @Tool} 方法的 JSON Schema，验证 Spring AI 把方法反射生成工具定义。
 *
 * <p>用法：{@link #printToolSchema(Object, String, Class[])} 传入任意带 {@code @Tool} 方法的实例，
 * 即可打印出该方法对应的 JSON Schema——也就是真正发给大模型的工具使用说明书。</p>
 */
public class ToolSchemaPrinter
{
    public static void main(String[] args) throws Exception
    {
        // 示例一：天气工具
        printToolSchema(new WeatherTools(null), "getWeather", String.class);
        System.out.println();

        // 示例二：shell 执行工具
        printToolSchema(new ShellTools(null), "executeCommand", String.class);
    }

    /**
     * 打印某个实例上被 {@code @Tool} 标注方法的完整 JSON Schema。
     *
     * @param toolObject  工具实例（无业务依赖时可传 {@code new XxxTools(null)}）
     * @param methodName  @Tool 方法名
     * @param paramTypes  该方法形参类型（反射定位方法用），无参方法传 {@code new Class[0]}
     */
    public static void printToolSchema(Object toolObject, String methodName, Class<?>... paramTypes) throws Exception
    {
        Class<?> toolClass = toolObject.getClass();
        Method method = toolClass.getDeclaredMethod(methodName, paramTypes);

        System.out.println("========== 工具方法: " + toolClass.getSimpleName() + "#" + methodName + " ==========");

        // 第一步：直接调 JsonSchemaGenerator，看"反射方法"这一步到底生成了什么
        System.out.println("[1] JsonSchemaGenerator.generateForMethodInput 的输出：");
        System.out.println(JsonSchemaGenerator.generateForMethodInput(method));
        System.out.println();

        // 第二步：用 MethodToolCallbackProvider 扫描该实例，验证 @Tool 方法确实被注册成工具
        System.out.println("[2] MethodToolCallbackProvider 扫描结果：");
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(toolObject)
                .build()
                .getToolCallbacks();
        for (ToolCallback cb : callbacks)
        {
            System.out.println("  工具名 = " + cb.getToolDefinition().name());
            System.out.println("  描述 = " + cb.getToolDefinition().description());
            System.out.println("  输入 JSON Schema =");
            System.out.println("  " + cb.getToolDefinition().inputSchema());
        }
        System.out.println();
    }
}