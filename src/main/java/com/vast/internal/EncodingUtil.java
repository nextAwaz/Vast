package com.vast.internal;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

//编码工具类
public class EncodingUtil {

    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static String fixEncoding(String text) {
        if (text == null) return null;

        try {
            // 尝试检测和修复常见的编码问题
            byte[] bytes = text.getBytes("ISO-8859-1");
            if (isLikelyMisencodedUTF8(bytes)) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // 忽略异常，返回原文本
        }
        return text;
    }

    private static boolean isLikelyMisencodedUTF8(byte[] bytes) {
        // 简单的启发式检测：检查是否有 UTF-8 多字节序列的特征
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if ((b & 0xE0) == 0xC0) { // 2字节UTF-8序列开始
                if (i + 1 < bytes.length && (bytes[i + 1] & 0xC0) == 0x80) {
                    return true;
                }
            } else if ((b & 0xF0) == 0xE0) { // 3字节UTF-8序列开始
                if (i + 2 < bytes.length &&
                        (bytes[i + 1] & 0xC0) == 0x80 &&
                        (bytes[i + 2] & 0xC0) == 0x80) {
                    return true;
                }
            }
        }
        return false;
    }
}