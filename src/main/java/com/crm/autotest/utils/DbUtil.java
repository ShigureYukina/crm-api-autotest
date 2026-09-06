package com.crm.autotest.utils;

import com.crm.autotest.config.ConfigManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具层:数据库操作,供落库断言使用。
 * 用 PreparedStatement 预编译防注入,参数用 ? 占位符传入。
 */
public final class DbUtil {

    private DbUtil() {
    }

    private static Connection connection() throws Exception {
        // 驱动可配置,便于特殊环境切换;默认 MySQL
        Class.forName(ConfigManager.get("db.driver", "com.mysql.cj.jdbc.Driver"));
        return DriverManager.getConnection(
                ConfigManager.get("db.url"),
                ConfigManager.get("db.username"),
                ConfigManager.get("db.password"));
    }

    /** 返回查询结果第一行第一列的数值(用于 SELECT COUNT(*) 类查询) */
    public static long count(String sql, Object... params) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("数据库查询失败:" + sql, e);
        }
    }

    /** 返回查询结果的第一行,key 为列名;无结果返回 null */
    public static Map<String, Object> queryOne(String sql, Object... params) {
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    return row;
                }
                return null;
            }
        } catch (Exception e) {
            throw new IllegalStateException("数据库查询失败:" + sql, e);
        }
    }

    private static void bindParams(PreparedStatement ps, Object... params) throws Exception {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
