package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.StatusCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;

/**
 * URL安全工具类
 * 
 * <p>提供URL验证、SSRF防护、代理配置等功能。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class UrlUtils {

    private static final long IP_10_0_0_0 = ipToLong("10.0.0.0");
    private static final long IP_10_255_255_255 = ipToLong("10.255.255.255");
    private static final long IP_172_16_0_0 = ipToLong("172.16.0.0");
    private static final long IP_172_31_255_255 = ipToLong("172.31.255.255");
    private static final long IP_192_168_0_0 = ipToLong("192.168.0.0");
    private static final long IP_192_168_255_255 = ipToLong("192.168.255.255");
    private static final long IP_127_0_0_0 = ipToLong("127.0.0.0");
    private static final long IP_127_255_255_255 = ipToLong("127.255.255.255");
    private static final long IP_0_0_0_0 = ipToLong("0.0.0.0");

    private UrlUtils() {
        // Utility class
    }

    /**
     * 检查URL是否有效
     * 
     * <p>验证URL协议、解析主机名并检查是否为内网IP（SSRF防护）。
     * 
     * @param url 要检查的URL
     * @throws com.openjiuwen.core.common.exception.JiuWenBaseException 如果URL无效
     */
    public static void checkUrlIsValid(String url) {
        if (url == null || url.isEmpty()) {
            ExceptionUtils.raiseException(StatusCode.COMMON_URL_INPUT_INVALID, "url is empty", null);
        }
        
        if (!url.matches("^https?://.*$")) {
            ExceptionUtils.raiseException(StatusCode.COMMON_URL_INPUT_INVALID, "illegal url protocol", null);
        }
        
        try {
            URI uri = new URI(url);
            String hostname = uri.getHost();
            
            if (hostname == null) {
                ExceptionUtils.raiseException(StatusCode.COMMON_URL_INPUT_INVALID, "invalid hostname", null);
            }
            
            InetAddress inetAddress = InetAddress.getByName(hostname);
            String ipAddress = inetAddress.getHostAddress();
            
            if (isInnerIpAddress(ipAddress)) {
                ExceptionUtils.raiseException(StatusCode.COMMON_URL_INPUT_INVALID, "illegal ip address", null);
            }
        } catch (UnknownHostException e) {
            ExceptionUtils.raiseException(StatusCode.COMMON_URL_INPUT_INVALID, "resolving IP address failed", e);
        } catch (Exception e) {
            if (e instanceof com.openjiuwen.core.common.exception.JiuWenBaseException) {
                throw (com.openjiuwen.core.common.exception.JiuWenBaseException) e;
            }
            ExceptionUtils.raiseException(StatusCode.COMMON_URL_INPUT_INVALID, "invalid url format", e);
        }
    }

    /**
     * 获取全局代理URL
     * 
     * @param url 目标URL
     * @return 代理URL，如果不需要代理则返回null
     */
    public static String getGlobalProxyUrl(String url) {
        if (url != null && shouldBypassProxy(url)) {
            return null;
        }
        
        String proxyUrl = System.getenv("http_proxy");
        if (proxyUrl == null) proxyUrl = System.getenv("https_proxy");
        if (proxyUrl == null) proxyUrl = System.getenv("HTTP_PROXY");
        if (proxyUrl == null) proxyUrl = System.getenv("HTTPS_PROXY");
        
        return proxyUrl != null ? proxyUrl.trim() : null;
    }

    /**
     * 获取全局代理配置
     * 
     * @param url 目标URL
     * @return 代理配置Map，如果不需要代理则返回null
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
     * 判断是否为内网IP地址
     *
     * @param ip IP地址字符串
     * @return 如果是内网IP返回true，否则返回false
     */
    public static boolean isInnerIpAddress(String ip) {
        String ssrfProtect = System.getenv("SSRF_PROTECT_ENABLED");
        if (ssrfProtect == null) {
            // 回退到系统属性（便于测试）
            ssrfProtect = System.getProperty("SSRF_PROTECT_ENABLED");
        }
        if ("false".equalsIgnoreCase(ssrfProtect)) {
            return false;
        }
        
        try {
            long ipLong = ipToLong(ip);
            return (ipLong >= IP_10_0_0_0 && ipLong <= IP_10_255_255_255) ||
                   (ipLong >= IP_172_16_0_0 && ipLong <= IP_172_31_255_255) ||
                   (ipLong >= IP_192_168_0_0 && ipLong <= IP_192_168_255_255) ||
                   (ipLong >= IP_127_0_0_0 && ipLong <= IP_127_255_255_255) ||
                   (ipLong == IP_0_0_0_0);
        } catch (Exception e) {
            return true; // If parsing fails, consider it unsafe
        }
    }

    /**
     * 将IP地址转换为long值
     * 
     * @param ipAddress IP地址字符串
     * @return IP地址的long表示
     */
    public static long ipToLong(String ipAddress) {
        try {
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            byte[] bytes = inetAddress.getAddress();
            return ((bytes[0] & 0xFFL) << 24) |
                   ((bytes[1] & 0xFFL) << 16) |
                   ((bytes[2] & 0xFFL) << 8) |
                   (bytes[3] & 0xFFL);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress, e);
        }
    }

    /**
     * 检查URL是否应该绕过代理
     * 
     * @param url 目标URL
     * @return 如果应该绕过代理返回true，否则返回false
     */
    public static boolean shouldBypassProxy(String url) {
        try {
            URI uri = new URI(url);
            String hostname = uri.getHost();
            
            if (hostname == null) {
                return false;
            }
            
            List<String> noProxyList = getNoProxyList();
            if (noProxyList.isEmpty()) {
                return false;
            }
            
            return hostnameMatchesNoProxy(hostname, noProxyList);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取NO_PROXY列表
     * 
     * @return NO_PROXY列表
     */
    private static List<String> getNoProxyList() {
        String noProxyUpper = System.getenv("NO_PROXY");
        String noProxyLower = System.getenv("no_proxy");
        
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        processProxyString(noProxyUpper, result, seen);
        processProxyString(noProxyLower, result, seen);
        
        return result;
    }

    /**
     * 处理代理字符串
     */
    private static void processProxyString(String proxyStr, List<String> result, Set<String> seen) {
        if (proxyStr == null || proxyStr.isEmpty()) {
            return;
        }
        
        proxyStr = proxyStr.replace(" ", ",").replace(";", ",");
        String[] items = proxyStr.split(",");
        
        for (String item : items) {
            item = item.trim().toLowerCase();
            if (!item.isEmpty() && !seen.contains(item)) {
                seen.add(item);
                result.add(item);
            }
        }
    }

    /**
     * 检查主机名是否匹配NO_PROXY列表
     */
    private static boolean hostnameMatchesNoProxy(String hostname, List<String> noProxyList) {
        String hostnameLower = hostname.toLowerCase();
        
        for (String entry : noProxyList) {
            // Wildcard match
            if ("*".equals(entry)) {
                return true;
            }
            
            // Exact domain match
            if (entry.equals(hostnameLower)) {
                return true;
            }
            
            // Suffix match: .example.com matches *.example.com
            if (entry.startsWith(".") && hostnameLower.endsWith(entry)) {
                return true;
            }
            
            // IP address match with CIDR support
            if (isIpMatch(hostnameLower, entry)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查IP地址或CIDR是否匹配
     * 
     * @param hostname 主机名（可能是IP地址）
     * @param entry NO_PROXY条目（可能是IP或CIDR）
     * @return 如果匹配返回true，否则返回false
     */
    private static boolean isIpMatch(String hostname, String entry) {
        try {
            InetAddress hostAddr = InetAddress.getByName(hostname);
            byte[] hostBytes = hostAddr.getAddress();
            
            // Check if entry contains CIDR notation
            if (entry.contains("/")) {
                // Parse CIDR (e.g., "192.168.1.0/24")
                String[] parts = entry.split("/");
                if (parts.length != 2) {
                    return false;
                }
                
                InetAddress networkAddr = InetAddress.getByName(parts[0]);
                byte[] networkBytes = networkAddr.getAddress();
                
                int prefixLength = Integer.parseInt(parts[1]);
                
                // Check if IP versions match
                if (hostBytes.length != networkBytes.length) {
                    return false;
                }
                
                // Compare bytes up to prefix length
                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;
                
                // Compare full bytes
                for (int i = 0; i < fullBytes; i++) {
                    if (hostBytes[i] != networkBytes[i]) {
                        return false;
                    }
                }
                
                // Compare remaining bits
                if (remainingBits > 0 && fullBytes < hostBytes.length) {
                    int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                    if ((hostBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                        return false;
                    }
                }
                
                return true;
            } else {
                // Exact IP match
                InetAddress entryAddr = InetAddress.getByName(entry);
                return hostAddr.equals(entryAddr);
            }
        } catch (Exception e) {
            // If parsing fails, not an IP match
            return false;
        }
    }
}

