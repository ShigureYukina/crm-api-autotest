package com.crm.autotest.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 工具层:极简 JWT 解析(只解不改、不验签)。
 * 测试框架只需要从 token 里读出 userId 来拼 Redis key,不需要校验签名——
 * 签名校验是被测系统 TokenVerifyFilter 的职责,测试侧重复实现反而失真。
 *
 * 注意:dlyk 的 JWT 负载形如 {"user":"{\"id\":1,...}"},
 * user 的值是被转义过的 JSON 字符串,所以要先把 \" 还原成 " 再取字段。
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    /** 从 JWT 负载的 user JSON 里取 id;解析失败抛异常,由用例决定怎么处理 */
    public static Object parseUserId(String token) {
        String payload = decodePayload(token);

        int begin = payload.indexOf("\"user\":\"");
        if (begin < 0) {
            throw new IllegalStateException("JWT 负载里没有 user 字段:" + abbreviate(payload));
        }
        begin += "\"user\":\"".length();

        // 逐字符扫描到 value 的收尾引号(跳过转义序列)
        StringBuilder escaped = new StringBuilder();
        for (int i = begin; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (c == '\\' && i + 1 < payload.length()) {
                escaped.append(c).append(payload.charAt(++i));
            } else if (c == '"') {
                break;
            } else {
                escaped.append(c);
            }
        }
        String userJson = escaped.toString().replace("\\\"", "\"").replace("\\\\", "\\");

        int idKey = userJson.indexOf("\"id\":");
        if (idKey < 0) {
            throw new IllegalStateException("user JSON 里没有 id:" + abbreviate(userJson));
        }
        idKey += "\"id\":".length();
        int end = idKey;
        while (end < userJson.length() && Character.isDigit(userJson.charAt(end))) {
            end++;
        }
        if (end == idKey) {
            throw new IllegalStateException("id 不是数字:" + abbreviate(userJson.substring(idKey)));
        }
        return Integer.parseInt(userJson.substring(idKey, end));
    }

    private static String decodePayload(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("token 为空,无法解析");
        }
        String[] parts = token.split("[.]");
        if (parts.length < 2) {
            throw new IllegalStateException("token 不是 JWT(段数 " + parts.length + "):" + abbreviate(token));
        }
        try {
            // java.util.Base64 的 URL 解码器兼容无 padding 的段
            return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 兜底:手动补位再试一次
            String seg = parts[1];
            int rem = seg.length() % 4;
            if (rem == 2) {
                seg = seg + "==";
            } else if (rem == 3) {
                seg = seg + "=";
            }
            return new String(Base64.getUrlDecoder().decode(seg), StandardCharsets.UTF_8);
        }
    }

    private static String abbreviate(String s) {
        return s == null ? "null" : s.substring(0, Math.min(80, s.length()));
    }
}
