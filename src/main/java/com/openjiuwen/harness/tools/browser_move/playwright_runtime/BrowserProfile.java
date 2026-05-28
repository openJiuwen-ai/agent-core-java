/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted browser profile metadata.
 *
 * <p>Mirrors Python's {@code BrowserProfile} dataclass in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.profiles}.</p>
 */
public class BrowserProfile {

    private String name;
    private String driverType = "remote";
    private String cdpUrl = "";
    private String browserBinary = "";
    private String userDataDir = "";
    private int debugPort = 0;
    private String host = "127.0.0.1";
    private List<String> extraArgs = new ArrayList<>();

    /**
     * Default constructor.
     */
    public BrowserProfile() {
    }

    /**
     * Create a BrowserProfile with the given name.
     *
     * @param name the profile name
     */
    public BrowserProfile(String name) {
        this.name = name;
    }

    /**
     * Full constructor with all fields.
     *
     * @param name          the profile name
     * @param driverType    the driver type (default "remote")
     * @param cdpUrl        the CDP URL
     * @param browserBinary the browser binary path
     * @param userDataDir   the user data directory
     * @param debugPort     the debug port
     * @param host          the host (default "127.0.0.1")
     * @param extraArgs     extra browser arguments
     */
    public BrowserProfile(String name, String driverType, String cdpUrl, String browserBinary,
                          String userDataDir, int debugPort, String host, List<String> extraArgs) {
        this.name = name;
        this.driverType = driverType != null ? driverType : "remote";
        this.cdpUrl = cdpUrl != null ? cdpUrl : "";
        this.browserBinary = browserBinary != null ? browserBinary : "";
        this.userDataDir = userDataDir != null ? userDataDir : "";
        this.debugPort = debugPort;
        this.host = host != null ? host : "127.0.0.1";
        this.extraArgs = extraArgs != null ? new ArrayList<>(extraArgs) : new ArrayList<>();
    }

    /**
     * Create a BrowserProfile from a raw dictionary.
     *
     * @param raw the raw dictionary
     * @return the BrowserProfile instance
     */
    public static BrowserProfile fromDict(Map<String, Object> raw) {
        if (raw == null) {
            return new BrowserProfile();
        }
        
        int debugPort = 0;
        Object debugPortRaw = raw.get("debug_port");
        if (debugPortRaw != null) {
            try {
                debugPort = Integer.parseInt(String.valueOf(debugPortRaw));
            } catch (NumberFormatException ignored) {
                debugPort = 0;
            }
        }

        String name = raw.get("name") != null ? String.valueOf(raw.get("name")).trim() : "";
        String driverType = raw.get("driver_type") != null 
                ? String.valueOf(raw.get("driver_type")).trim().toLowerCase() 
                : "remote";
        if (driverType.isEmpty()) {
            driverType = "remote";
        }
        String cdpUrl = raw.get("cdp_url") != null ? String.valueOf(raw.get("cdp_url")).trim() : "";
        String browserBinary = raw.get("browser_binary") != null 
                ? String.valueOf(raw.get("browser_binary")).trim() : "";
        String userDataDir = raw.get("user_data_dir") != null 
                ? String.valueOf(raw.get("user_data_dir")).trim() : "";
        String host = raw.get("host") != null ? String.valueOf(raw.get("host")).trim() : "127.0.0.1";
        if (host.isEmpty()) {
            host = "127.0.0.1";
        }

        List<String> extraArgs = new ArrayList<>();
        Object extraArgsRaw = raw.get("extra_args");
        if (extraArgsRaw instanceof List) {
            for (Object item : (List<?>) extraArgsRaw) {
                String strItem = String.valueOf(item).trim();
                if (!strItem.isEmpty()) {
                    extraArgs.add(strItem);
                }
            }
        }

        return new BrowserProfile(name, driverType, cdpUrl, browserBinary, userDataDir, 
                debugPort, host, extraArgs);
    }

    /**
     * Convert this profile to a dictionary.
     *
     * @return the dictionary representation
     */
    public Map<String, Object> toDict() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", name);
        dict.put("driver_type", driverType);
        dict.put("cdp_url", cdpUrl);
        dict.put("browser_binary", browserBinary);
        dict.put("user_data_dir", userDataDir);
        dict.put("debug_port", debugPort);
        dict.put("host", host);
        dict.put("extra_args", new ArrayList<>(extraArgs));
        return dict;
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public String getDriverType() {
        return driverType;
    }

    public void setDriverType(String driverType) {
        this.driverType = driverType != null ? driverType.trim().toLowerCase() : "remote";
        if (this.driverType.isEmpty()) {
            this.driverType = "remote";
        }
    }

    public String getCdpUrl() {
        return cdpUrl;
    }

    public void setCdpUrl(String cdpUrl) {
        this.cdpUrl = cdpUrl != null ? cdpUrl.trim() : "";
    }

    public String getBrowserBinary() {
        return browserBinary;
    }

    public void setBrowserBinary(String browserBinary) {
        this.browserBinary = browserBinary != null ? browserBinary.trim() : "";
    }

    public String getUserDataDir() {
        return userDataDir;
    }

    public void setUserDataDir(String userDataDir) {
        this.userDataDir = userDataDir != null ? userDataDir.trim() : "";
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
        this.host = host != null ? host.trim() : "127.0.0.1";
        if (this.host.isEmpty()) {
            this.host = "127.0.0.1";
        }
    }

    public List<String> getExtraArgs() {
        return extraArgs;
    }

    public void setExtraArgs(List<String> extraArgs) {
        this.extraArgs = extraArgs != null ? new ArrayList<>(extraArgs) : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BrowserProfile that = (BrowserProfile) o;
        return debugPort == that.debugPort &&
                Objects.equals(name, that.name) &&
                Objects.equals(driverType, that.driverType) &&
                Objects.equals(cdpUrl, that.cdpUrl) &&
                Objects.equals(browserBinary, that.browserBinary) &&
                Objects.equals(userDataDir, that.userDataDir) &&
                Objects.equals(host, that.host) &&
                Objects.equals(extraArgs, that.extraArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, driverType, cdpUrl, browserBinary, userDataDir, debugPort, host, extraArgs);
    }

    @Override
    public String toString() {
        return "BrowserProfile{" +
                "name='" + name + '\'' +
                ", driverType='" + driverType + '\'' +
                ", cdpUrl='" + cdpUrl + '\'' +
                ", browserBinary='" + browserBinary + '\'' +
                ", userDataDir='" + userDataDir + '\'' +
                ", debugPort=" + debugPort +
                ", host='" + host + '\'' +
                ", extraArgs=" + extraArgs +
                '}';
    }
}