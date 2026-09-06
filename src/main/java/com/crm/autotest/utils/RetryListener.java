package com.crm.autotest.utils;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 工具层:通过 IAnnotationTransformer 把重试器全局注入所有 @Test,
 * 用例代码里不需要逐个写 retryAnalyzer = RetryAnalyzer.class。在 testng.xml 中注册生效。
 */
public class RetryListener implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
