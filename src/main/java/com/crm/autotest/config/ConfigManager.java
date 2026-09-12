package com.crm.autotest.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * 工具层:配置中心。所有环境信息(baseUrl、账号、数据库、接口路径)统一从 config.yaml 读取,
 * 切换环境只改配置文件,不动代码。
 *
 * 环境变量覆盖:key 中的点号转下划线大写,如 base-url -> BASE_URL、db.url -> DB_URL。
 * 优先级:环境变量 > config.yaml,方便 CI(Jenkins 容器里把 localhost 换成 host.docker.internal)。
 */
public final class ConfigManager {

    private static final Map<String, Object> CONFIG = load();

    private ConfigManager() {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() {
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream("config.yaml")) {
            if (in == null) {
                throw new IllegalStateException("classpath 下找不到 config.yaml");
            }
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("加载 config.yaml 失败", e);
        }
    }

    /** 按 a.b.c 的点号路径取嵌套配置项,如 auth.username */
    @SuppressWarnings("unchecked")
    public static String get(String key) {
        // 环境变量优先:BASE_URL / DB_URL / REDIS_HOST 等
        String env = System.getenv(envName(key));
        if (env != null && !env.isBlank()) {
            return env;
        }
        Object val = CONFIG;
        for (String part : key.split("\\.")) {
            if (!(val instanceof Map)) {
                return null;
            }
            val = ((Map<String, Object>) val).get(part);
            if (val == null) {
                return null;
            }
        }
        return String.valueOf(val);
    }

    /** base-url -> BASE_URL,db.url -> DB_URL */
    private static String envName(String key) {
        return key.toUpperCase().replace('-', '_').replace('.', '_');
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }
}
