/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted browser profile metadata.
 *
 * <p>Mirrors Python's {@code BrowserProfile} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/profiles.py}.
 */
public final class BrowserProfile {

    private String name;
    private String driverType = "remote";
    private String cdpUrl = "";
    private String browserBinary = "";
    private String userDataDir = "";
    private int debugPort;
    private String host = "127.0.0.1";
    private List<String> extraArgs = new ArrayList<>();

    public BrowserProfile() {
    }

    public BrowserProfile(
            String name,
            String driverType,
            String cdpUrl,
            String browserBinary,
            String userDataDir,
            int debugPort,
            String host,
            List<String> extraArgs
    ) {
        this.name = sanitize(name);
        this.driverType = sanitize(driverType).isEmpty() ? "remote" : sanitize(driverType).toLowerCase();
        this.cdpUrl = sanitize(cdpUrl);
        this.browserBinary = sanitize(browserBinary);
        this.userDataDir = sanitize(userDataDir);
        this.debugPort = debugPort;
        this.host = sanitize(host).isEmpty() ? "127.0.0.1" : sanitize(host);
        this.extraArgs = extraArgs == null ? new ArrayList<>() : new ArrayList<>(extraArgs);
    }

    public static BrowserProfile fromMap(Map<String, Object> raw) {
        if (raw == null) {
            return new BrowserProfile();
        }
        int debugPort;
        try {
            debugPort = Integer.parseInt(String.valueOf(raw.getOrDefault("debug_port", 0)));
        } catch (RuntimeException ex) {
            debugPort = 0;
        }
        List<String> extraArgs = new ArrayList<>();
        Object rawArgs = raw.get("extra_args");
        if (rawArgs instanceof List<?> list) {
            for (Object item : list) {
                String value = sanitize(item);
                if (!value.isEmpty()) {
                    extraArgs.add(value);
                }
            }
        }
        return new BrowserProfile(
                sanitize(raw.get("name")),
                sanitize(raw.getOrDefault("driver_type", "remote")),
                sanitize(raw.get("cdp_url")),
                sanitize(raw.get("browser_binary")),
                sanitize(raw.get("user_data_dir")),
                debugPort,
                sanitize(raw.getOrDefault("host", "127.0.0.1")),
                extraArgs
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", name);
        raw.put("driver_type", driverType);
        raw.put("cdp_url", cdpUrl);
        raw.put("browser_binary", browserBinary);
        raw.put("user_data_dir", userDataDir);
        raw.put("debug_port", debugPort);
        raw.put("host", host);
        raw.put("extra_args", List.copyOf(extraArgs));
        return raw;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = sanitize(name);
    }

    public String getDriverType() {
        return driverType;
    }

    public void setDriverType(String driverType) {
        String value = sanitize(driverType).toLowerCase();
        this.driverType = value.isEmpty() ? "remote" : value;
    }

    public String getCdpUrl() {
        return cdpUrl;
    }

    public void setCdpUrl(String cdpUrl) {
        this.cdpUrl = sanitize(cdpUrl);
    }

    public String getBrowserBinary() {
        return browserBinary;
    }

    public void setBrowserBinary(String browserBinary) {
        this.browserBinary = sanitize(browserBinary);
    }

    public String getUserDataDir() {
        return userDataDir;
    }

    public void setUserDataDir(String userDataDir) {
        this.userDataDir = sanitize(userDataDir);
    }

    public int getDebugPort() {
        return debugPort;
    }

    public void setDebugPort(int debugPort) {
        this.debugPort = debugPort;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        String value = sanitize(host);
        this.host = value.isEmpty() ? "127.0.0.1" : value;
    }

    public List<String> getExtraArgs() {
        return List.copyOf(extraArgs);
    }

    public void setExtraArgs(List<String> extraArgs) {
        this.extraArgs = extraArgs == null ? new ArrayList<>() : new ArrayList<>(extraArgs);
    }

    private static String sanitize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
