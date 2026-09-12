package com.crm.autotest.testcase;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.core.ApiAssertion;
import com.crm.autotest.core.ApiClient;
import com.crm.autotest.service.AuthApiService;
import com.crm.autotest.utils.JwtUtil;
import com.crm.autotest.utils.RedisUtil;
import com.crm.autotest.utils.TokenManager;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;

/**
 * 用例层:登录态(token)生命周期。
 *
 * dlyk 的 token 架构:登录发 JWT(本身无 exp claim)→ 写入 Redis(dlyk:user:login:{userId})
 * → 之后每个请求由 TokenVerifyFilter 做"JWT 签名 + Redis 比对"双重校验。
 * 过期/互踢全靠 Redis TTL 与覆写实现,所以断言必须打到 Redis 侧,只看 HTTP 响应不够。
 *
 * 本类全部用 config.yaml 配置的主账号:登出/互踢/删 key 都只动该账号自己的登录态,
 * 不影响其它账号;业务模块用例的 TokenManager 缓存与本类互不干扰(每类结束后 invalidate)。
 */
public class TokenLifecycleTest {

    private String user() {
        return ConfigManager.get("auth.username");
    }

    private String pwd() {
        return ConfigManager.get("auth.password");
    }

    @AfterClass(alwaysRun = true)
    public void restoreTokenManagerState() {
        // 本类反复登录/登出/删 key,主线程缓存的 token 已不可靠,强制下一次重新登录
        TokenManager.invalidate();
    }

    @Test(priority = 1, description = "901:不带 token 访问业务接口被拒")
    public void test901TokenMissing() {
        Response resp = given()
                .baseUri(ConfigManager.get("base-url"))
                .when().get("/api/customers")
                .then().extract().response();
        ApiAssertion.assertCode(resp, 901);
        ApiAssertion.assertJsonPath(resp, "msg", "请求Token参数为空");
    }

    @Test(priority = 2, description = "902:伪造/篡改的 token 被拒(签名校验生效)")
    public void test902TokenTampered() {
        String real = TokenManager.token();
        // 改最后一段(签名),签名校验必挂
        String tampered = real.substring(0, real.length() - 4) + "XXXX";
        Response resp = businessRequestWith(tampered, "/api/customers");
        ApiAssertion.assertCode(resp, 902);
        ApiAssertion.assertJsonPath(resp, "msg", "请求Token有误");
    }

    @Test(priority = 3, description = "登录后 Redis 应写入 token 且 TTL 在约定窗口内(默认 30 分钟)")
    public void testRedisTokenWrittenWithTtl() {
        String token = doLogin();
        Object userId = JwtUtil.parseUserId(token);
        String key = RedisUtil.tokenKey(userId);

        Assert.assertTrue(RedisUtil.exists(key), "登录后 Redis 应写入 " + key);
        Assert.assertEquals(RedisUtil.get(key), token, "Redis 里的 token 应与登录返回的一致");

        long ttl = RedisUtil.ttl(key);
        // 默认 30 分钟(1800s);留出请求耗时的余量
        Assert.assertTrue(ttl > 1790 && ttl <= 1800,
                "未勾选记住我时 TTL 应约 1800s,实际 " + ttl);
    }

    @Test(priority = 4, description = "rememberMe=true 时 TTL 应为 7 天")
    public void testRememberMeLongTtl() {
        Response login = given()
                .config(ApiClient.UTF8_FORM_CONFIG)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .baseUri(ConfigManager.get("base-url"))
                .formParam(ConfigManager.get("auth.username-field", "username"), user())
                .formParam(ConfigManager.get("auth.password-field", "password"), pwd())
                .formParam("rememberMe", true)
                .when().post(ConfigManager.get("auth.login-path", "/login"))
                .then().extract().response();
        ApiAssertion.assertSuccess(login);
        String token = login.jsonPath().getString(ConfigManager.get("auth.token-path", "data"));

        long ttl = RedisUtil.ttl(RedisUtil.tokenKey(JwtUtil.parseUserId(token)));
        long week = 7L * 24 * 3600;
        Assert.assertTrue(ttl > week - 60 && ttl <= week,
                "记住我 TTL 应约 7 天(" + week + "s),实际 " + ttl);
    }

    @Test(priority = 5, description = "同一用户再次登录:Redis 里的 token 被最后一次登录覆写")
    public void testReloginOverwritesRedisToken() {
        String first = doLogin();
        String key = RedisUtil.tokenKey(JwtUtil.parseUserId(first));

        // dlyk 的 JWT 不含时间戳/jti,同一用户快速重登可能产生完全相同的 JWT(设计弱点,已记录),
        // 所以这里断言"Redis 值 == 最后一次登录的返回值",而不是两个 JWT 不相等。
        String second = doLogin();
        Assert.assertEquals(RedisUtil.get(key), second,
                "Redis 里的 token 应被最后一次登录覆写");

        // 模拟互踢语义:把 Redis 值改成"另一个会话"的 token,当前 token 立即 904
        RedisUtil.set(key, "simulated-other-session-token");
        ApiAssertion.assertCode(businessRequestWith(second, "/api/customers"), 904);
        ApiAssertion.assertJsonPath(businessRequestWith(second, "/api/customers"), "msg", "请求Token不匹配");

        // 恢复:重新登录拿回有效登录态
        TokenManager.invalidate();
        doLogin();
    }

    @Test(priority = 6, description = "登出后 Redis 删除 token,原 token 立即失效(903:key 不存在)")
    public void testLogoutInvalidatesToken() {
        String token = doLogin();

        Response logout = given()
                .baseUri(ConfigManager.get("base-url"))
                .header(ConfigManager.get("auth.header-name", "Authorization"), token)
                .when().post("/api/logout")
                .then().extract().response();
        ApiAssertion.assertSuccess(logout);

        Object userId = JwtUtil.parseUserId(token);
        Assert.assertFalse(RedisUtil.exists(RedisUtil.tokenKey(userId)),
                "登出后 Redis 应删除 token key");
        // TokenVerifyFilter:Redis 里查不到该用户的 token → 903(已过期),而不是 904(不匹配)
        ApiAssertion.assertCode(businessRequestWith(token, "/api/customers"), 903);
        ApiAssertion.assertJsonPath(businessRequestWith(token, "/api/customers"), "msg", "请求Token已过期");
    }

    @Test(priority = 7, description = "903:JWT 有效但 Redis 已过期(模拟 TTL 到期)应拒绝")
    public void test903RedisExpired() {
        String token = doLogin();
        Object userId = JwtUtil.parseUserId(token);

        // 模拟"过期":直接删掉 Redis key,等价于 TTL 到 0
        RedisUtil.del(RedisUtil.tokenKey(userId));
        Response resp = businessRequestWith(token, "/api/customers");
        ApiAssertion.assertCode(resp, 903);
        ApiAssertion.assertJsonPath(resp, "msg", "请求Token已过期");
    }

    private String doLogin() {
        Response resp = AuthApiService.login(user(), pwd());
        ApiAssertion.assertSuccess(resp);
        return resp.jsonPath().getString(ConfigManager.get("auth.token-path", "data"));
    }

    private Response businessRequestWith(String token, String path) {
        RequestSpecification req = given()
                .baseUri(ConfigManager.get("base-url"))
                .header(ConfigManager.get("auth.header-name", "Authorization"), token);
        return req.when().get(path).then().extract().response();
    }
}
