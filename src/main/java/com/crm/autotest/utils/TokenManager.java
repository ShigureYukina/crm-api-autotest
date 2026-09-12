package com.crm.autotest.utils;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.service.AuthApiService;

import io.restassured.response.Response;

/**
 * 工具层:登录 token 依赖处理,并发安全。
 * 全局只登录一次并缓存 token,后续所有请求复用,避免每个用例都重复登录;
 * token 失效(如被登出、Redis 过期)时可调 invalidate() 清缓存,下一次请求自动重登。
 *
 * 并发要点(配合 testng.xml 的 parallel="methods"):
 * - token 声明为 volatile,保证多线程读到的不是半初始化对象;
 * - 双检锁登录:同时只放一个线程去调登录接口,其余线程在锁外等 token 就绪;
 * - 登录失败会把失败状态也缓存(FAILED),避免极端情况下每个线程都去打一遍登录接口;
 *   下次 invalidate() 后重置为 NORMAL 再试。
 */
public final class TokenManager {

    private enum State { NORMAL, FAILED }

    private static volatile String token;
    private static volatile State state = State.NORMAL;

    private TokenManager() {
    }

    public static String token() {
        String current = token;
        if (current != null) {
            return current;
        }
        synchronized (TokenManager.class) {
            // 拿到锁后 double check:可能别的线程刚登录完
            if (token != null) {
                return token;
            }
            // 上一次登录失败过,没有人工 invalidate 就不再重试,防止打爆登录接口
            if (state == State.FAILED) {
                throw new IllegalStateException("登录曾失败且未恢复,请检查配置或调用 TokenManager.invalidate() 重试");
            }
            login();
            return token;
        }
    }

    private static void login() {
        try {
            Response response = AuthApiService.login(
                    ConfigManager.get("auth.username"),
                    ConfigManager.get("auth.password"));
            String tokenPath = ConfigManager.get("auth.token-path", "data.token");
            token = response.jsonPath().getString(tokenPath);
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException(
                        "登录失败,未从响应取到 token(JSONPath: " + tokenPath + "),响应:" + response.asString());
            }
            state = State.NORMAL;
        } catch (RuntimeException e) {
            state = State.FAILED;
            throw e;
        }
    }

    public static void invalidate() {
        synchronized (TokenManager.class) {
            token = null;
            state = State.NORMAL;
        }
    }
}
