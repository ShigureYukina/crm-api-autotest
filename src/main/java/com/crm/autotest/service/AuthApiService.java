package com.crm.autotest.service;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.core.ApiClient;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务层:登录模块接口封装。
 * 登录本身不带登录态,不走 ApiClient 的 token 逻辑;
 * 同时被 TokenManager(获取 token)和 LoginTest(登录用例)复用。
 *
 * dlyk 的登录是 Spring Security formLogin,收的是 form-urlencoded 的
 * loginAct/loginPwd,不是 JSON 的 username/password,所以这里用 formParam 提交。
 * 字段名同样收敛到 config.yaml,换系统时不用改代码。
 */
public final class AuthApiService {

    private AuthApiService() {
    }

    public static Response login(String username, String password) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put(ConfigManager.get("auth.username-field", "username"), username);
        form.put(ConfigManager.get("auth.password-field", "password"), password);
        return RestAssured.given()
                .config(ApiClient.UTF8_FORM_CONFIG)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .baseUri(ConfigManager.get("base-url"))
                .formParams(form)
                .log().all()
                .when()
                .post(ConfigManager.get("auth.login-path", "/login"))
                .then()
                .log().all()
                .extract()
                .response();
    }
}
