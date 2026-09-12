package com.crm.autotest.service;

import com.crm.autotest.config.ConfigManager;
import com.crm.autotest.core.ApiClient;

import io.restassured.response.Response;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务层:线索模块接口封装。
 *
 * dlyk 里客户不能直接新增,只能由线索转换而来,所以客户链路的用例必须先用这里的
 * createClue + convertToCustomer 造出前置数据。一个方法对应一个接口,用例层不拼报文。
 */
public final class ClueApiService {

    private ClueApiService() {
    }

    /**
     * 新增线索。dlyk 的 /api/clue 收的是表单参数(ClueQuery 没用 @RequestBody)。
     *
     * @param source 线索来源字典值 id;@param state 线索状态字典值 id(1=待跟进这类有效状态)
     */
    public static Response createClue(String fullName, String phone, String description,
                                      int state, int source) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("fullName", fullName);
        form.put("phone", phone);
        form.put("description", description);
        form.put("state", state);
        form.put("source", source);
        return ApiClient.postForm(ConfigManager.get("api.clue-path", "/api/clue"), form);
    }

    /** 线索转客户:线索状态会被置为 -1,同时生成一条 t_customer 记录 */
    public static Response convertToCustomer(Object clueId) {
        String path = ConfigManager.get("api.clue-convert-path", "/api/clue/convert") + "/" + clueId;
        return ApiClient.post(path, new HashMap<String, Object>());
    }

    /** 线索详情 */
    public static Response getClueDetail(Object clueId) {
        String path = ConfigManager.get("api.clue-detail-path", "/api/clue/detail") + "/" + clueId;
        return ApiClient.get(path, null);
    }

    /** 逻辑删除线索(deleted 置 1),用例收尾清理数据用 */
    public static Response deleteClue(Object clueId) {
        String path = ConfigManager.get("api.clue-path", "/api/clue") + "/" + clueId;
        return ApiClient.delete(path);
    }
}
