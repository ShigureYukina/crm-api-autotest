package com.crm.autotest.testcase;

import com.crm.autotest.core.ApiAssertion;
import com.crm.autotest.service.AuthApiService;
import com.crm.autotest.utils.TokenManager;
import com.crm.autotest.utils.YamlReader;

import io.restassured.response.Response;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * 用例层:登录模块。
 * 正常/异常场景用 YAML 数据驱动,一个方法跑多组参数,新增场景只加数据不改代码。
 */
public class LoginTest {

    @Test(dataProvider = "loginData", description = "登录功能:正常/异常场景数据驱动")
    public void testLogin(Map<String, Object> data) {
        Response response = AuthApiService.login(
                String.valueOf(data.get("username")),
                String.valueOf(data.get("password")));
        int expectedCode = Integer.parseInt(String.valueOf(data.get("expectedCode")));
        ApiAssertion.assertCode(response, expectedCode);
    }

    @DataProvider(name = "loginData")
    public Object[][] loginData() {
        return YamlReader.toDataProvider("data/login_data.yaml");
    }

    @Test(description = "登录态:能获取到 token,供后续业务接口使用")
    public void testTokenAvailable() {
        Assert.assertFalse(TokenManager.token().isEmpty(), "token 不应为空");
    }
}
