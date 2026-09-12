package com.crm.autotest.testcase;

import com.crm.autotest.core.ApiAssertion;
import com.crm.autotest.service.ClueApiService;
import com.crm.autotest.service.TranApiService;
import com.crm.autotest.utils.DbUtil;

import io.restassured.response.Response;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * 用例层:交易模块全链路。
 *
 * 交易挂在客户下,而客户只能由线索转换而来,所以本类前置是完整业务链:
 * 建线索 → 转客户 → 建交易 → 改阶段(自动写历史) → 查历史轨迹 → 加备注 → 查备注。
 * 交易阶段(stage)取字典 t_dic_value(type_code='stage')的 id:12=创建交易,37=产品检验,42=付款成交。
 * 收尾清理:交易无删除接口(业务设计),通过逻辑删除客户与线索让交易从列表消失。
 */
public class TranTest {

    private static final long TS = System.currentTimeMillis();
    private static final String CLUE_NAME = "自动化交易_" + TS;
    private static final String PHONE = "137" + String.valueOf(TS).substring(5, 13);

    private Object clueId;
    private Object customerId;
    private Object tranId;

    @Test(priority = 1, description = "前置链路:建线索→转客户,为交易准备挂靠点")
    public void prepareCustomer() {
        ApiAssertion.assertSuccess(ClueApiService.createClue(CLUE_NAME, PHONE, "交易前置", 1, 1));
        Map<String, Object> clueRow = DbUtil.queryOne("SELECT id FROM t_clue WHERE phone = ?", PHONE);
        Assert.assertNotNull(clueRow, "线索应已落库,phone=" + PHONE);
        clueId = clueRow.get("id");

        ApiAssertion.assertSuccess(ClueApiService.convertToCustomer(clueId));
        Map<String, Object> custRow = DbUtil.queryOne(
                "SELECT id FROM t_customer WHERE clue_id = ? AND deleted = 0", clueId);
        Assert.assertNotNull(custRow, "应已生成客户记录");
        customerId = custRow.get("id");
    }

    @Test(priority = 2, dependsOnMethods = "prepareCustomer",
            description = "创建交易:tranNo 后端生成,初始阶段与金额落库正确")
    public void testCreateTran() {
        Response resp = TranApiService.createTran(customerId, 6666.66, 12, "接口自动化创建");
        ApiAssertion.assertSuccess(resp);

        Map<String, Object> row = DbUtil.queryOne(
                "SELECT id, tran_no FROM t_tran WHERE customer_id = ? ORDER BY id DESC LIMIT 1", customerId);
        Assert.assertNotNull(row, "交易应已落库");
        tranId = row.get("id");
        Assert.assertTrue(String.valueOf(row.get("tran_no")).startsWith("TRAN"),
                "tranNo 应由后端生成且以 TRAN 开头,实际:" + row.get("tran_no"));

        // 初始阶段与金额
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_tran WHERE id = ? AND stage = 12 AND money = 6666.66", 1, tranId);
    }

    @Test(priority = 3, dependsOnMethods = "testCreateTran",
            description = "交易详情:customerId/tranNo/money/stage 与落库一致")
    public void testTranDetail() {
        Response resp = TranApiService.getTranDetail(tranId);
        ApiAssertion.assertSuccess(resp);
        ApiAssertion.assertJsonPath(resp, "data.customerId", customerId);
        ApiAssertion.assertJsonPath(resp, "data.money", 6666.66);
        ApiAssertion.assertJsonPath(resp, "data.stage", 12);
    }

    @Test(priority = 4, dependsOnMethods = "testTranDetail",
            description = "修改阶段:接口成功且自动追加交易历史(阶段流转 12→37→42)")
    public void testUpdateTranStage() {
        ApiAssertion.assertSuccess(TranApiService.updateTranStage(tranId, 37, 6666.66));
        ApiAssertion.assertSuccess(TranApiService.updateTranStage(tranId, 42, 6666.66));

        // 当前阶段
        ApiAssertion.assertJsonPath(TranApiService.getTranDetail(tranId), "data.stage", 42);

        // 历史轨迹:创建时 1 条 + 改阶段 2 条 = 3 条,且阶段顺序为 12,37,42
        Response history = TranApiService.getTranHistory(tranId);
        ApiAssertion.assertSuccess(history);
        List<Map<String, Object>> rows = history.jsonPath().getList("data");
        Assert.assertEquals(rows.size(), 3, "交易历史应有 3 条(创建+2 次改阶段)");
        for (int i = 0; i < rows.size(); i++) {
            int expectedStage = (i == 0) ? 12 : (i == 1 ? 37 : 42);
            Assert.assertEquals(
                    String.valueOf(rows.get(i).get("stage")), String.valueOf(expectedStage),
                    "第 " + (i + 1) + " 条历史的阶段应为 " + expectedStage);
        }
    }

    @Test(priority = 5, dependsOnMethods = "testUpdateTranStage",
            description = "交易备注:新增后分页列表能查回内容本身(防'字段名不匹配静默丢内容')")
    public void testAddTranRemark() {
        String noteContent = "自动化备注_" + TS;
        ApiAssertion.assertSuccess(TranApiService.addTranRemark(tranId, noteContent));

        Response list = TranApiService.listTranRemarks(1, tranId);
        ApiAssertion.assertSuccess(list);
        // 不能只看 code=200:曾实测传错字段名(remark≠noteContent)时接口 200 但内容落库为 NULL
        Assert.assertTrue(list.asString().contains(noteContent),
                "备注列表应能查回新增的备注内容:" + noteContent);
    }

    @Test(priority = 6, description = "业务规则:给不存在的客户创建交易应失败")
    public void testCreateTranForNotExistCustomerShouldFail() {
        // 外键约束:customer_id 引用 t_customer,不存在 → 数据库操作失败
        Response resp = TranApiService.createTran(999999999, 1.00, 12, "应失败");
        ApiAssertion.assertCode(resp, 500);
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        // 交易没有删除接口,收尾把挂靠的客户和线索逻辑删除,让测试数据从业务列表消失
        if (customerId != null) {
            try {
                ClueApiService.deleteClue(clueId);
            } catch (Exception ignored) {
            }
        }
    }
}
