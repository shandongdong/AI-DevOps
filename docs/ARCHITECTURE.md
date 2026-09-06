# ARCHITECTURE — 项目架构顶层地图

> 基于 RuoYi-Vue 3.9.2（Spring Boot 4.1.0 / Java 17）单体多模块架构，前端 Vue3 + TS 独立工程（zip 引入，无 git 跟踪）。本文是域和包分层的顶层地图——系统全景、模块依赖、请求链路、目录结构、前端架构一目了然。详细开发规范见 [项目开发规范](design-docs/项目开发规范.md)。

## 一、系统整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    前端层 (ruoyi-ui-vue3)                        │
│               Vue 3.5.26 + Element Plus 2.13.1                  │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────────┐   │
│  │ 系统管理  │ 系统监控  │ 定时任务  │ 代码生成  │  开发工具     │   │
│  └──────────┴──────────┴──────────┴──────────┴──────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              ai-devops 业务页面（规划中）                 │   │
│  └──────────────────────────────────────────────────────────┘   │
│  状态：Pinia   路由：Vue Router(动态)   请求：axios            │
└─────────────────────────────────────────────────────────────────┘
                              ↕ HTTP/HTTPS (RESTful + JWT)
┌─────────────────────────────────────────────────────────────────┐
│                  后端服务层 (ruoyi-admin:8080)                  │
│                    Spring Boot 4.1.0 / Java 17                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              控制器层 (Controller)                      │    │
│  │  web/controller/{system,monitor,tool,common}            │    │
│  │  + com.ruoyi.aidevops.controller（ai-devops 业务）       │    │
│  └─────────────────────────────────────────────────────────┘    │
│  ┌───────────────────────┐  ┌──────────────────────────────┐   │
│  │  Service / Mapper       │  │  Security + JWT 鉴权           │   │
│  │  (ruoyi-system)         │  │  (ruoyi-framework)            │   │
│  └───────────────────────┘  └──────────────────────────────┘   │
│  ┌──────────┬──────────┬──────────┬──────────────────────┐      │
│  │ 定时任务  │ 代码生成  │ 数据源   │  缓存/通用工具        │      │
│  │ quartz   │ generator│ Druid   │  Redis + ruoyi-common │      │
│  └──────────┴──────────┴──────────┴──────────────────────┘      │
└─────────────────────────────────────────────────────────────────┘
        ↕                              ↕
┌──────────────────┐          ┌──────────────────────┐
│   MySQL 8.0      │          │     Redis 6+          │
│  sys_*/gen_*/     │          │  登录态/限流/缓存/字典 │
│  QRTZ_*/sys_job*  │          └──────────────────────┘
└──────────────────┘
```

## 二、Maven 模块依赖关系

```
                    ┌─────────────────┐
                    │   pom.xml(根)    │  版本管理 + 聚合 7 模块
                    │   ruoyi 3.9.2    │
                    └────────┬────────┘
                             │ parent
        ┌────────────────────┼────────────────────────┐
        ▼                    ▼                        ▼
┌──────────────┐    ┌────────────────┐      ┌──────────────────┐
│ ruoyi-admin  │    │ ruoyi-framework│      │  ai-devops(自有) │
│ (启动/web入口)│    │ (框架核心)      │      │  com.ruoyi.aidevops│
└──────┬───────┘    └───────┬────────┘      └────────┬─────────┘
       │ depends            │ depends                │ depends
       ├────────────────────►│                        │
       │   ┌─────────────────┴────────────────┐       │
       │   ▼              ▼              ▼            │
       │ ┌────────┐  ┌──────────┐  ┌───────────┐     │
       │ │ruoyi-  │  │ruoyi-    │  │ruoyi-     │     │
       │ │system  │  │quartz    │  │generator  │     │
       │ └────┬───┘  └────┬─────┘  └─────┬─────┘     │
       │      │           │              │           │
       │      └───────────┴──────────────┘           │
       │             │ depends                      │ depends
       │             ▼                              │
       │      ┌──────────────┐◄─────────────────────┘
       └─────►│ ruoyi-common │  (AjaxResult/@Anonymous/utils)
              └──────────────┘
