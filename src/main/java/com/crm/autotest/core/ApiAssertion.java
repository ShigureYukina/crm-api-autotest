package com.crm.autotest.core;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.utils.DbUtil;

import io.qameta.allure.Allure;
import io.restassured.response.Response;

import java.util.Arrays;

/**
 * 工具层:统一断言封装,分三类——
 * 1. 业务码断言:{"code":200,"msg":"..."} 里的 code;
 * 2. 响应字段断言:按 JSONPath 比对任意字段;
 * 3. 数据库断言:接口返回成功不代表落库正确,从 DB 视角再验一次。
 * 断言过程写入 Allure step,报告里能直接看到"期望 vs 实际"。
 */
public final class ApiAssertion {

    private ApiAssertion() {
    }

    /** 业务码断言,字段路径由 config.yaml 的 assert.code-path 配置 */
    public static void assertCode(Response response, int expected) {
        String codePath = ConfigManager.get("assert.code-path", "code");
        Integer actual = response.jsonPath().getInt(codePath);
        Allure.step(String.format("断言业务码 %s:期望=%d,实际=%s", codePath, expected, actual));
        if (actual == null || actual != expected) {
            throw new AssertionError(String.format(
                    "业务码断言失败,期望 %d,实际 %s,响应:%s", expected, actual, response.asString()));
        }
    }

    /** 断言接口处理成功(成功业务码由 assert.success-code 配置) */
    public static void assertSuccess(Response response) {
        assertCode(response, Integer.parseInt(ConfigManager.get("assert.success-code", "200")));
    }

    /** 响应字段断言:按 JSONPath 比对 */
    public static void assertJsonPath(Response response, String jsonPath, Object expected) {
        Object actual = response.jsonPath().get(jsonPath);
        Allure.step(String.format("断言字段 %s:期望=%s,实际=%s", jsonPath, expected, actual));
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            throw new AssertionError(String.format(
                    "字段断言失败 %s,期望 %s,实际 %s,响应:%s", jsonPath, expected, actual, response.asString()));
        }
    }

    /** 数据库断言:sql 需为 COUNT 查询,断言行数等于 expected,params 为 ? 占位符参数 */
    public static void assertDbCount(String sql, long expected, Object... params) {
        if (!"true".equalsIgnoreCase(ConfigManager.get("db.enabled", "true"))) {
            Allure.step("数据库断言已通过 db.enabled=false 关闭,跳过:" + sql);
            return;
        }
        long actual = DbUtil.count(sql, params);
        Allure.step(String.format("数据库断言:期望 %d 行,实际 %d 行,SQL=%s", expected, actual, sql));
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "数据库断言失败,期望 %d 行,实际 %d 行,SQL=%s,参数=%s",
                    expected, actual, sql, Arrays.toString(params)));
        }
    }
}
