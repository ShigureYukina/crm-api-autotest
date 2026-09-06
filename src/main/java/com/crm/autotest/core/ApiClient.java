package com.crm.autotest.core;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.utils.TokenManager;

import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * 工具层:统一请求封装。
 * 所有需要登录态的接口请求都从这里发出,统一处理 baseUrl、token 请求头、Content-Type,
 * 并把请求/响应作为附件挂到 Allure 报告,失败时可直接看到报文。
 */
public final class ApiClient {

    private static final RequestSpecification BASE_SPEC = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .build();

    private ApiClient() {
    }

    public static Response get(String path, Map<String, Object> queryParams) {
        return execute(Method.GET, path, null, queryParams);
    }

    public static Response post(String path, Object body) {
        return execute(Method.POST, path, body, null);
    }

    public static Response put(String path, Object body) {
        return execute(Method.PUT, path, body, null);
    }

    public static Response delete(String path) {
        return execute(Method.DELETE, path, null, null);
    }

    public static Response execute(Method method, String path, Object body,
                                   Map<String, Object> queryParams) {
        String headerName = ConfigManager.get("auth.header-name", "Authorization");
        String headerPrefix = ConfigManager.get("auth.header-prefix", "Bearer ");

        RequestSpecification request = RestAssured.given()
                .spec(BASE_SPEC)
                .baseUri(ConfigManager.get("base-url"))
                .header(headerName, headerPrefix + TokenManager.token())
                .log().all();
        if (queryParams != null) {
            request.queryParams(queryParams);
        }
        if (body != null) {
            request.body(body);
        }

        long start = System.currentTimeMillis();
        Response response = request.when()
                .request(method, path)
                .then()
                .log().all()
                .extract()
                .response();
        long cost = System.currentTimeMillis() - start;

        Allure.addAttachment("请求信息",
                method + " " + path + "\nquery: " + queryParams + "\nbody: " + body);
        Allure.addAttachment("响应信息(耗时 " + cost + "ms)", response.asString());
        return response;
    }
}
