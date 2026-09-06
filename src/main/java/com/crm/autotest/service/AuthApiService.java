package com.crm.autotest.service;

import com.crm.autotest.config.ConfigManager;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务层:登录模块接口封装。
 * 登录本身不带登录态,直接走 RestAssured 原生请求;
 * 同时被 TokenManager(获取 token)和 LoginTest(登录用例)复用。
 */
public final class AuthApiService {

    private AuthApiService() {
    }

    public static Response login(String username, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .baseUri(ConfigManager.get("base-url"))
                .body(body)
                .log().all()
                .when()
                .post(ConfigManager.get("auth.login-path", "/login"))
                .then()
                .log().all()
                .extract()
                .response();
    }
}
