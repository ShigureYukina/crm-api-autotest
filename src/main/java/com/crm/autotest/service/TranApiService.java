package com.crm.autotest.service;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.core.ApiClient;

import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务层:交易模块接口封装(dlyk 的交易/商机)。
 * 与活动不同,dlyk 的交易增改收 JSON(@RequestBody TranQuery),
 * 交易编号(tranNo)由后端自动生成,阶段(stage)取字典 t_dic_value(type_code=stage)的 id。
 */
public final class TranApiService {

    private TranApiService() {
    }

    /** 创建交易(挂在某客户下),返回 R.OK 不带 id */
    public static Response createTran(Object customerId, double money, int stage, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("money", money);
        body.put("stage", stage);
        body.put("description", description);
        return ApiClient.post(ConfigManager.get("api.tran-path", "/api/tran"), body);
    }

    /** 交易列表(分页) */
    public static Response listTrans(int current) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("current", current);
        return ApiClient.get(ConfigManager.get("api.tran-list-path", "/api/trans"), query);
    }

    /** 交易详情(tranNo/customerId/money/stage) */
    public static Response getTranDetail(Object id) {
        String path = ConfigManager.get("api.tran-path", "/api/tran") + "/" + id;
        return ApiClient.get(path, null);
    }

    /** 修改交易阶段,后端会自动追加一条交易历史 */
    public static Response updateTranStage(Object id, int stage, double money) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("stage", stage);
        body.put("money", money);
        return ApiClient.put(ConfigManager.get("api.tran-stage-path", "/api/tran/stage"), body);
    }

    /** 交易历史(按创建时间正序),用于断言阶段流转轨迹 */
    public static Response getTranHistory(Object tranId) {
        String path = ConfigManager.get("api.tran-history-path", "/api/tran/history") + "/" + tranId;
        return ApiClient.get(path, null);
    }

    /** 给交易添加备注(字段名是 noteContent,传错名字后端会静默丢内容) */
    public static Response addTranRemark(Object tranId, String noteContent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tranId", tranId);
        body.put("noteContent", noteContent);
        return ApiClient.post(ConfigManager.get("api.tran-remark-path", "/api/tran/remark"), body);
    }

    /** 交易备注分页 */
    public static Response listTranRemarks(int current, Object tranId) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("current", current);
        query.put("tranId", tranId);
        return ApiClient.get(ConfigManager.get("api.tran-remark-path", "/api/tran/remark"), query);
    }
}
