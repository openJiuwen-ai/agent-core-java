// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * URL工具类
 *
 * <p>提供URL验证、代理配置等功能。</p>
 */
public final class UrlUtils {

    private static final Pattern HTTPS_PATTERN = Pattern.compile("^https?://.*$", Pattern.CASE_INSENSITIVE);
    private static final List<String> TRIGGER_VALUES = Arrays.asList("true", "1", "yes", "on");

    /**
     * 私有构造函数，防止实例化
     */
    private UrlUtils() {
    }

    /**
     * 检查URL是否有效
     *
     * @param url URL字符串
     * @throws IllegalArgumentException 如果URL无效
     */
    public static void checkUrlIsValid(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL is empty");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL format", e);
        }

        if (!HTTPS_PATTERN.matcher(url).matches()) {
            throw new IllegalArgumentException("Illegal URL protocol: must be http or https");
        }

        String hostname = uri.getHost();
        if (hostname == null) {
            throw new IllegalArgumentException("Invalid URL: hostname is null");
        }

        try {
            InetAddress address = InetAddress.getByName(hostname);
            if (isInnerIpAddress(address)) {
                throw new IllegalArgumentException("Illegal IP address: inner IP not allowed");
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Resolving IP address failed", e);
        }
    }

    /**
     * 获取全局代理URL
     *
     * @param url URL字符串
     * @return 代理URL，如果不需要代理则返回null
     */
    public static String getGlobalProxyUrl(String url) {
        if (url != null && shouldBypassProxy(url)) {
            return null;
        }

        String proxyUrl = getEnvVar("http_proxy") != null ? getEnvVar("http_proxy") :
                           getEnvVar("https_proxy") != null ? getEnvVar("https_proxy") :
                           getEnvVar("HTTP_PROXY") != null ? getEnvVar("HTTP_PROXY") :
                           getEnvVar("HTTPS_PROXY");

        return proxyUrl != null ? proxyUrl.trim() : null;
    }

    /**
     * 获取全局代理配置
     *
     * @param url URL字符串
     * @return 代理配置映射，如果不需要代理则返回null
     */
    public static Map<String, String> getGlobalProxies(String url) {
        String proxyUrl = getGlobalProxyUrl(url);
        if (proxyUrl != null) {
            Map<String, String> proxies = new HashMap<>();
            proxies.put("http", proxyUrl);
            proxies.put("https", proxyUrl);
            return proxies;
        }
        return null;
    }

    /**
     * 检查URL是否应该绕过代理
     *
     * @param url URL字符串
     * @return 是否应该绕过代理
     */
    public static boolean shouldBypassProxy(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String hostname = uri.getHost();
        if (hostname == null) {
            return false;
        }

        List<String> noProxyList = getNoProxyList();
        if (noProxyList.isEmpty()) {
            return false;
        }

        return hostnameMatchesNoProxy(hostname, noProxyList);
    }

    /**
     * 获取NO_PROXY列表
     *
     * @return NO_PROXY列表
     */
    private static List<String> getNoProxyList() {
        List<String> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        processProxyStr(getEnvVar("NO_PROXY"), seen, result);
        processProxyStr(getEnvVar("no_proxy"), seen, result);

        return result;
    }

    /**
     * 处理代理字符串
     */
    private static void processProxyStr(String proxyStr, java.util.Set<String> seen, List<String> result) {
        if (proxyStr == null || proxyStr.isEmpty()) {
            return;
        }

        String normalized = proxyStr.replace(" ", ",").replace(";", ",");
        String[] items = normalized.split(",");

        for (String item : items) {
            String trimmed = item.trim().toLowerCase();
            if (!trimmed.isEmpty() && !seen.contains(trimmed)) {
                seen.add(trimmed);
                result.add(trimmed);
            }
        }
    }

    /**
     * 检查主机名是否匹配NO_PROXY列表
     */
    private static boolean hostnameMatchesNoProxy(String hostname, List<String> noProxyList) {
        String hostnameLower = hostname.toLowerCase();

        for (String entry : noProxyList) {
            // 1. 通配符 "*" 匹配所有
            if ("*".equals(entry)) {
                return true;
            }
            // 2. 完全匹配
            if (entry.equals(hostnameLower)) {
                return true;
            }
            // 3. 后缀匹配: ".example.com" 匹配 "*.example.com"
            if (entry.startsWith(".")) {
                if (hostnameLower.endsWith(entry)) {
                    return true;
                }
            }
            // 4. IP地址匹配
            if (isIpMatch(hostnameLower, entry)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查IP地址或CIDR是否匹配
     */
    private static boolean isIpMatch(String hostname, String entry) {
        // 简化实现：仅支持完全匹配
        return hostname.equals(entry);
    }

    /**
     * 判断是否为内网IP地址
     */
    private static boolean isInnerIpAddress(InetAddress address) {
        // 检查SSRF保护是否启用
        String ssrfProtect = System.getenv("SSRF_PROTECT_ENABLED");
        if (ssrfProtect != null && ssrfProtect.equalsIgnoreCase("false")) {
            return false;
        }

        byte[] addr = address.getAddress();
        if (addr.length != 4) {
            return false; // IPv6暂时不检查
        }

        // 10.0.0.0 - 10.255.255.255
        if (addr[0] == 10) {
            return true;
        }

        // 172.16.0.0 - 172.31.255.255
        if (addr[0] == (byte) 172 && (addr[1] & 0xF0) == 0x10) {
            return true;
        }

        // 192.168.0.0 - 192.168.255.255
        if (addr[0] == (byte) 192 && addr[1] == (byte) 168) {
            return true;
        }

        // 127.0.0.0 - 127.255.255.255 (loopback)
        if (addr[0] == 127) {
            return true;
        }

        // 0.0.0.0
        if (addr[0] == 0 && addr[1] == 0 && addr[2] == 0 && addr[3] == 0) {
            return true;
        }

        return false;
    }

    /**
     * 获取环境变量
     */
    private static String getEnvVar(String name) {
        return System.getenv(name);
    }
}