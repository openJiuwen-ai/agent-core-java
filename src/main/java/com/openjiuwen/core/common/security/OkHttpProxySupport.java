/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies process proxy environment variables to OkHttp clients.
 */
public final class OkHttpProxySupport {
    private static final Logger LOG = LoggerFactory.getLogger(OkHttpProxySupport.class);

    private OkHttpProxySupport() {
    }

    /**
     * Configure proxy settings from http_proxy/https_proxy for the target URL.
     *
     * @param builder   OkHttp client builder
     * @param targetUrl target service URL
     */
    public static void configureFromEnvironment(OkHttpClient.Builder builder, String targetUrl) {
        URI targetUri = parseUri(targetUrl);
        if (targetUri == null || shouldBypassProxy(targetUri.getHost())) {
            return;
        }

        URI proxyUri = selectProxyUri(targetUri.getScheme());
        if (proxyUri == null || proxyUri.getHost() == null) {
            return;
        }
        int port = proxyPort(proxyUri);
        if (port <= 0) {
            return;
        }

        builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), port)));
        String userInfo = proxyUri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] credentials = decodeCredentials(userInfo);
            String proxyAuthorization = Credentials.basic(credentials[0], credentials[1]);
            builder.proxyAuthenticator((route, response) -> response.request().newBuilder()
                    .header("Proxy-Authorization", proxyAuthorization)
                    .build());
            LOG.debug("Proxy authentication configured for OkHttp, proxyHost={}, proxyPort={}, usernamePresent={}",
                    proxyUri.getHost(), port, !credentials[0].isBlank());
        }
    }

    private static String[] decodeCredentials(String userInfo) {
        String[] parts = userInfo.split(":", 2);
        String user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        String password = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
        return new String[]{user, password};
    }

    private static URI selectProxyUri(String targetScheme) {
        String proxyValue;
        if ("https".equalsIgnoreCase(targetScheme)) {
            proxyValue = firstNonBlankEnv("https_proxy", "HTTPS_PROXY", "http_proxy", "HTTP_PROXY");
        } else {
            proxyValue = firstNonBlankEnv("http_proxy", "HTTP_PROXY", "https_proxy", "HTTPS_PROXY");
        }
        return parseProxyUri(proxyValue);
    }

    private static URI parseProxyUri(String proxyValue) {
        if (proxyValue == null || proxyValue.isBlank()) {
            return null;
        }
        String normalized = proxyValue.trim();
        if (!normalized.contains("://")) {
            normalized = "http://" + normalized;
        }
        return parseUri(normalized);
    }

    private static URI parseUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int proxyPort(URI proxyUri) {
        if (proxyUri.getPort() > 0) {
            return proxyUri.getPort();
        }
        if ("https".equalsIgnoreCase(proxyUri.getScheme())) {
            return 443;
        }
        if ("http".equalsIgnoreCase(proxyUri.getScheme())) {
            return 80;
        }
        return -1;
    }

    private static boolean shouldBypassProxy(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if (isLoopbackHost(host)) {
            return true;
        }
        String noProxy = firstNonBlankEnv("no_proxy", "NO_PROXY");
        if (noProxy == null || noProxy.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String entry : noProxy.replace(";", ",").split(",")) {
            String pattern = entry.trim().toLowerCase(Locale.ROOT);
            if (matchesNoProxy(normalizedHost, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoopbackHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost);
    }

    private static boolean matchesNoProxy(String host, String pattern) {
        if (pattern.isBlank() || pattern.contains("/")) {
            return false;
        }
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.startsWith("*.")) {
            return host.endsWith(pattern.substring(1));
        }
        if (pattern.startsWith(".")) {
            return host.endsWith(pattern);
        }
        return host.equals(pattern);
    }

    private static String firstNonBlankEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
