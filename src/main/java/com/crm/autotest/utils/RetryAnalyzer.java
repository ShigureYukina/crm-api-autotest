package com.crm.autotest.utils;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * 工具层:失败自动重试。单个用例最多重试 2 次,用于吸收环境抖动
 * (网络超时、服务重启瞬间等),真实的功能失败重试后依然失败,不会被掩盖。
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRY = 2;
    private int count = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (count < MAX_RETRY) {
            count++;
            return true;
        }
        return false;
    }
}
