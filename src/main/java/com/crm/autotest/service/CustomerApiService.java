package com.crm.autotest.service;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.core.ApiClient;

import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务层:CRM 客户模块接口封装。一个方法对应一个接口,用例层只调方法、不拼报文。
 * 对接自己的 CRM 时,重点改这里的接口路径(config.yaml 的 api 段)和请求字段名。
 */
public final class CustomerApiService {

    private CustomerApiService() {
    }

    /** 新增客户 */
    public static Response createCustomer(String name, String phone, String remark) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerName", name);
        body.put("phone", phone);
        body.put("remark", remark);
        return ApiClient.post(ConfigManager.get("api.customer-path", "/crm/customer"), body);
    }

    /** 分页/关键字查询客户列表 */
    public static Response listCustomers(String keyword) {
        Map<String, Object> query = new HashMap<>();
        query.put("pageNum", "1");
        query.put("pageSize", "10");
        if (keyword != null && !keyword.isEmpty()) {
            query.put("keyword", keyword);
        }
        return ApiClient.get(ConfigManager.get("api.customer-list-path", "/crm/customer/list"), query);
    }

    /** 修改客户 */
    public static Response updateCustomer(Object id, String newName) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("customerName", newName);
        return ApiClient.put(ConfigManager.get("api.customer-path", "/crm/customer"), body);
    }

    /** 删除客户 */
    public static Response deleteCustomer(Object id) {
        return ApiClient.delete(ConfigManager.get("api.customer-path", "/crm/customer") + "/" + id);
    }
}
