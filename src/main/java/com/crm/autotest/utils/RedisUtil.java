package com.crm.autotest.utils;

import com.crm.autotest.config.ConfigManager;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;

/**
 * 工具层:Redis 直连,做登录态的生命周期断言用。
 *
 * 背景:dlyk 的 token 过期不是 JWT 自身过期(JWT 里没有 exp claim),
 * 而是把 JWT 存在 Redis(dlyk:user:login:{userId}),每次请求比对。
 * 所以"token 是否过期"只有从 Redis 侧才能断言,TTL 是关键观测量。
 * 与被测系统同款 Lettuce 客户端,全局一个连接,进程退出时关闭。
 */
public final class RedisUtil {

    private static final RedisClient CLIENT = RedisClient.create(
            RedisURI.builder()
                    .withHost(ConfigManager.get("redis.host", "127.0.0.1"))
                    .withPort(Integer.parseInt(ConfigManager.get("redis.port", "6379")))
                    .withTimeout(Duration.ofSeconds(5))
                    .build());

    private RedisUtil() {
    }

    private static StatefulRedisConnection<String, String> connection() {
        return CLIENT.connect();
    }

    /** 指定 key 的剩余存活秒数;-2 表示 key 不存在,-1 表示无过期时间 */
    public static long ttl(String key) {
        try (StatefulRedisConnection<String, String> conn = connection()) {
            Long ttl = conn.sync().ttl(key);
            return ttl == null ? -2 : ttl;
        }
    }

    /** key 是否存在 */
    public static boolean exists(String key) {
        try (StatefulRedisConnection<String, String> conn = connection()) {
            Boolean exists = conn.sync().exists(key) > 0;
            return Boolean.TRUE.equals(exists);
        }
    }

    /** key 的原始值(dlyk 用 Jackson 序列化器存储,字符串值带 JSON 引号) */
    public static String getRaw(String key) {
        try (StatefulRedisConnection<String, String> conn = connection()) {
            return conn.sync().get(key);
        }
    }

    /**
     * key 的值并剥掉 JSON 序列化器加的引号。
     * dlyk 的 RedisTemplate 配了 Jackson2JsonRedisSerializer,字符串存储为 "value"(带引号),
     * 读回时框架自动反序列化;测试侧直读原始字节,需要手动剥引号才能与业务值比较。
     */
    public static String get(String key) {
        String raw = getRaw(key);
        if (raw != null && raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    /** 按 dlyk 的存储格式写入 JSON 字符串(模拟"另一个会话的 token 覆盖"用) */
    public static void set(String key, String value) {
        try (StatefulRedisConnection<String, String> conn = connection()) {
            conn.sync().set(key, "\"" + value + "\"");
        }
    }

    /** 删除 key(测试登出/过期场景后清理现场用) */
    public static void del(String key) {
        try (StatefulRedisConnection<String, String> conn = connection()) {
            conn.sync().del(key);
        }
    }

    /** dlyk 的 token key 规则:项目名:模块:功能:userId */
    public static String tokenKey(Object userId) {
        return "dlyk:user:login:" + userId;
    }

    /** 从 JWT 里解出 userId(token key 需要用,JWT 负载是 user 字段的 JSON) */
    public static Object parseUserIdFromToken(String token) {
        return com.crm.autotest.utils.JwtUtil.parseUserId(token);
    }

    /** 进程退出时释放客户端(供监听器调用,一般测试进程结束即退出,不调也可) */
    public static void shutdown() {
        CLIENT.shutdown();
    }
}
