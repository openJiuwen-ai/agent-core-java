/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.drivers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Managed isolated browser launcher for CDP attach.
 *
 * <p>Manages Chrome browser instances with isolated profiles
 * for Playwright automation.
 *
 * <p>Mirrors Python's {@code ManagedBrowser} in
 * {@code openjiuwen.harness.tools.browser_move.drivers.managed_browser}.
 */
public class ManagedBrowser {

    private static final Logger LOG = LoggerFactory.getLogger(ManagedBrowser.class);

    private final String browserType;
    private final Path userDataDir;
    private final int port;
    private Process browserProcess;
    private String cdpEndpoint;

    /**
     * Construct managed browser launcher.
     */
    public ManagedBrowser(String browserType, Path userDataDir, int port) {
        this.browserType = browserType != null ? browserType : "chrome";
        this.userDataDir = userDataDir;
        this.port = port;
    }

    /**
     * Default constructor.
     */
    public ManagedBrowser() {
        this("chrome", null, 9222);
    }

    /**
     * Start browser process.
     */
    public CompletableFuture<BrowserInfo> start() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.info("[ManagedBrowser] start browser_type={} port={}", browserType, port);

                // Placeholder - actual implementation depends on browser launch
                String cdpUrl = "http://localhost:" + port;
                this.cdpEndpoint = cdpUrl;

                return BrowserInfo.success(cdpUrl, port);
            } catch (Exception e) {
                LOG.error("[ManagedBrowser] start failed", e);
                return BrowserInfo.error(e.getMessage());
            }
        });
    }

    /**
     * Stop browser process.
     */
    public void stop() {
        LOG.info("[ManagedBrowser] stop");
        if (browserProcess != null && browserProcess.isAlive()) {
            browserProcess.destroy();
        }
        browserProcess = null;
        cdpEndpoint = null;
    }

    /**
     * Get CDP endpoint URL.
     */
    public String getCdpEndpoint() {
        return cdpEndpoint;
    }

    /**
     * Check if browser is running.
     */
    public boolean isRunning() {
        return browserProcess != null && browserProcess.isAlive();
    }

    /**
     * Get default Chrome user data directory.
     */
    public static Path getDefaultChromeUserDataDir() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                return Path.of(localAppData, "Google", "Chrome", "User Data");
            }
            return Path.of(System.getProperty("user.home"), "AppData", "Local", "Google", "Chrome", "User Data");
        }
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Google", "Chrome");
        }
        return Path.of(System.getProperty("user.home"), ".config", "google-chrome");
    }

    /**
     * Browser info wrapper.
     */
    public static class BrowserInfo {
        private final boolean success;
        private final String cdpUrl;
        private final int port;
        private final String error;

        private BrowserInfo(boolean success, String cdpUrl, int port, String error) {
            this.success = success;
            this.cdpUrl = cdpUrl;
            this.port = port;
            this.error = error;
        }

        public static BrowserInfo success(String cdpUrl, int port) {
            return new BrowserInfo(true, cdpUrl, port, null);
        }

        public static BrowserInfo error(String error) {
            return new BrowserInfo(false, null, 0, error);
        }

        public boolean isSuccess() { return success; }
        public String getCdpUrl() { return cdpUrl; }
        public int getPort() { return port; }
        public String getError() { return error; }
    }
}