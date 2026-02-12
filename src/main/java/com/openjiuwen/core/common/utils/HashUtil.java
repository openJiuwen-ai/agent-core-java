package com.openjiuwen.core.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 哈希生成工具类
 * 
 * 从 Python hash_util.py 转换
 * 提供SHA-256哈希生成功能
 */
public final class HashUtil {

    private HashUtil() {
        // 防止实例化
    }

    /**
     * 生成API配置的哈希键
     * 
     * 将输入参数排序后拼接，生成SHA-256哈希
     *
     * @param apiKey        API密钥
     * @param apiBase       API基础URL
     * @param modelProvider 模型提供商（默认"openai"）
     * @return SHA-256哈希的十六进制字符串
     */
    public static String generateKey(String apiKey, String apiBase, String modelProvider) {
        // 使用默认值
        if (modelProvider == null || modelProvider.isEmpty()) {
            modelProvider = "openai";
        }

        // 对输入进行排序并拼接
        String[] parts = {apiKey, apiBase, modelProvider};
        Arrays.sort(parts);
        String combined = String.join("", parts);

        // 生成SHA-256哈希
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}


