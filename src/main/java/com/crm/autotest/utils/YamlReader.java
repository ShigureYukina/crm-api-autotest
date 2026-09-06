package com.crm.autotest.utils;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 工具层:YAML 数据驱动。
 * 数据文件放在 src/test/resources/data 下,形如一组 Map,一个 Map 就是一条用例数据,
 * 直接转成 TestNG @DataProvider 的二维数组,实现"一套脚本跑多组参数"。
 */
public final class YamlReader {

    private YamlReader() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadList(String path) {
        try (InputStream in = YamlReader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("classpath 下找不到数据文件:" + path);
            }
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("读取数据文件失败:" + path, e);
        }
    }

    /** 每个 Map 作为一组用例数据,配合 @DataProvider 使用 */
    public static Object[][] toDataProvider(String path) {
        List<Map<String, Object>> rows = loadList(path);
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }
        return data;
    }
}
