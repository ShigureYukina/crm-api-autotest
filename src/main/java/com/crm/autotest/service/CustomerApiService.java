package com.crm.autotest.service;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.core.ApiClient;

import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务层:CRM 客户模块接口封装。一个方法对应一个接口,用例层只调方法、不拼报文。
 *
 * dlyk 的客户接口比较特殊,对接时注意三点:
 * 1. 没有"直接新增客户"的接口,客户只能由线索转换产生(见 ClueApiService);
 * 2. 没有修改/删除客户的接口;
 * 3. 列表接口只支持按 current 翻页,不支持关键字过滤,所以校验"客户是否出现"要翻页找。
 */
public final class CustomerApiService {

    private CustomerApiService() {
    }

    /** 客户分页列表,dlyk 只有 current 一个参数,每页固定 10 条 */
    public static Response listCustomers(int current) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("current", current);
        return ApiClient.get(ConfigManager.get("api.customer-list-path", "/api/customers"), query);
    }

    /** 客户详情 */
    public static Response getCustomerDetail(Object id) {
        String path = ConfigManager.get("api.customer-detail-path", "/api/customer") + "/" + id;
        return ApiClient.get(path, null);
    }

    /** 逻辑删除客户(行保留,deleted 置 1) */
    public static Response deleteCustomer(Object id) {
        String path = ConfigManager.get("api.customer-detail-path", "/api/customer") + "/" + id;
        return ApiClient.delete(path);
    }

    /** 每页条数,用于翻页遍历 */
    public static int pageSize() {
        return Integer.parseInt(ConfigManager.get("api.page-size", "10"));
    }
}
