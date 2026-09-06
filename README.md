# CRM 接口自动化测试框架

基于自己开发的 CRM 系统(Spring Boot)做的接口自动化测试实践。
技术栈:**Java + RestAssured + TestNG + Maven + MySQL + Allure + Jenkins + Git**。

## 一、项目结构(三层架构)

```
crm-api-autotest
├── pom.xml                          # Maven 依赖与构建
├── testng.xml                       # 测试套件编排 + 全局重试监听器注册
├── Jenkinsfile                      # Jenkins 定时回归流水线
└── src
    ├── main/java/com/crm/autotest   # ↓ 框架部分 ↓
    │   ├── config/ConfigManager     # 配置中心:环境信息全部收敛到 config.yaml
    │   ├── core/ApiClient           # 工具层:统一请求封装(baseUrl/token头/日志/Allure附件)
    │   ├── core/ApiAssertion        # 工具层:统一断言(业务码/JSONPath/数据库三类)
    │   ├── utils/TokenManager       # 工具层:登录一次缓存 token,解决接口登录依赖
    │   ├── utils/YamlReader         # 工具层:YAML 数据驱动 → TestNG DataProvider
    │   ├── utils/DbUtil             # 工具层:数据库查询(PreparedStatement 预编译)
    │   ├── utils/RetryAnalyzer      # 工具层:失败自动重试(最多 2 次)
    │   ├── utils/RetryListener      # 工具层:全局注入重试,用例零侵入
    │   └── service/                 # 业务层:一个方法封装一个接口,用例层不拼报文
    │       ├── AuthApiService
    │       └── CustomerApiService
    ├── main/resources/config.yaml   # 环境配置(域名/账号/DB/接口路径)
    └── test/java/com/crm/autotest
        └── testcase/                # 用例层:只写业务断言
            ├── LoginTest            # 登录:YAML 数据驱动
            └── CustomerTest         # 客户:增删改查全链路 + DB 断言
    └── test/resources/data/*.yaml   # 用例数据
```

## 二、简历描述 ↔ 代码对照(面试前把每一条都对上)

| 简历描述 | 代码位置 |
|---|---|
| 三层架构(用例层/业务层/工具层) | `testcase/` → `service/` → `core + utils + config/` |
| 封装统一请求 | `core/ApiClient.java`(统一 baseUrl、token 头、报文日志、Allure 附件) |
| 统一断言 + MySQL 数据库断言 | `core/ApiAssertion.java` + `utils/DbUtil.java` |
| YAML 数据驱动,一套脚本多组参数 | `utils/YamlReader.java` + `data/login_data.yaml` + `LoginTest` |
| 登录 token 依赖处理 | `utils/TokenManager.java`(全局登录一次,双检锁缓存) |
| 接口间数据传递 | `CustomerTest`(create 返回的 id 传给后续查询/修改/删除) |
| 失败自动重试 | `utils/RetryAnalyzer.java` + `RetryListener.java`(IAnnotationTransformer 全局注入) |
| Allure 可视化报告 | `pom.xml` 引 allure-testng;ApiClient/ApiAssertion 里写入请求报文附件与断言步骤 |
| Jenkins 定时回归 | `Jenkinsfile`(cron 定时 + allure 插件发布报告) |

## 三、快速开始

前置:JDK 8+、Maven 3.6+、MySQL(可选,做 DB 断言用)、Allure 命令行(看报告用)。

```bash
# 1. 启动本地 CRM,修改 src/main/resources/config.yaml 指向它
# 2. 执行测试
mvn clean test
# 3. 查看 Allure 报告(结果目录为项目根下的 allure-results/)
allure serve allure-results
```

无 MySQL 时,把 config.yaml 的 `db.enabled` 设为 false,只做接口层断言;对接真实 CRM 后改回 true。

### 本地验证环境(项目外,可整体删除)

`D:\test\tools\` 下有本次验证用的便携环境,不属于项目本身:

| 内容 | 说明 |
|---|---|
| jdk-17.0.20.1+1/ | Temurin JDK 17(本机原本没有 Java) |
| apache-maven-3.9.16/ | Maven(免安装) |
| MockCrmServer.java/.class | Mock CRM 服务,实现登录/token校验/客户增删改查,内存存储 |

复现方式:先 `java -cp tools MockCrmServer` 启动 Mock(config.yaml 的 base-url 已指向 18080),再 `mvn clean test`。**对接真实 CRM 后:改 base-url、删掉 tools 目录即可。**
本次验证结果:`mvn clean test` → `Tests run: 9, Failures: 0, Errors: 0` + `BUILD SUCCESS`;数据库断言路径已用 H2(MySQL 兼容模式)冒烟验证,连真实 MySQL 后只需确认连接配置。


## 四、对接自己的 CRM(改造清单)

1. **config.yaml**:改 `base-url`、登录账号、`auth.token-path`(登录响应里 token 的 JSONPath)、`db.*`、`api.*` 接口路径;
2. **service 层**:按真实接口的请求字段调整 `CustomerApiService` 里的 body 字段名(如 `customerName` → 你的字段),新增模块就照着加一个 Service;
3. **数据文件**:`data/login_data.yaml` 里的 `expectedCode` 按你 CRM 的业务码约定改(失败是 500 还是 40x);
4. **DB 断言 SQL**:改成你真实的表名/字段名;**注意逻辑删除**——若删除是置 `is_deleted=1`,`testDeleteCustomer` 里的断言要改为查标志位,而不是行数为 0;
5. 跑通后把代码推到 GitHub,简历项目链接指向它。

## 五、面试高频问题(先想清楚再写简历)

- **接口依赖怎么处理?** 登录态:TokenManager 全局登录一次缓存 token,401 时 invalidate 后自动重登;业务数据:上游接口响应里提取(如新增返回的 id),用类字段 + dependsOnMethods 传递和保序,前置失败则后续跳过。
- **失败重试会不会掩盖真实 bug?** 只重试 2 次,针对网络抖动/环境重启这类偶发问题;真实功能缺陷重试后依然失败;每次执行的请求响应都留在 Allure 附件里可回溯。
- **为什么用 YAML 做数据驱动而不是 Excel?** 轻量、可读、Git 版本可控,天然解析成 Map 直接映射参数;Excel 适合复杂多 sheet 场景。
- **为什么要有数据库断言?** 接口返回成功≠落库正确(字段映射错、逻辑删除漏改标志位等),从 DB 视角再验一次才算闭环。
- **怎么避免污染测试环境?** 用例数据带时间戳前缀可辨识,删除用例收尾清理;条件允许用专用测试账号/测试库。

## 六、可以继续加分的扩展方向

- 多环境切换:config-dev.yaml / config-test.yaml + Maven profile 或 JVM 参数选择;
- 并发执行:testng.xml 开 `parallel="methods"`,token 缓存改成线程安全初始化;
- 接口签名/加密:在 ApiClient 统一拦截处加签;
- 测试结果推送:Jenkins 构建后调用钉钉/企业微信机器人发日报;
- 数据工厂:为复杂前置数据写初始化脚本,替代手工造数。
