package com.ruoyi.aidevops.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ai-devops 模块架构不变量测试（Constitution 原则 IV 机械强制）
 *
 * <p>原则 IV：Controller → Service → Mapper → Domain 严格单向依赖，
 * 依赖方向只能向下，不得跨层（如 Controller 直查 Mapper）或反向。
 * ai-devops 按功能域子包聚合，各层包名后缀固定（..controller.. / ..service.. / ..mapper.. / ..domain..）。
 *
 * <p>本测试是 Computational sensor：每次 {@code mvn test} 机械执行，
 * 违反分层的代码在测试期即失败，无需依赖 LLM 判断或人工 review。
 * 对应 Fowler《Harness Engineering》的 Fitness Functions、
 * OpenAI《在智能体优先的世界中利用 Codex》的"自定义 linter + 结构测试机械强制依赖方向"。
 *
 * @author shandongdong
 */
@AnalyzeClasses(packages = "com.ruoyi.aidevops")
class LayerDependencyTest
{
    /**
     * 原则 IV：四层单向依赖，依赖方向只能 Controller→Service→Mapper→Domain。
     *
     * <p>consideringOnlyDependenciesInAnyPackage 限定只看 ai-devops 内部依赖，
     * 排除对 ruoyi-common（BaseController/AjaxResult）等上游模块的合法依赖，
     * 否则 Controller 继承 BaseController 会被误判为跨层。
     */
    @ArchTest
    static final ArchRule 分层单向依赖 = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("com.ruoyi.aidevops..")
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Mapper").definedBy("..mapper..")
            .layer("Domain").definedBy("..domain..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Mapper").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Controller", "Service", "Mapper");

    /**
     * 原则 IV 补充：Controller 不得直接依赖 Mapper。
     *
     * <p>跨层违规最常见形态：Controller 绕过 Service 直查 Mapper，
     * 会绕过事务边界与日志切面，是 bug 与安全漏洞温床。
     * 此规则独立于分层全量约束，即使包名边界误判也能兜底抓到。
     */
    @ArchTest
    static final ArchRule Controller不得直接访问Mapper = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..mapper..")
            .as("Controller 不得直接依赖 Mapper，必须经 Service 层（原则 IV 分层单向依赖）");
}
