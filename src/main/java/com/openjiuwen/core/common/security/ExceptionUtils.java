// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.security;

/**
 * 异常工具类
 *
 * <p>用于格式化验证错误。</p>
 */
public final class ExceptionUtils {

    /**
     * 私有构造函数，防止实例化
     */
    private ExceptionUtils() {
    }

    /**
     * 格式化验证错误消息
     *
     * <p>Python版本使用Pydantic的ValidationError，Java版本简化实现。</p>
     *
     * @param e 验证错误异常
     * @return 格式化后的错误消息
     */
    public static String formatValidationError(Exception e) {
        if (e == null) {
            return "Unknown validation error";
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /**
     * 格式化验证错误消息（带错误位置）
     *
     * @param errorMap 错误映射
     * @return 格式化后的错误消息
     */
    @SuppressWarnings("unchecked")
    public static String formatValidationErrorWithLocation(java.util.Map<String, Object> errorMap) {
        if (errorMap == null) {
            return "Unknown validation error";
        }

        StringBuilder sb = new StringBuilder();

        for (java.util.Map.Entry<String, Object> entry : errorMap.entrySet()) {
            String location = entry.getKey();
            Object messageObj = entry.getValue();

            sb.append(location).append(": ");
            sb.append(messageObj != null ? messageObj.toString() : "Unknown error");
            sb.append("\n");
        }

        return sb.toString();
    }
}