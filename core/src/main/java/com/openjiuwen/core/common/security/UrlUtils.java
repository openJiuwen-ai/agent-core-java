/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code UrlUtils} in
 * {@code openjiuwen/core/common/security/url_utils.py}.
 */
public final class UrlUtils {

    private static volatile Function<String, String> envReader = System::getenv;

    private UrlUtils() {
    }

    public static void checkUrlIsValid(String url) {
        if (url == null || url.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_URL_INPUT_INVALID,
                    "error_msg",
                    "url is empty"
            );
        }
        if (!url.matches("^https?://.*$")) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_URL_INPUT_INVALID,
                    "error_msg",
                    "illegal url protocol"
            );
        }
        try {
            URI parsedUrl = new URI(url);
            String hostname = parsedUrl.getHost();
            String ipAddress = InetAddress.getByName(hostname).getHostAddress();
            if (isInnerIpAddress(ipAddress)) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_URL_INPUT_INVALID,
                        "error_msg",
                        "illegal ip address"
                );
            }
        } catch (URISyntaxException | NullPointerException | java.net.UnknownHostException error) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_URL_INPUT_INVALID,
                    "error_msg",
                    "resolving IP address failed"
            );
        }
    }

    public static String getGlobalProxyUrl(String url) {
        if (url != null && shouldBypassProxy(url)) {
            return null;
        }
        String proxy = firstNonEmpty(
                envReader.apply("http_proxy"),
                envReader.apply("https_proxy"),
                envReader.apply("HTTP_PROXY"),
                envReader.apply("HTTPS_PROXY")
        );
        return proxy == null ? null : proxy.trim();
    }

    public static Map<String, String> getGlobalProxies(String url) {
        String globalProxyUrl = getGlobalProxyUrl(url);
        if (globalProxyUrl == null) {
            return null;
        }
        Map<String, String> proxies = new LinkedHashMap<>();
        proxies.put("http", globalProxyUrl);
        proxies.put("https", globalProxyUrl);
        return proxies;
    }

    public static boolean shouldBypassProxy(String url) {
        try {
            URI parsedUrl = new URI(url);
            String hostname = parsedUrl.getHost();
            if (hostname == null || hostname.isEmpty()) {
                return false;
            }
            List<String> noProxyList = getNoProxyList();
            if (noProxyList.isEmpty()) {
                return false;
            }
            return hostnameMatchesNoProxy(hostname, noProxyList);
        } catch (Exception ignored) {
            return false;
        }
    }

    static void setEnvReaderForTests(Function<String, String> reader) {
        envReader = reader != null ? reader : System::getenv;
    }

    static void resetEnvReaderForTests() {
        envReader = System::getenv;
    }

    private static boolean isInnerIpAddress(String ip) {
        String protectEnabled = envReader.apply("SSRF_PROTECT_ENABLED");
        if ("false".equalsIgnoreCase(protectEnabled != null ? protectEnabled.toLowerCase(Locale.ROOT) : null)) {
            return false;
        }
        long ipLong = ipToLong(ip);
        return (ipToLong("10.0.0.0") <= ipLong && ipLong <= ipToLong("10.255.255.255"))
                || (ipToLong("172.16.0.0") <= ipLong && ipLong <= ipToLong("172.31.255.255"))
                || (ipToLong("192.168.0.0") <= ipLong && ipLong <= ipToLong("192.168.255.255"))
                || (ipToLong("127.0.0.0") <= ipLong && ipLong <= ipToLong("127.255.255.255"))
                || ipLong == ipToLong("0.0.0.0");
    }

    private static long ipToLong(String ipAddress) {
        try {
            return ByteBuffer.wrap(InetAddress.getByName(ipAddress).getAddress()).getInt() & 0xFFFF_FFFFL;
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid ip address", error);
        }
    }

    private static List<String> getNoProxyList() {
        List<String> result = new ArrayList<>();
        processProxyString(envReader.apply("NO_PROXY"), result);
        processProxyString(envReader.apply("no_proxy"), result);
        return result;
    }

    private static void processProxyString(String proxyString, List<String> result) {
        if (proxyString == null || proxyString.isEmpty()) {
            return;
        }
        String normalized = proxyString.replace(" ", ",").replace(";", ",");
        for (String item : normalized.split(",")) {
            String candidate = item.trim().toLowerCase(Locale.ROOT);
            if (!candidate.isEmpty() && !result.contains(candidate)) {
                result.add(candidate);
            }
        }
    }

    private static boolean hostnameMatchesNoProxy(String hostname, List<String> noProxyList) {
        String hostnameLower = hostname.toLowerCase(Locale.ROOT);
        for (String entry : noProxyList) {
            if ("*".equals(entry)) {
                return true;
            }
            if (entry.equals(hostnameLower)) {
                return true;
            }
            if (entry.startsWith(".") && hostnameLower.endsWith(entry)) {
                return true;
            }
            if (isIpMatch(hostnameLower, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIpMatch(String hostname, String entry) {
        try {
            InetAddress hostAddress = InetAddress.getByName(hostname);
            if (entry.contains("/")) {
                return isInCidr(hostAddress, entry);
            }
            return hostAddress.equals(InetAddress.getByName(entry));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isInCidr(InetAddress address, String cidr) {
        try {
            String[] parts = cidr.split("/", 2);
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            byte[] addressBytes = address.getAddress();
            byte[] networkBytes = network.getAddress();
            if (addressBytes.length != networkBytes.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addressBytes[i] != networkBytes[i]) {
                    return false;
                }
            }
            if (remainingBits == 0 || fullBytes >= addressBytes.length) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (addressBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
