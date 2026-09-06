package com.crm.autotest.utils;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.service.AuthApiService;

import io.restassured.response.Response;

/**
 * 工具层:登录 token 依赖处理。
 * 全局只登录一次并缓存 token,后续所有请求复用,避免每个用例都重复登录。
 * token 过期(401)时可调 invalidate() 清缓存,下一次请求会自动重新登录。
 */
public final class TokenManager {

    private static volatile String token;

    private TokenManager() {
    }

    public static String token() {
        if (token == null) {
            synchronized (TokenManager.class) {
                if (token == null) {
                    login();
                }
            }
        }
        return token;
    }

    private static void login() {
        Response response = AuthApiService.login(
                ConfigManager.get("auth.username"),
                ConfigManager.get("auth.password"));
        String tokenPath = ConfigManager.get("auth.token-path", "data.token");
        token = response.jsonPath().getString(tokenPath);
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException(
                    "登录失败,未从响应取到 token(JSONPath: " + tokenPath + "),响应:" + response.asString());
        }
    }

    public static void invalidate() {
        token = null;
    }
}
