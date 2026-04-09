/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * URL validation and proxy utilities — protects against SSRF attacks.
 */
public final class UrlUtils {

    private UrlUtils() {
    }

    /**
     * Validate that a URL is well-formed, uses http(s), and does not resolve to an internal IP.
     *
     * @throws com.openjiuwen.core.common.exception.BaseError if the URL is invalid or resolves to a private IP
     */
    public static void checkUrlIsValid(String url) {
        if (url == null || url.isBlank()) {
            ErrorHelper.raiseError(StatusCode.COMMON_URL_INPUT_INVALID,
                "url is empty", null, null, null);
        }
        if (!url.matches("^https?://.*$")) {
            ErrorHelper.raiseError(StatusCode.COMMON_URL_INPUT_INVALID,
                "illegal url protocol", null, null, null);
        }
        try {
            URI uri = new URI(sanitizeUrl(url));
            String hostname = uri.getHost();
            InetAddress addr = InetAddress.getByName(hostname);
            if (isInnerIpAddress(addr)) {
                ErrorHelper.raiseError(StatusCode.COMMON_URL_INPUT_INVALID,
                    "illegal ip address", null, null, null);
            }
        } catch (URISyntaxException | UnknownHostException e) {
            throw ErrorHelper.buildError(StatusCode.COMMON_URL_INPUT_INVALID,
                "resolving IP address failed", null, e, null);
        }
    }

    /**
     * Get the global proxy URL from environment variables, respecting NO_PROXY.
     */
    public static String getGlobalProxyUrl(String url) {
        if (url != null && shouldBypassProxy(url)) {
            return null;
        }
        String proxy = System.getenv("http_proxy");
        if (proxy == null) proxy = System.getenv("https_proxy");
        if (proxy == null) proxy = System.getenv("HTTP_PROXY");
        if (proxy == null) proxy = System.getenv("HTTPS_PROXY");
        return proxy != null ? proxy.trim() : null;
    }

    /**
     * Get global proxies as a map (http → proxy, https → proxy).
     */
    public static Map<String, String> getGlobalProxies(String url) {
        String proxy = getGlobalProxyUrl(url);
        if (proxy != null) {
            return Map.of("http", proxy, "https", proxy);
        }
        return null;
    }

    /**
     * Check if the URL should bypass proxying based on NO_PROXY.
     */
    public static boolean shouldBypassProxy(String url) {
        try {
            URI uri = new URI(url);
            String hostname = uri.getHost();
            if (hostname == null || hostname.isBlank()) {
                return false;
            }
            List<String> noProxyList = getNoProxyList();
            return !noProxyList.isEmpty() && hostnameMatchesNoProxy(hostname.toLowerCase(), noProxyList);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Internal ====================

    private static boolean isInnerIpAddress(InetAddress addr) {
        String ssrfEnabled = System.getenv("SSRF_PROTECT_ENABLED");
        if (ssrfEnabled == null || ssrfEnabled.isBlank()) {
            ssrfEnabled = System.getProperty("SSRF_PROTECT_ENABLED");
        }
        if ("false".equalsIgnoreCase(ssrfEnabled)) {
            return false;
        }
        return addr.isLoopbackAddress()
            || addr.isSiteLocalAddress()
            || addr.isLinkLocalAddress()
            || addr.isAnyLocalAddress();
    }

    private static String sanitizeUrl(String url) {
        return url.replaceAll("\\{[^/{}]+}", "placeholder");
    }

    private static List<String> getNoProxyList() {
        Set<String> seen = new LinkedHashSet<>();
        processProxyStr(System.getenv("NO_PROXY"), seen);
        processProxyStr(System.getenv("no_proxy"), seen);
        return new ArrayList<>(seen);
    }

    private static void processProxyStr(String proxyStr, Set<String> seen) {
        if (proxyStr == null || proxyStr.isBlank()) {
            return;
        }
        String normalized = proxyStr.replace(" ", ",").replace(";", ",");
        for (String item : normalized.split(",")) {
            String trimmed = item.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                seen.add(trimmed);
            }
        }
    }

    private static boolean hostnameMatchesNoProxy(String hostname, List<String> noProxyList) {
        for (String entry : noProxyList) {
            if ("*".equals(entry)) return true;
            if (entry.equals(hostname)) return true;
            if (entry.startsWith(".") && hostname.endsWith(entry)) return true;
            if (isIpMatch(hostname, entry)) return true;
        }
        return false;
    }

    private static boolean isIpMatch(String hostname, String entry) {
        try {
            InetAddress hostIp = InetAddress.getByName(hostname);
            if (entry.contains("/")) {
                // CIDR match
                return isInCidr(hostIp, entry);
            } else {
                InetAddress entryIp = InetAddress.getByName(entry);
                return hostIp.equals(entryIp);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isInCidr(InetAddress addr, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefixLen = Integer.parseInt(parts[1]);

            byte[] addrBytes = addr.getAddress();
            byte[] networkBytes = network.getAddress();
            if (addrBytes.length != networkBytes.length) return false;

            int fullBytes = prefixLen / 8;
            int remainBits = prefixLen % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addrBytes[i] != networkBytes[i]) return false;
            }
            if (remainBits > 0 && fullBytes < addrBytes.length) {
                int mask = 0xFF << (8 - remainBits);
                if ((addrBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
