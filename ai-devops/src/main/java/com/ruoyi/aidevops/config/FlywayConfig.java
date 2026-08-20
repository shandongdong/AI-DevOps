package com.ruoyi.aidevops.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 数据库版本管理配置
 *
 * 由于项目使用了动态数据源并排除了 DataSourceAutoConfiguration，
 * 需要手动配置 Flyway 使用主数据源进行迁移。
 * matchIfMissing = false：当配置不存在时，Flyway 不会执行。
 *
 * 注意：Spring Boot 4.1.0 已移除 FlywayAutoConfiguration（迁移由本类手动管理），
 * 因此 application.yml 中的 spring.flyway.enabled / spring.flyway.locations
 * 只对本类生效（@ConditionalOnProperty + @Value），不再有框架自动绑定。
 *
 * @author shandongdong
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = false)
public class FlywayConfig
{
    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    /**
     * 配置 Flyway 使用主数据源，并在 Bean 创建时立即执行迁移，
     * 确保在其他 Bean（依赖业务表的 Service 等）初始化之前完成表结构就绪。
     *
     * @param masterDataSource 主数据源
     * @param locations 迁移脚本位置，默认 classpath:db/migration（可由 application.yml 覆盖）
     * @return Flyway 实例
     */
    @Bean
    public Flyway flyway(@Qualifier("masterDataSource") DataSource masterDataSource,
                         @Value("${spring.flyway.locations:classpath:db/migration}") String locations)
    {
        Flyway flyway = Flyway.configure()
                .dataSource(masterDataSource)
                .locations(locations)
                .encoding("UTF-8")
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .sqlMigrationSuffixes(".sql")
                .validateOnMigrate(true)
                // cleanDisabled 留默认值（Flyway 12+ 默认 true，禁用破坏性 clean，防止误清库）
                .baselineOnMigrate(true)
                // baselineVersion=2：V1(若依基础表 ry_20260417.sql) 与 V2(quartz 定时任务表 quartz.sql)
                //   这两个若依基础脚本在项目启用 Flyway 之前已由人工在开发/测试/生产库手动执行过。
                //   - 已有库（schema 非空）：baselineOnMigrate 在 version=2 打基线，V1/V2(version<=2)被标记为
                //     已应用而跳过，不会重复建表/插数据，避免覆盖现有业务数据。
                //   - 全新空库：Flyway 不打 baseline，V1/V2 会正常执行，完成若依基础表初始化。
                //   后续 ai-devops 业务表迁移从 V3 起编号，无论新库旧库都会被正常应用。
                .baselineVersion("2")
                .baselineDescription("Baseline: V1-V2 (ruoyi base + quartz tables) already executed manually before Flyway")
                .validateMigrationNaming(true)
                .load();

        // 不无条件执行 repair()：新库无历史可修，且 repair 会清除失败迁移记录、掩盖问题。
        // checksum 不一致由 validateOnMigrate 在 migrate 时直接暴露并抛出，让启动失败以提示运维。
        log.info("Flyway 迁移开始，locations={}, 数据源={}", locations, masterDataSource);
        flyway.migrate();
        log.info("Flyway 迁移完成");
        return flyway;
    }
}
