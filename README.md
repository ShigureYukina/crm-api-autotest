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
    │       ├── AuthApiService       # 登录(form-urlencoded + 字段名可配置)
    │       ├── ClueApiService       # 线索:新增 / 转客户 / 详情
    │       └── CustomerApiService   # 客户:分页列表 / 详情
    ├── main/resources/config.yaml   # 环境配置(域名/账号/DB/接口路径)
    └── test/java/com/crm/autotest
        └── testcase/                # 用例层:只写业务断言
            ├── LoginTest            # 登录:YAML 数据驱动(正常/异常 5 组)
            └── CustomerTest         # 客户:建线索→转客户→列表校验 全链路 + DB 断言
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
| 接口间数据传递 | `CustomerTest`(建线索→转客户→分页查列表,clueId/customerId 在用例间传递) |
| 失败自动重试 | `utils/RetryAnalyzer.java` + `RetryListener.java`(IAnnotationTransformer 全局注入) |
| Allure 可视化报告 | `pom.xml` 引 allure-testng;ApiClient/ApiAssertion 里写入请求报文附件与断言步骤 |
| Jenkins 定时回归 | `Jenkinsfile`(cron 定时 + allure 插件发布报告) |
| 接口未回传主键时怎么拿 id | `CustomerTest.testCreateClueAndConvertToCustomer`(新增线索不返回 id,转而用 DbUtil 反查) |
| 已知缺陷如何回归不飘红 | `CustomerTest.testCustomerDetailKnownSutBug`(断言缺陷现状 + 注释标明修复后如何切换) |

## 三、快速开始

前置:JDK 17、Maven 3.6+、MySQL 8、Redis。
**Redis 不是可选项**——dlyk 的登录态存在 Redis,TokenVerifyFilter 每次请求都会拿 Redis 里的
token 做比对,没有 Redis 所有业务接口都会返回 903。

被测系统是 `../dlyk-2in1`,用 Docker 起依赖:

```bash
docker run -d --name dlyk-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=1056398086 -e MYSQL_DATABASE=dlyk \
  -v dlyk-mysql-data:/var/lib/mysql mysql:8.0 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci

docker run -d --name dlyk-redis -p 6379:6379 \
  -v dlyk-redis-data:/data redis:7.2-alpine redis-server --appendonly yes

# 导入数据(dlyk.sql 里没有 CREATE DATABASE,库名由上面的 MYSQL_DATABASE 建好)
docker exec -i dlyk-mysql mysql -uroot -p1056398086 --default-character-set=utf8mb4 dlyk < ../dlyk-2in1/dlyk.sql

# 补一列:dlyk.sql 是 2023 年的旧导出,而代码里的逻辑删除依赖 t_clue.deleted
docker exec dlyk-mysql mysql -uroot -p1056398086 \
  -e "ALTER TABLE dlyk.t_clue ADD COLUMN deleted int NULL DEFAULT 0 AFTER edit_by;"
```

启动被测系统并执行测试:

```bash
cd ../dlyk-2in1/dlyk-server && mvn spring-boot:run   # 起在 :8089
cd ../../crm-api-autotest && mvn clean test
allure serve allure-results                          # 看报告
```

无 MySQL/Redis 时可把 config.yaml 的 `db.enabled` 设为 false,只做接口层断言。

**本次实测**(2026-09-12,对着 dlyk-2in1 真实服务):`Tests run: 14, Failures: 0, Errors: 0` + `BUILD SUCCESS`,跑完不留脏数据。

### 开发期用的 Mock 环境(项目外,可整体删除)

