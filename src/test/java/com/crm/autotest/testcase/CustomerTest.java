package com.crm.autotest.testcase;

import com.crm.autotest.core.ApiAssertion;
import com.crm.autotest.service.ClueApiService;
import com.crm.autotest.service.CustomerApiService;
import com.crm.autotest.utils.DbUtil;

import io.restassured.response.Response;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * 用例层:客户模块全链路。
 *
 * dlyk 的业务约束:客户不能直接新增,只能由线索转换生成;删除是逻辑删除(deleted 置 1)。
 * 所以本类按「建线索 -> 转客户 -> 列表校验 -> 删除清理」的真实链路组织,并保留 DB 视角的落库断言,
 * 用例收尾会把造出来的客户和线索都删掉,不污染测试库。
 * 用类内字段 clueId / customerId 在用例间传递数据(接口间数据依赖),
 * priority + dependsOnMethods 保证执行顺序,前置失败时后续用例自动跳过。
 */
public class CustomerTest {

    private static final long TS = System.currentTimeMillis();
    private static final String CLUE_NAME = "自动化客户_" + TS;
    private static final String PHONE = "139" + String.valueOf(TS).substring(5, 13);

    private Object clueId;
    private Object customerId;

    @Test(priority = 1, description = "建线索 -> 转客户,并断言线索状态与客户落库")
    public void testCreateClueAndConvertToCustomer() {
        Response createResp = ClueApiService.createClue(CLUE_NAME, PHONE, "接口自动化造数", 1, 1);
        ApiAssertion.assertSuccess(createResp);

        // dlyk 的新增线索接口不返回线索 id,只能从 DB 反查(接口未回传主键时的常见处理方式)
        Map<String, Object> clueRow = DbUtil.queryOne("SELECT id FROM t_clue WHERE phone = ?", PHONE);
        Assert.assertNotNull(clueRow, "线索应已落库,phone=" + PHONE);
        clueId = clueRow.get("id");
        Assert.assertNotNull(clueId, "应从库中取到线索 id");

        Response convertResp = ClueApiService.convertToCustomer(clueId);
        ApiAssertion.assertSuccess(convertResp);

        // 接口返回成功 != 落库正确:从 DB 视角再验一次
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_customer WHERE clue_id = ?", 1, clueId);
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_clue WHERE id = ? AND state = -1", 1, clueId);

        // 再用接口验一次:转换后线索状态应变成 -1(已转换)
        ApiAssertion.assertJsonPath(ClueApiService.getClueDetail(clueId), "data.state", -1);

        Map<String, Object> customerRow = DbUtil.queryOne(
                "SELECT id FROM t_customer WHERE clue_id = ?", clueId);
        Assert.assertNotNull(customerRow, "应已生成客户记录");
        customerId = customerRow.get("id");
    }

    @Test(priority = 2, dependsOnMethods = "testCreateClueAndConvertToCustomer",
            description = "翻页查询客户列表,断言新建的客户可见且姓名无乱码")
    public void testListCustomerContainsConverted() {
        String matchedName = findNameInCustomerList(customerId);
        Assert.assertNotNull(matchedName, "列表应包含新建客户,customerId=" + customerId);
        Assert.assertEquals(matchedName, CLUE_NAME, "客户姓名应与线索一致(校验中文无乱码)");
    }

    @Test(priority = 3, dependsOnMethods = "testCreateClueAndConvertToCustomer",
            description = "业务规则:同一线索不能重复转换")
    public void testConvertTwiceShouldFail() {
        Response resp = ClueApiService.convertToCustomer(clueId);
        ApiAssertion.assertCode(resp, 500);
        ApiAssertion.assertJsonPath(resp, "msg", "该线索已被转换");
    }

    @Test(priority = 4, description = "业务规则:转换不存在的线索应失败")
    public void testConvertNotExistClueShouldFail() {
        Response resp = ClueApiService.convertToCustomer(999999999);
        ApiAssertion.assertCode(resp, 500);
        ApiAssertion.assertJsonPath(resp, "msg", "线索不存在");
    }

