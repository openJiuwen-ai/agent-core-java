// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.security.PathChecker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 日志工具类
 *
 * <p>提供日志相关的工具函数，支持异步环境。</p>
 *
 * <p>使用Java的ScopedValue（Java 21+）或ThreadLocal来支持线程上下文隔离。</p>
 */
public final class LogUtils {

    // 使用ThreadLocal支持多线程环境（Java 21+可以使用ScopedValue）
    private static final ThreadLocal<String> TRACE_ID_CONTEXT = ThreadLocal.withInitial(() -> "default_trace_id");

    /**
     * 私有构造函数，防止实例化
     */
    private LogUtils() {
    }

    /**
     * 在当前上下文中设置trace_id
     *
     * @param traceId 用于日志关联和跟踪的跟踪ID
     */
    public static void setSessionId(String traceId) {
        TRACE_ID_CONTEXT.set(traceId != null ? traceId : "default_trace_id");
    }

    /**
     * 从当前上下文中获取trace_id
     *
     * @return 当前上下文中的trace_id，如果未设置则返回默认值"default_trace_id"
     */
    public static String getSessionId() {
        try {
            return TRACE_ID_CONTEXT.get();
        } catch (Exception e) {
            return "default_trace_id";
        }
    }

    /**
     * 清除当前上下文中的trace_id
     */
    public static void clearSessionId() {
        TRACE_ID_CONTEXT.remove();
    }

    /**
     * 获取日志最大字节数
     *
     * @param maxBytesConfig 最大字节数配置
     * @return 有效的最大字节数
     * @throws IllegalArgumentException 如果配置无效
     */
    public static int getLogMaxBytes(Object maxBytesConfig) {
        int maxBytes;
        try {
            if (maxBytesConfig instanceof Number) {
                maxBytes = ((Number) maxBytesConfig).intValue();
            } else if (maxBytesConfig instanceof String) {
                maxBytes = Integer.parseInt((String) maxBytesConfig);
            } else {
                throw new IllegalArgumentException("Invalid max_bytes configuration type: " +
                    (maxBytesConfig != null ? maxBytesConfig.getClass().getName() : "null"));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid max_bytes configuration: " + maxBytesConfig + ", error: " + e.getMessage(), e
            );
        }

        int defaultLogMaxBytes = 100 * 1024 * 1024; // 100MB
        if (maxBytes <= 0 || maxBytes > defaultLogMaxBytes) {
            maxBytes = defaultLogMaxBytes;
        }

        return maxBytes;
    }

    /**
     * 规范化和验证日志路径
     *
     * <p>此辅助函数由日志配置和默认日志记录器实现共享。</p>
     * <p>当以下情况时抛出BaseError：</p>
     * <ul>
     *     <li>值类型无效</li>
     *     <li>规范化的路径被认为是敏感/不安全的</li>
     * </ul>
     *
     * @param pathValue 路径值
     * @return 规范化的绝对路径
     * @throws IllegalArgumentException 如果路径无效或为敏感路径
     */
    public static String normalizeAndValidateLogPath(Object pathValue) {
        // 支持String类型
        if (pathValue == null) {
            throw new IllegalArgumentException("path_value cannot be null");
        }

        String pathStr;
        if (pathValue instanceof String) {
            pathStr = (String) pathValue;
        } else if (pathValue instanceof Path) {
            pathStr = pathValue.toString();
        } else {
            throw new IllegalArgumentException("path_value must be String or Path, got: " +
                pathValue.getClass().getName());
        }

        // 检查空值
        if (pathStr == null || pathStr.trim().isEmpty()) {
            throw new IllegalArgumentException("path_str cannot be empty");
        }

        Path path = Paths.get(pathStr);
        Path realPath;
        try {
            // 尝试获取真实路径（解析符号链接）
            realPath = path.toRealPath();
        } catch (IOException e) {
            // 如果无法解析，使用绝对路径并扩展用户主目录
            realPath = path.toAbsolutePath().normalize();
        }

        String realPathStr = realPath.toString();

        // 检查敏感路径（暂时简化实现，后续在PathChecker中完善）
        if (isSensitivePath(realPathStr)) {
            throw new IllegalArgumentException(
                "Path is sensitive or unsafe: " + realPathStr
            );
        }

        return realPathStr;
    }

    /**
     * 检查路径是否为敏感路径
     *
     * @param path 路径
     * @return 是否为敏感路径
     */
    private static boolean isSensitivePath(String path) {
        return PathChecker.getInstance().isSensitivePath(path);
    }
}