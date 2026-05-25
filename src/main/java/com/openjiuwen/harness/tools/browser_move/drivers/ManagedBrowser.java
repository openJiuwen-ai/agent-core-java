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
     * <p>
     * Mirrors Python's {@code start} method which launches Chrome with CDP debugging.
     */
    public CompletableFuture<BrowserInfo> start() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.info("[ManagedBrowser] start browser_type={} port={}", browserType, port);

                // Resolve Chrome binary
                String binary = resolveChromeBinary();
                if (binary == null) {
                    return BrowserInfo.error("Chrome binary not found. Please install Chrome.");
                }

                // Ensure user data directory exists
                java.nio.file.Path actualUserDataDir = userDataDir;
                if (actualUserDataDir == null) {
                    actualUserDataDir = getDefaultChromeUserDataDir().resolve("managed_profile_" + port);
                }
                if (!actualUserDataDir.toFile().exists()) {
                    actualUserDataDir.toFile().mkdirs();
                }

                // Build Chrome launch arguments
                java.util.List<String> args = buildChromeArgs(binary, actualUserDataDir, port);

                // Kill existing Chrome processes using this user data directory
                killChromeByUserDataDir(actualUserDataDir.toString());

                // Cleanup stale singleton files
                cleanupSingletonFiles(actualUserDataDir);

                // Launch Chrome process
                ProcessBuilder pb = new ProcessBuilder(args);
                pb.redirectErrorStream(true);
                browserProcess = pb.start();

                LOG.info("[ManagedBrowser] Chrome launched, waiting for CDP endpoint...");

                // Wait for CDP endpoint to be ready
                String cdpUrl = waitForCdpEndpoint(port, 20.0);
                this.cdpEndpoint = cdpUrl;

                LOG.info("[ManagedBrowser] CDP endpoint ready: {}", cdpUrl);
                return BrowserInfo.success(cdpUrl, port);
            } catch (Exception e) {
                LOG.error("[ManagedBrowser] start failed", e);
                return BrowserInfo.error(e.getMessage());
            }
        });
    }

    /**
     * Resolve Chrome binary path.
     * <p>
     * Mirrors Python's {@code _resolve_binary} and {@code _candidate_chrome_binaries}.
     */
    private String resolveChromeBinary() {
        String os = System.getProperty("os.name").toLowerCase();
        java.util.List<String> candidates = new java.util.ArrayList<>();

        // Check common binary names
        candidates.add("chrome");
        candidates.add("google-chrome");
        candidates.add("google-chrome-stable");

        // Platform-specific paths
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                candidates.add(localAppData + "\\Google\\Chrome\\Application\\chrome.exe");
            }
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                candidates.add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
            }
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            if (programFilesX86 != null) {
                candidates.add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
            }
        } else if (os.contains("mac")) {
            candidates.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
            candidates.add(System.getProperty("user.home") + "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
        } else {
            candidates.add("/usr/bin/google-chrome");
            candidates.add("/usr/bin/google-chrome-stable");
            candidates.add("/opt/google/chrome/chrome");
        }

        // Find first existing binary
        for (String candidate : candidates) {
            java.nio.file.Path path = java.nio.file.Path.of(candidate);
            if (path.toFile().exists()) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Build Chrome launch arguments.
     * <p>
     * Mirrors Python's {@code _build_args} method.
     */
    private java.util.List<String> buildChromeArgs(String binary, java.nio.file.Path userDataDir, int port) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(binary);
        args.add("--remote-debugging-address=127.0.0.1");
        args.add("--remote-debugging-port=" + port);
        args.add("--user-data-dir=" + userDataDir.toString());
        args.add("--no-first-run");
        args.add("--no-default-browser-check");
        args.add("about:blank");
        return args;
    }

    /**
     * Kill existing Chrome processes using the specified user data directory.
     * <p>
     * Mirrors Python's {@code _kill_chrome_by_user_data_dir}.
     */
    private void killChromeByUserDataDir(String userDataDir) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                // Use PowerShell on Windows
                ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-WmiObject Win32_Process -Filter \"name='chrome.exe'\" | " +
                    "Where-Object { $_.CommandLine -like '*--user-data-dir=" + userDataDir + "*' } | " +
                    "ForEach-Object { taskkill /F /PID $_.ProcessId }"
                );
                pb.start();
            } else {
                // Use pgrep/kill on Unix
                ProcessBuilder pb = new ProcessBuilder(
                    "sh", "-c",
                    "pgrep -f '--user-data-dir=" + userDataDir + "' | xargs kill -9"
                );
                pb.start();
            }
        } catch (Exception e) {
            LOG.debug("[ManagedBrowser] kill_chrome_by_user_data_dir failed (ignored)", e);
        }
    }

    /**
     * Cleanup stale Chrome singleton lock files.
     * <p>
     * Mirrors Python's {@code _cleanup_chrome_singleton_files}.
     */
    private void cleanupSingletonFiles(java.nio.file.Path userDataDir) {
        String[] singletonFiles = {"SingletonLock", "SingletonSocket", "SingletonCookie"};
        for (String name : singletonFiles) {
            java.nio.file.Path target = userDataDir.resolve(name);
            try {
                if (target.toFile().exists()) {
                    target.toFile().delete();
                }
            } catch (Exception e) {
                LOG.debug("[ManagedBrowser] cleanup singleton file failed (ignored)", e);
            }
        }
    }

    /**
     * Wait for CDP endpoint to be ready.
     * <p>
     * Mirrors Python's {@code _is_endpoint_ready} with polling.
     */
    private String waitForCdpEndpoint(int port, double timeoutSeconds) throws InterruptedException {
        String cdpUrl = "http://127.0.0.1:" + port;
        String versionUrl = cdpUrl + "/json/version";
        long startTime = System.currentTimeMillis();
        long timeoutMs = (long) (timeoutSeconds * 1000);

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                java.net.URL url = new java.net.URL(versionUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1500);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    return cdpUrl;
                }
            } catch (Exception e) {
                // Endpoint not ready yet, wait and retry
            }
            Thread.sleep(500);
        }

        throw new RuntimeException("CDP endpoint not ready after " + timeoutSeconds + " seconds");
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