package com.crm.autotest.testcase;

import com.crm.autotest.core.ApiAssertion;
import com.crm.autotest.service.ActivityApiService;
import com.crm.autotest.utils.DbUtil;

import io.restassured.response.Response;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * 用例层:活动模块全链路(增→查→列表→改→删→批量删)。
 * dlyk 的活动是物理删除,与客户/线索的逻辑删除不同,断言按行数清零组织。
 * 新增接口不回传主键,统一用 DbUtil 按名称反查 id。
 */
public class ActivityTest {

    private static final long TS = System.currentTimeMillis();
    private static final String NAME = "自动化活动_" + TS;

    private Object activityId;

    @Test(priority = 1, description = "新增活动,断言落库(名称/费用/描述)")
    public void testCreateActivity() {
        Response resp = ActivityApiService.createActivity(NAME, "8888.88", "接口自动化创建");
        ApiAssertion.assertSuccess(resp);

        // 新增不回传 id,按名称反查(同手机号反查线索的模式,这里用唯一名称)
        java.util.Map<String, Object> row = DbUtil.queryOne(
                "SELECT id FROM t_activity WHERE name = ?", NAME);
        Assert.assertNotNull(row, "活动应已落库,name=" + NAME);
        activityId = row.get("id");

        // 落库字段断言:费用与描述
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_activity WHERE name = ? AND cost = 8888.88", 1, NAME);
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_activity WHERE name = ? AND description = ?", 1, NAME, "接口自动化创建");
    }

    @Test(priority = 2, dependsOnMethods = "testCreateActivity",
            description = "活动详情接口返回创建的数据")
    public void testActivityDetail() {
        Response resp = ActivityApiService.getActivityDetail(activityId);
        ApiAssertion.assertSuccess(resp);
        ApiAssertion.assertJsonPath(resp, "data.name", NAME);
        ApiAssertion.assertJsonPath(resp, "data.description", "接口自动化创建");
    }

    @Test(priority = 3, dependsOnMethods = "testCreateActivity",
            description = "分页列表应能翻页找到新建活动")
    public void testActivityListContains() {
        // dependsOnMethods 只约束前置成功,不把本用例排到 testDeleteActivity 之前;
        // methods 级并发下删除用例可能先跑,导致这里翻页找不到 → 挂到 delete 链后面
        boolean found = scanActivityPagesFor(NAME);
        Assert.assertTrue(found, "全部分页中应包含新建活动:" + NAME);
    }

    @Test(priority = 4, dependsOnMethods = "testActivityDetail",
            description = "修改活动名称,断言接口与落库一致")
    public void testUpdateActivity() {
        String newName = NAME + "_改";
        Response resp = ActivityApiService.updateActivity(activityId, newName);
        ApiAssertion.assertSuccess(resp);
        ApiAssertion.assertJsonPath(ActivityApiService.getActivityDetail(activityId), "data.name", newName);
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_activity WHERE id = ? AND name = ?", 1, activityId, newName);
    }

    @Test(priority = 5, dependsOnMethods = "testUpdateActivity",
            description = "删除不存在的活动应失败")
    public void testDeleteNotExistActivityShouldFail() {
        ApiAssertion.assertCode(ActivityApiService.deleteActivity(999999999), 500);
    }

    @Test(priority = 6, dependsOnMethods = {"testUpdateActivity", "testActivityListContains"},
            description = "删除活动(物理删除),断言行数清零")
    public void testDeleteActivity() {
        Response resp = ActivityApiService.deleteActivity(activityId);
        ApiAssertion.assertSuccess(resp);
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_activity WHERE id = ?", 0, activityId);
    }

    /**
     * 逐页扫描活动列表找目标名称。物理删除后活动行会消失,
     * 所以本方法只在"活动还存在"的窗口内可靠;调用方应保证此刻未删除。
     */
    private boolean scanActivityPagesFor(String targetName) {
        int pageSize = 10;
        // 最多 3 轮:并发下记录总数实时变化,单轮翻页可能刚好漏掉被"推页"的目标行
        for (int attempt = 1; attempt <= 3; attempt++) {
            for (int page = 1; page <= 200; page++) {
                Response resp = ActivityApiService.listActivities(page);
                ApiAssertion.assertSuccess(resp);
                if (resp.asString().contains(targetName)) {
                    return true;
                }
                Integer total = resp.jsonPath().getInt("data.total");
                int totalPages = (total + pageSize - 1) / pageSize;
                if (page >= totalPages) {
                    break;
                }
            }
        }
        return false;
    }

    /**
     * 批量删除:建两条、批删两条,验证 ids 逗号分隔的批量端点。
     */
    @Test(priority = 7, description = "批量删除活动:一次删两条")
    public void testBatchDeleteActivities() {
        String name1 = "自动化批量_" + TS + "_a";
        String name2 = "自动化批量_" + TS + "_b";
        ApiAssertion.assertSuccess(ActivityApiService.createActivity(name1, "1.00", "批量"));
        ApiAssertion.assertSuccess(ActivityApiService.createActivity(name2, "2.00", "批量"));

        Object id1 = DbUtil.queryOne("SELECT id FROM t_activity WHERE name = ?", name1).get("id");
        Object id2 = DbUtil.queryOne("SELECT id FROM t_activity WHERE name = ?", name2).get("id");

        Response resp = ActivityApiService.batchDeleteActivities(id1 + "," + id2);
        ApiAssertion.assertSuccess(resp);
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_activity WHERE id IN (?, ?)", 0, id1, id2);
    }
}
