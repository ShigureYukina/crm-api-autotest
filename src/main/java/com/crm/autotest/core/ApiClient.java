package com.crm.autotest.core;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.utils.TokenManager;

import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
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

    /**
     * form-urlencoded 请求的统一编码配置。
     * RestAssured 默认按 ISO-8859-1 编码表单参数,中文(如线索姓名)会被服务端解成乱码,
     * 这里必须显式指定 UTF-8。登录接口(AuthApiService)也复用这个配置。
     */
    public static final RestAssuredConfig UTF8_FORM_CONFIG = RestAssured.config()
            .encoderConfig(EncoderConfig.encoderConfig().defaultContentCharset("UTF-8"));

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

    /** 带登录态的 form-urlencoded 提交(dlyk 的 /api/clue 新增、修改用的是表单参数而非 JSON) */
    public static Response postForm(String path, Map<String, Object> formParams) {
        return executeForm(Method.POST, path, formParams);
    }

    public static Response putForm(String path, Map<String, Object> formParams) {
        return executeForm(Method.PUT, path, formParams);
    }

    private static Response executeForm(Method method, String path, Map<String, Object> formParams) {
        RequestSpecification request = RestAssured.given()
                .config(UTF8_FORM_CONFIG)
                .baseUri(ConfigManager.get("base-url"))
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header(headerName(), headerPrefix() + TokenManager.token())
                .formParams(formParams)
                .log().all();
        return send(method, path, request, formParams);
    }

    public static Response execute(Method method, String path, Object body,
                                   Map<String, Object> queryParams) {
        RequestSpecification request = RestAssured.given()
                .spec(BASE_SPEC)
                .baseUri(ConfigManager.get("base-url"))
                .header(headerName(), headerPrefix() + TokenManager.token())
                .log().all();
        if (queryParams != null) {
            request.queryParams(queryParams);
        }
        if (body != null) {
            request.body(body);
        }
        return send(method, path, request, body != null ? body : queryParams);
    }

    private static Response send(Method method, String path, RequestSpecification request, Object payload) {
        long start = System.currentTimeMillis();
        Response response = request.when()
                .request(method, path)
                .then()
                .log().all()
                .extract()
                .response();
        long cost = System.currentTimeMillis() - start;

        Allure.addAttachment("请求信息", method + " " + path + "\npayload: " + payload);
        Allure.addAttachment("响应信息(耗时 " + cost + "ms)", response.asString());
        return response;
    }

    private static String headerName() {
        return ConfigManager.get("auth.header-name", "Authorization");
    }

    /** token 请求头前缀。dlyk 是裸 JWT,配置成空串;Bearer 方案则配 "Bearer " */
    private static String headerPrefix() {
        return ConfigManager.get("auth.header-prefix", "Bearer ");
    }
}
