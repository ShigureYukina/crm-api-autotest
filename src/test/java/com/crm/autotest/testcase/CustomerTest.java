package com.crm.autotest.testcase;

import com.crm.autotest.core.ApiAssertion;
import com.crm.autotest.service.CustomerApiService;

import io.restassured.response.Response;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * 用例层:客户模块增删改查全链路。
 * 用类内字段 customerId 在用例间传递数据(接口间数据依赖),
 * priority + dependsOnMethods 保证执行顺序且前置失败时后续用例跳过。
 */
public class CustomerTest {

    private String customerName = "自动化客户_" + System.currentTimeMillis();
    private Object customerId;

    @Test(priority = 1, description = "新增客户,并断言数据库落库成功")
    public void testCreateCustomer() {
        Response response = CustomerApiService.createCustomer(customerName, "13800001111", "接口自动化创建");
        ApiAssertion.assertSuccess(response);
        customerId = response.jsonPath().get("data.id");

        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM crm_customer WHERE customer_name = ?", 1, customerName);
    }

    @Test(priority = 2, dependsOnMethods = "testCreateCustomer",
            description = "分页查询,断言新增的客户在列表中可见")
    public void testListCustomer() {
        Response response = CustomerApiService.listCustomers(customerName);
        ApiAssertion.assertSuccess(response);
        Assert.assertTrue(response.asString().contains(customerName), "列表应包含新增客户:" + customerName);
    }

    @Test(priority = 3, dependsOnMethods = "testCreateCustomer", description = "修改客户名称")
    public void testUpdateCustomer() {
        String newName = customerName + "_改";
        Response response = CustomerApiService.updateCustomer(customerId, newName);
        ApiAssertion.assertSuccess(response);

        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM crm_customer WHERE customer_name = ?", 1, newName);
        customerName = newName;
    }

    @Test(priority = 4, dependsOnMethods = "testUpdateCustomer",
            description = "删除客户,并断言数据库已删除")
    public void testDeleteCustomer() {
        Response response = CustomerApiService.deleteCustomer(customerId);
        ApiAssertion.assertSuccess(response);

        // 若你的 CRM 是逻辑删除,这里应改为断言 is_deleted/deleted 标志位,而不是行数为 0
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM crm_customer WHERE customer_name = ?", 0, customerName);
    }
}