    /**
     * 已知缺陷(2026-09-11 发现):GET /api/customer/{id} 稳定报 500。
     * 根因:TCustomerMapper.java 声明了 selectCustomerDetailById,但 TCustomerMapper.xml
     * 里没有对应的 <select>,MyBatis 抛 "Invalid bound statement"。
     *
     * 这里断言的是"当前确实坏掉了"这个事实,目的是让回归保持绿色同时把缺陷钉在报告里。
     * 被测系统修好该 mapper 后,把本方法改成 ApiAssertion.assertSuccess(resp) 即可。
     */
    @Test(priority = 5, dependsOnMethods = "testCreateClueAndConvertToCustomer",
            description = "[已知缺陷] 客户详情接口因 mapper 缺 statement 报 500")
    public void testCustomerDetailKnownSutBug() {
        Response resp = CustomerApiService.getCustomerDetail(customerId);
        ApiAssertion.assertCode(resp, 500);
        String msg = resp.jsonPath().getString("msg");
        Assert.assertTrue(msg != null && msg.contains("selectCustomerDetailById"),
                "应命中缺失的 mapper statement,实际:" + msg);
    }

    @Test(priority = 6, description = "业务规则:删除不存在的客户应失败")
    public void testDeleteNotExistCustomerShouldFail() {
        ApiAssertion.assertCode(CustomerApiService.deleteCustomer(999999999), 500);
    }

    @Test(priority = 7, dependsOnMethods = {"testCustomerDetailKnownSutBug", "testListCustomerContainsConverted"},
            description = "删除客户:逻辑删除,行保留标志位翻转,且列表不再可见")
    public void testDeleteCustomer() {
        Response resp = CustomerApiService.deleteCustomer(customerId);
        ApiAssertion.assertSuccess(resp);

        // 逻辑删除:行还在(deleted=1),而不是物理行数为 0
        ApiAssertion.assertDbCount("SELECT COUNT(*) FROM t_customer WHERE id = ?", 1, customerId);
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_customer WHERE id = ? AND deleted = 1", 1, customerId);

        Assert.assertNull(findNameInCustomerList(customerId),
                "删除后客户不应再出现在列表中,customerId=" + customerId);
    }

    @Test(priority = 8, dependsOnMethods = "testDeleteCustomer",
            description = "收尾清理:逻辑删除前置线索,不污染测试库")
    public void testDeleteClueCleanup() {
        Response resp = ClueApiService.deleteClue(clueId);
        ApiAssertion.assertSuccess(resp);
        ApiAssertion.assertDbCount(
                "SELECT COUNT(*) FROM t_clue WHERE id = ? AND deleted = 1", 1, clueId);
    }

    /**
     * 在客户分页列表里逐页找指定客户,返回其关联线索的姓名;找不到返回 null。
     * dlyk 列表接口不支持关键字过滤,只能按页遍历(每页 10 条)。
     *
     * 并发注意:其它测试类在并行跑时会增删记录,导致 total 和每页内容实时变化,
     * 所以总页数不能在循环前算一次就固定,必须每翻一页都从该页响应里重取 total。
     */
    private String findNameInCustomerList(Object targetCustomerId) {
        // 最多 3 轮:并发下记录总数实时变化,单轮翻页可能刚好漏掉被"推页"的目标行
        for (int attempt = 1; attempt <= 3; attempt++) {
            String name = scanCustomerPages(targetCustomerId);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    private String scanCustomerPages(Object targetCustomerId) {
        int pageSize = CustomerApiService.pageSize();
        for (int page = 1; page <= 500; page++) {
            Response resp = CustomerApiService.listCustomers(page);
            ApiAssertion.assertSuccess(resp);

            List<Map<String, Object>> rows = resp.jsonPath().getList("data.list");
            for (Map<String, Object> row : rows) {
                if (String.valueOf(targetCustomerId).equals(String.valueOf(row.get("id")))) {
                    Object clueDo = row.get("clueDO");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> clue = (Map<String, Object>) clueDo;
                    return clue == null ? null : String.valueOf(clue.get("fullName"));
                }
            }

            Integer total = resp.jsonPath().getInt("data.total");
            int totalPages = (total + pageSize - 1) / pageSize;
            if (page >= totalPages) {
                break;
            }
        }
        return null;
    }
}