```

依赖要点：

- `ruoyi-admin` 依赖 `ruoyi-framework`、`ruoyi-quartz`、`ruoyi-generator`、`ai-devops`，是唯一启动模块。
- `ruoyi-framework` 依赖 `ruoyi-system` 和 `ruoyi-common`，承载 Security/数据源/切面/拦截器。
- `ai-devops` 只依赖 `ruoyi-common`，与上游业务模块解耦，零冲突。

## 三、请求处理链路

```
浏览器
  │ HTTP + JWT Header
  ▼
DispatcherServlet
  │
  ├─ Security Filter Chain (ruoyi-framework)
  │    ├─ JwtAuthenticationTokenFilter  ← 校验 token
  │    └─ PermitAllUrlProperties        ← @Anonymous 路径直接放行
  │
  ├─ Interceptor (ruoyi-framework)
  │    └─ RepeatSubmitInterceptor 等
  │
  ├─ Controller (@RestController)
  │    ├─ admin: web/controller/**
  │    └─ ai-devops: com.ruoyi.aidevops.controller
  │
  ├─ Aspectj (ruoyi-framework)
  │    ├─ @Log          → 记录操作日志
  │    ├─ @DataScope    → 部门数据权限拼接
  │    └─ @DataSource   → 切换主从数据源
  │
  ├─ Service (ruoyi-system / quartz / generator)
  │
  ├─ Mapper (MyBatis + PageHelper)
  │
  └─ MySQL / Redis
```

## 四、项目目录结构

### 4.1 项目整体结构

```
AI-DevOps/
├── pom.xml                      根 pom（聚合 + 版本管理）
├── LICENSE
├── ry.sh                        启动脚本（Linux，Mac/Ubuntu 部署环境）
├── deploy_to_prod.sh            生产环境一键部署脚本（三机滚动）
├── deploy_to_test.sh            测试环境一键部署脚本（单机）
├── docs/                        本项目文档体系（项目级静态文档）
│   ├── README.md                文档总入口与导航
│   ├── ARCHITECTURE.md          架构顶层地图（本文）
│   ├── design-docs/             设计文档（技术方案/架构/规范/流程等）
│   ├── product-specs/           功能模块完整静态文档（模块全貌）
│   ├── tech-debt-tracker.md     技术债追踪
│   ├── deploy/                  多环境部署资产
│   ├── hifi-prototypes/         高保真原型模板（通用 CRUD，纯 HTML+CDN）
│   └── tools/                   工具脚本说明（规划中）
├── memory/                      SDD 全局宪法（constitution.md，治理原则）
├── specs/                       SDD 功能规范产物（spec/plan/tasks，按功能分目录）
├── ruoyi-admin/                 启动模块（web 入口）
├── ruoyi-framework/             框架核心（Security/数据源/切面）
├── ruoyi-system/                 系统业务（用户/角色/菜单...）
├── ruoyi-quartz/                定时任务
├── ruoyi-generator/             代码生成
├── ruoyi-common/                通用工具
├── ai-devops/                   自有业务模块（新建，上游零触碰）
└── ruoyi-ui-vue3/               前端（Vue3 + TS + Vite）
```

### 4.2 后端模块详细结构

**ruoyi-admin（启动模块）**

```
ruoyi-admin/
├── pom.xml                      依赖 framework/quartz/generator/ai-devops
└── src/main/
    ├── java/com/ruoyi/
    │   ├── RuoYiApplication.java            启动类（@SpringBootApplication）
    │   └── web/
    │       ├── core/config/                 Web 配置（如 Druid 监控 servlet）
    │       └── controller/
    │           ├── common/                  通用（文件上传下载 CommonController）
    │           ├── monitor/                 监控（日志/在线用户/服务/缓存）
    │           ├── system/                 系统管理（用户/角色/菜单/字典...）
    │           └── tool/                   开发工具（测试接口/表单构建）
    └── resources/
        ├── application.yml                  主配置（端口/上传/token/MyBatis 等，active: dev,druid-dev）
        ├── application-dev.yml              开发环境（Redis .41 + Flyway 关 + debug 日志）
        ├── application-druid-dev.yml        开发环境数据源（.41/ai-devops，凭据环境变量化）
        ├── application-test.yml             测试环境（Redis .41 + Flyway 开 + info 日志）
        ├── application-druid-test.yml       测试环境数据源（.41/ai-devops）
        ├── application-prod.yml             生产环境（Redis .31 + Flyway 开 + info 日志）
        ├── application-druid-prod.yml       生产环境数据源（.30/ai-devops）
        ├── logback.xml                     日志
        ├── i18n/                            国际化
        └── META-INF/spring-devtools.properties
```

**ruoyi-framework（框架核心）**

```
ruoyi-framework/src/main/java/com/ruoyi/framework/
├── aspectj/                    @Log/@DataScope/@DataSource 切面
├── config/
│   ├── properties/             PermitAllUrlProperties（@Anonymous 扫描）
│   ├── SecurityConfig          Spring Security 配置
│   ├── DruidConfig             Druid 数据源
│   ├── MyBatisConfig           MyBatis 配置
│   └── ApplicationConfig       应用配置
├── datasource/                 多数据源（主从）动态切换
├── interceptor/
│   └── impl/                   RepeatSubmitInterceptor 防重复提交
├── manager/
│   └── factory/                异步任务工厂（登录日志等）
├── security/
│   ├── context/                SecurityContext
│   ├── filter/                 JwtAuthenticationTokenFilter
│   └── handle/                 认证/登出/未授权处理
└── web/
    ├── domain/server/          服务器监控实体（oshi）
    ├── exception/              全局异常
    └── service/                Web 层服务（如 token、登录策略）
```

**ruoyi-system / ruoyi-quartz / ruoyi-generator / ruoyi-common**

```
ruoyi-system/.../com/ruoyi/system/      controller/ service/impl/ mapper/ domain/vo/
ruoyi-quartz/.../com/ruoyi/quartz/      controller/ service/impl/ mapper/ domain/ task/ util/ config/
ruoyi-generator/.../com/ruoyi/generator/ controller/ service/ mapper/ domain/ config/ util/
ruoyi-common/.../com/ruoyi/common/
├── annotation/               @Anonymous @DataScope @DataSource @Excel @Log @RateLimiter @RepeatSubmit @Sensitive
├── core/                     BaseController / AjaxResult / BaseEntity / page / redis / text
├── config/                   全局配置 + serializer
├── enums/                    枚举常量
├── exception/                ServiceException 等
├── filter/                   请求过滤
├── utils/                    SecurityUtils/StringUtils/DateUtils/PageUtils/DictUtils/Arith + file/html/http/ip/poi/reflect/sign/sql/uuid
└── xss/                      XSS 清洗
```

**ai-devops（自有业务模块）**

```
ai-devops/
├── pom.xml                      parent=ruoyi:3.9.2，依赖 ruoyi-common + flyway + spring-boot-starter-test(test)
└── src/
    ├── main/
    │   ├── java/com/ruoyi/aidevops/
    │   │   ├── config/
    │   │   │   └── FlywayConfig.java          Flyway 配置（baselineVersion=2）
    │   │   ├── controller/
    │   │   │   ├── HealthController.java       GET /aidevops/health（@Anonymous）
    │   │   │   └── calc/                       计算器功能域
    │   │   │       └── CalcController.java     compute/list/remove，权限 aidevops:calc:*
    │   │   ├── domain/
    │   │   │   └── calc/
    │   │   │       ├── CalcHistoryEntity.java    继承 BaseEntity，审计字段 + del_flag
    │   │   │       └── CalcComputeRequest.java   compute 入参 record（operator, first, second）
    │   │   ├── mapper/
    │   │   │   └── calc/
    │   │   │       └── CalcHistoryMapper.java  insert/selectList/软删除
    │   │   └── service/
    │   │       └── calc/
    │   │           ├── ICalcHistoryService.java
    │   │           └── impl/CalcHistoryServiceImpl.java  运算逻辑 + 历史查询 + 软删除
    │   └── resources/
    │       ├── db/migration/                   Flyway 迁移脚本
    │       │   ├── V1__ruoyi_base_tables.sql   若依基础表
    │       │   ├── V2__quartz_tables.sql       Quartz 表
    │       │   └── V3__create_ai_devops_calc_history.sql  计算历史表 + sys_menu 菜单
    │       └── mapper/calc/
    │           └── CalcHistoryMapper.xml        MyBatis XML（del_flag 过滤 + 用户隔离）
    └── test/java/com/ruoyi/aidevops/
        ├── architecture/LayerDependencyTest.java    ArchUnit 分层约束（原则 IV 机械强制）
        └── service/calc/CalcHistoryServiceImplTest.java  JUnit5 + Mockito
```

> 业务按功能域分子包（`controller/service/mapper/domain` 各下按 calc 等模块细分），在此模块内扩展，保持与上游解耦。业务表迁移走 Flyway（脚本放 `db/migration/`，业务表从 V3 起编号），菜单/权限走 `sys_menu` 数据驱动，详见 [项目开发规范](design-docs/项目开发规范.md)。

### 4.3 前端模块结构

```
ruoyi-ui-vue3/
├── package.json                 Vue3.5.26 + ElementPlus2.13.1 + Vite6.4.1
├── vite.config.ts               构建配置 + devServer 代理 8080
├── tsconfig.json
├── index.html
└── src/
    ├── main.ts                  入口（挂载 Pinia/Router/ElementPlus）
    ├── App.vue
    ├── permission.ts            路由守卫（token + 动态路由）
    ├── settings.ts              前端设置
    ├── api/                     接口封装
    │   ├── system/  monitor/  tool/
    │   └── ai-devops/           自有业务接口
    │       └── calc.ts          compute/listCalc/delCalc
    ├── assets/                  静态资源（icons/images/styles/logo）
    ├── components/              公共组件（Pagination/Editor/FileUpload...）
    ├── directive/
    │   ├── common/  permission/   v-hasPermi 权限指令
    ├── layout/                  整体布局
    ├── plugins/                 插件
    ├── router/                  静态路由（动态路由运行时注册）
    ├── store/
    │   └── modules/             Pinia（user/permission/dict...）
    ├── types/
    │   └── api/                 TS 类型
    │       └── ai-devops/calc.ts   CalcHistory / CalcHistoryQueryParams
    ├── utils/                   工具（request/auth/ruoyi + generator/）
    └── views/
        ├── system/  monitor/  tool/  error/  login.vue  register.vue  index.vue  lock.vue
        └── ai-devops/            自有业务页面
            └── calc/
                ├── index.vue     计算器页（按钮式）
                └── history.vue   历史记录列表页
```

前端关键流：登录 → 后端签发 JWT → 前端存 token → `permission.ts` 拉取用户菜单 → 后端按 `sys_menu` 生成动态路由 → 注册到 Router → 渲染页面。

## 五、配置文件说明

| 文件 | 作用 |
|------|------|
| `pom.xml`（根） | 聚合 7 模块 + `<dependencyManagement>` 统一版本 |
| `ruoyi-admin/.../application.yml` | 端口 8080、Redis、上传路径、验证码 |
| `ruoyi-admin/.../application-druid-{dev,test,prod}.yml` | 三环境 Druid 主库连接（凭据环境变量化） |
| `ruoyi-admin/.../logback.xml` | 日志路径 `./ruoyi/logs` |
| `ruoyi-ui-vue3/vite.config.ts` | 前端构建 + 代理后端 8080 |
| `ruoyi-ui-vue3/package.json` | 前端依赖与脚本 |