框架最初是脱离真实系统开发验证的,`D:\test\tools\` 下留了那套便携环境:

| 内容 | 说明 |
|---|---|
| jdk-17.0.20.1+1/ | Temurin JDK 17(本机原本没有 Java) |
| apache-maven-3.9.16/ | Maven(免安装) |
| MockCrmServer.java/.class | Mock CRM 服务,实现登录/token校验/客户增删改查,内存存储,端口 18080 |

现在 config.yaml 已经指向真实 dlyk(8089),Mock 不再需要;留着只是为了说明「先用契约桩把框架骨架调通,
再对接真实系统」这个顺序。


## 四、对接 dlyk-2in1 时踩到的坑(改造实录)

这版框架已经从 Mock 切到真实系统,下面是对接中真实改掉的东西,也是面试时最有说服力的部分:

| 差异点 | 框架原假设(Mock 契约) | dlyk 真实契约 | 改在哪 |
|---|---|---|---|
| 登录提交方式 | `POST /login`,JSON `{username,password}` | `POST /api/login`,**form-urlencoded** `loginAct/loginPwd`(Spring Security formLogin) | `AuthApiService` 改用 `formParam`,字段名提到 `config.yaml` |
| token 在响应里的位置 | `data.token` | `data`(裸 JWT 字符串,`R.OK(jwt)`) | `config.yaml` 的 `auth.token-path` |
| token 请求头 | `Authorization: Bearer xxx` | `Authorization: xxx`,**无 Bearer 前缀** | `config.yaml` 的 `auth.header-prefix` 置空 |
| 客户怎么新增 | `POST /crm/customer` 直接建 | **没有这个接口**,必须先建线索再 `POST /api/clue/convert/{id}` | 新增 `ClueApiService`,用例重排为「建线索→转客户」 |
| 列表过滤 | 支持 `keyword` | 只支持 `current` 翻页,每页固定 10 条 | `CustomerTest` 改成按总页数遍历查找 |
| 中文参数 | 无感知 | RestAssured 默认按 ISO-8859-1 编码表单,中文落库变 `EFBFBD` 乱码 | `ApiClient.UTF8_FORM_CONFIG` 显式指定 UTF-8 |
| 新增接口不回传主键 | 新增返回 `data.id` | 新增线索只返回 `code/msg`,**没有 id** | 用 `DbUtil.queryOne` 按手机号反查 id |
| 逻辑删除 | 无 | `t_clue`/`t_customer` 都用 `deleted` 字段做逻辑删除 | DB 断言改查标志位而不是行数为 0 |
| 删除接口 | `DELETE /crm/customer/{id}` | 客户原本**没有**删除接口 | 给 dlyk 补了 `DELETE /api/customer/{id}`(见下) |

**顺带发现的一个真实缺陷**:`GET /api/customer/{id}` 稳定报 500,根因是
`TCustomerMapper.java` 声明了 `selectCustomerDetailById`,但 `TCustomerMapper.xml` 里没有对应的
`<select>`,MyBatis 抛 `Invalid bound statement`。用例 `testCustomerDetailKnownSutBug` 把这个缺陷
钉在回归里(断言现状,避免 CI 飘红),系统修复后改成 `ApiAssertion.assertSuccess` 即可。

### 为打通「删除」链路,给被测系统做的修复与增强

自动化跑通「建线索→转客户」后,造出来的数据删不掉,反而暴露/补齐了被测系统的两处问题:

1. **修复线索删除(真 bug)**:`ClueServiceImpl.deleteClue` 依赖 `updateByPrimaryKeySelective`
   把 `deleted` 置 1,但 `TClueMapper.xml` 的 `<set>` 里漏了 `deleted` 子句 → 动态 SQL 整段为空,
   生成的语句是 `UPDATE t_clue where id = ?`,直接 SQL 语法错误,删除接口 500。
   修复:补上 `<if test="deleted != null"> deleted = ... </if>`。
2. **新增客户删除接口**:照线索模块的逻辑删除模式补齐——
   `t_customer` 加 `deleted` 列 → `TCustomerMapper` 加 `logicalDeleteById` →
   `CustomerService.deleteCustomer` → `DELETE /api/customer/{id}`;
   同时给 `selectCustomerPage`/`selectCustomerExcel` 加上 `deleted` 过滤,已删客户不再出现在列表。
   `dlyk.sql` 的 `t_clue`/`t_customer` 建表语句也同步补了 `deleted` 列,新环境一次导入即可用。

用例链路因此闭环:`testDeleteCustomer`(逻辑删除断言标志位 + 列表不可见)和
`testDeleteClueCleanup`(收尾删线索),**跑完测试库不留脏数据**。

**本次实测**(2026-09-12,对着 dlyk-2in1 真实服务):`Tests run: 14, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

## 五、面试高频问题(先想清楚再写简历)

- **接口依赖怎么处理?** 登录态:TokenManager 全局登录一次缓存 token,401 时 invalidate 后自动重登;业务数据:上游接口响应里提取(如新增返回的 id),用类字段 + dependsOnMethods 传递和保序,前置失败则后续跳过。**上游不回传 id 时**(dlyk 的新增线索接口就是这样),退化为按业务唯一键(手机号)查库反查,这个折中也要能讲清楚。
- **失败重试会不会掩盖真实 bug?** 只重试 2 次,针对网络抖动/环境重启这类偶发问题;真实功能缺陷重试后依然失败;每次执行的请求响应都留在 Allure 附件里可回溯。
- **为什么用 YAML 做数据驱动而不是 Excel?** 轻量、可读、Git 版本可控,天然解析成 Map 直接映射参数;Excel 适合复杂多 sheet 场景。
- **为什么要有数据库断言?** 接口返回成功≠落库正确。这里的两个实例:一是转换线索时接口返回 200,但要确认 `t_customer` 真的多了一行、`t_clue.state` 真的改成了 -1;二是逻辑删除要查 `deleted` 标志位而不是行数为 0。
- **已知缺陷怎么处理?** 不能让 CI 一直飘红,也不能假装没看见。做法是单独写一条用例断言缺陷现状并在注释里写清根因和修复后的切换方式,缺陷因此留在回归范围内。
- **怎么避免污染测试环境?** 用例数据带时间戳前缀可辨识;条件允许用专用测试账号/测试库。注意 dlyk 的"客户"无法直接删除(没有删除接口),所以造的客户数据会累积,更依赖可辨识前缀 + 定期重置测试库。

## 六、可以继续加分的扩展方向

- 多环境切换:config-dev.yaml / config-test.yaml + Maven profile 或 JVM 参数选择;
- 并发执行:testng.xml 开 `parallel="methods"`,token 缓存改成线程安全初始化;
- 接口签名/加密:在 ApiClient 统一拦截处加签;
- 测试结果推送:Jenkins 构建后调用钉钉/企业微信机器人发日报;
- 数据工厂:为复杂前置数据写初始化脚本,替代手工造数。
