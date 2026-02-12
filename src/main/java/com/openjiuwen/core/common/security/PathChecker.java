package com.openjiuwen.core.common.security;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路径安全检查器
 * 
 * <p>使用单例模式，检测路径是否为敏感系统路径，防止路径遍历攻击。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class PathChecker {

    private final Set<String> sensitivePaths;

    private PathChecker() {
        this.sensitivePaths = new HashSet<>();
        loadConfig();
    }

    /**
     * Bill Pugh单例模式
     */
    private static class Holder {
        private static final PathChecker INSTANCE = new PathChecker();
    }

    /**
     * 获取PathChecker单例实例
     * 
     * @return PathChecker实例
     */
    public static PathChecker getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 加载敏感路径配置
     */
    private void loadConfig() {
        sensitivePaths.clear();
        
        try {
            List<String> paths = UserConfig.getSensitivePaths();
            for (String path : paths) {
                if (path != null && !path.isEmpty()) {
                    try {
                        String normalizedPath = new File(path.trim()).getCanonicalPath();
                        sensitivePaths.add(normalizedPath);
                    } catch (Exception e) {
                        // If normalization fails, add as-is
                        sensitivePaths.add(path.trim());
                    }
                }
            }
        } catch (Exception e) {
            // Fallback to default sensitive paths
            loadDefaultSensitivePaths();
        }
    }

    /**
     * 加载默认敏感路径列表
     */
    private void loadDefaultSensitivePaths() {
        sensitivePaths.add("/etc/passwd");
        sensitivePaths.add("/etc/shadow");
        sensitivePaths.add("/etc/hosts");
        sensitivePaths.add("/etc/hostname");
        sensitivePaths.add("/etc/ssh/");
        sensitivePaths.add("/proc/");
        sensitivePaths.add("/sys/");
        sensitivePaths.add("/dev/");
        sensitivePaths.add("C:\\Windows\\System32\\");
        sensitivePaths.add("C:\\Windows\\SysWOW64\\");
        sensitivePaths.add("C:\\Windows\\System\\");
    }

    /**
     * 检查路径是否为敏感路径
     * 
     * @param path 要检查的路径（String类型）
     * @return 如果是敏感路径返回true，否则返回false
     */
    public boolean isSensitivePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        try {
            String normalizedPath = new File(path).getAbsolutePath();
            
            for (String sensitivePath : sensitivePaths) {
                if (normalizedPath.startsWith(sensitivePath)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // If path is invalid, consider it sensitive for safety
            return true;
        }
    }

    /**
     * 检查路径是否为敏感路径
     * 
     * @param path 要检查的路径（Path类型）
     * @return 如果是敏感路径返回true，否则返回false
     */
    public boolean isSensitivePath(Path path) {
        if (path == null) {
            return false;
        }
        return isSensitivePath(path.toString());
    }

    /**
     * 静态便捷方法：检查路径是否为敏感路径
     * 
     * <p>对应Python版本的模块级函数 is_sensitive_path(path)
     * 
     * @param path 要检查的路径
     * @return 如果是敏感路径返回true，否则返回false
     */
    public static boolean checkSensitivePath(String path) {
        return getInstance().isSensitivePath(path);
    }

    /**
     * 静态便捷方法：检查路径是否为敏感路径
     * 
     * <p>对应Python版本的模块级函数 is_sensitive_path(path)
     * 
     * @param path 要检查的路径
     * @return 如果是敏感路径返回true，否则返回false
     */
    public static boolean checkSensitivePath(Path path) {
        return getInstance().isSensitivePath(path);
    }
}

