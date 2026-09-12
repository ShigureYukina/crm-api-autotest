package com.crm.autotest.service;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.core.ApiClient;

import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务层:活动模块接口封装(dlyk 的市场活动)。
 * dlyk 的活动增改收表单参数(ActivityQuery 无 @RequestBody),删除是物理删除。
 */
public final class ActivityApiService {

    private ActivityApiService() {
    }

    /** 新增活动,返回 R.OK 不带 id,主键需从 DB 反查 */
    public static Response createActivity(String name, String cost, String description) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", name);
        form.put("cost", cost);
        form.put("description", description);
        return ApiClient.postForm(ConfigManager.get("api.activity-path", "/api/activity"), form);
    }

    /** 活动列表(分页,支持 ActivityQuery 的表单过滤参数) */
    public static Response listActivities(int current) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("current", current);
        return ApiClient.get(ConfigManager.get("api.activity-list-path", "/api/activitys"), query);
    }

    /** 活动详情 */
    public static Response getActivityDetail(Object id) {
        String path = ConfigManager.get("api.activity-path", "/api/activity") + "/" + id;
        return ApiClient.get(path, null);
    }

    /** 修改活动 */
    public static Response updateActivity(Object id, String newName) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("id", id);
        form.put("name", newName);
        return ApiClient.putForm(ConfigManager.get("api.activity-path", "/api/activity"), form);
    }

    /** 删除活动(物理删除) */
    public static Response deleteActivity(Object id) {
        String path = ConfigManager.get("api.activity-path", "/api/activity") + "/" + id;
        return ApiClient.delete(path);
    }

    /** 批量删除活动(ids 形如 "1,2,3") */
    public static Response batchDeleteActivities(String ids) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("ids", ids);
        return ApiClient.execute(io.restassured.http.Method.DELETE,
                ConfigManager.get("api.activity-path", "/api/activity") + "/batch",
                null, query);
    }
}
