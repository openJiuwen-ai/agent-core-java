/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.drivers;

import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserProfile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Managed isolated browser launcher for CDP attach.
 *
 * <p>Mirrors Python's {@code ManagedBrowserDriver} in
 * {@code openjiuwen/harness/tools/browser_move/drivers/managed_browser.py}.</p>
 */
public class ManagedBrowserDriver {

    private final BrowserProfile profile;
    private Process process;
    private boolean ownsProcess;

    public ManagedBrowserDriver(BrowserProfile profile) {
        this.profile = profile;
    }

    public BrowserProfile getProfile() {
        return profile;
    }

    public String getCdpEndpoint() {
        String host = blankToDefault(profile.getHost(), "127.0.0.1");
        return "http://" + host + ":" + profile.getDebugPort();
    }

    public boolean ownsProcess() {
        return ownsProcess;
    }

    public boolean isEndpointReady() {
        return isEndpointReadyInternal();
    }

    public String start() {
        return start(20.0, false);
    }

    public String start(double timeoutSeconds, boolean killExisting) {
        if (process != null && process.isAlive() && isEndpointReadyInternal()) {
            return getCdpEndpoint();
        }
        if (isEndpointReadyInternal()) {
            process = null;
            ownsProcess = false;
            return getCdpEndpoint();
        }

        if (killExisting) {
            String userDataDir = Path.of(profile.getUserDataDir()).toAbsolutePath().normalize().toString();
            killChromeByUserDataDir(userDataDir);
            sleepMillis(1500L);
            cleanupChromeSingletonFiles(userDataDir);
        }

        String binary = resolveBinary();
        List<String> args = buildArgs(binary);
        try {
            process = spawnProcess(args);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to launch managed browser: " + ex.getMessage(), ex);
        }
        ownsProcess = true;

        long deadline = System.nanoTime() + (long) (Math.max(1.0, timeoutSeconds) * 1_000_000_000L);
        while (System.nanoTime() < deadline) {
            if (process != null && !process.isAlive()) {
                ownsProcess = false;
                throw new IllegalStateException(
                        "Managed browser process exited early with code " + process.exitValue()
                );
            }
            if (isEndpointReadyInternal()) {
                return getCdpEndpoint();
            }
            sleepMillis(250L);
        }

        ownsProcess = false;
        throw new IllegalStateException(
                "Managed browser CDP endpoint not ready after %.1fs: %s"
                        .formatted(timeoutSeconds, getCdpEndpoint())
        );
    }

    public void stop() {
        stop(5.0);
    }

    public void stop(double waitTimeoutSeconds) {
        Process current = process;
        process = null;
        boolean owned = ownsProcess;
        ownsProcess = false;
        if (current == null || !owned || !current.isAlive()) {
            return;
        }
        current.destroy();
        long deadline = System.nanoTime() + (long) (Math.max(0.5, waitTimeoutSeconds) * 1_000_000_000L);
        while (current.isAlive() && System.nanoTime() < deadline) {
            sleepMillis(50L);
        }
        if (current.isAlive()) {
            current.destroyForcibly();
        }
    }

    protected Process spawnProcess(List<String> args) throws IOException {
        return new ProcessBuilder(args)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    protected boolean isEndpointReadyInternal() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1500))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(getCdpEndpoint() + "/json/version"))
                .GET()
                .timeout(Duration.ofMillis(1500))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            return body.contains("webSocketDebuggerUrl") || body.contains("\"Browser\"");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    protected String resolveBinary() {
        String explicit = trim(profile.getBrowserBinary());
        String explicitError = "";
        if (!explicit.isEmpty()) {
            if (isChromeIdentifier(explicit)) {
                Path candidate = Path.of(explicit).toAbsolutePath().normalize();
                if (Files.exists(candidate)) {
                    return candidate.toString();
                }
                Path onPath = findOnPath(explicit);
                if (onPath != null) {
                    return onPath.toString();
                }
                explicitError = "Configured Chrome binary not found: " + explicit;
            } else {
                explicitError =
                        "Managed mode supports Chrome only. Set BROWSER_MANAGED_BINARY to a Chrome executable.";
            }
        }

        List<String> candidates = candidateChromeBinaries();
        if (candidates.isEmpty()) {
            if (!explicitError.isEmpty()) {
                throw new IllegalStateException(
                        "Chrome needs to be installed. " + explicitError
                                + " No fallback Chrome binary was found on this machine."
                );
            }
            throw new IllegalStateException("Chrome needs to be installed. No Chrome binary was found on this machine.");
        }
        return candidates.get(0);
    }

    protected List<String> buildArgs(String binary) {
        Path userDataDir = Path.of(profile.getUserDataDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(userDataDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create browser profile dir: " + userDataDir, ex);
        }
        String host = blankToDefault(profile.getHost(), "127.0.0.1");
        int port = profile.getDebugPort();
        if (port <= 0) {
            throw new IllegalStateException("Invalid debug port for managed browser profile: " + port);
        }

        List<String> args = new ArrayList<>();
        args.add(binary);
        args.add("--remote-debugging-address=" + host);
        args.add("--remote-debugging-port=" + port);
        args.add("--user-data-dir=" + userDataDir);
        args.add("--no-first-run");
        args.add("--no-default-browser-check");
        args.add("about:blank");
        args.addAll(profile.getExtraArgs());
        return args;
    }

    static String defaultChromeUserDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home", "."));
        if (os.contains("win")) {
            String localAppData = blankToDefault(System.getenv("LOCALAPPDATA"), home.resolve("AppData").resolve("Local").toString());
            return Path.of(localAppData).resolve("Google").resolve("Chrome").resolve("User Data").toString();
        }
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve("Google").resolve("Chrome").toString();
        }
        return home.resolve(".config").resolve("google-chrome").toString();
    }

    static int killChromeByUserDataDir(String userDataDir) {
        int killed = 0;
        String normalized = Path.of(userDataDir).toAbsolutePath().normalize().toString().replace("\\", "/").toLowerCase(Locale.ROOT);
        try {
            if (isWindows()) {
                Process process = new ProcessBuilder(
                        "powershell",
                        "-NoProfile",
                        "-NonInteractive",
                        "-Command",
                        "Get-CimInstance Win32_Process -Filter \"name='chrome.exe'\" | "
                                + "Select-Object CommandLine,ProcessId | ConvertTo-Json -Depth 1"
                ).start();
                String payload = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                for (String line : payload.split("\\R")) {
                    if (!line.toLowerCase(Locale.ROOT).contains(normalized)) {
                        continue;
                    }
                    String pid = extractJsonNumberAfter(line, "\"ProcessId\":");
                    if (!pid.isBlank()) {
                        new ProcessBuilder("taskkill", "/F", "/PID", pid).start().waitFor();
                        killed += 1;
                    }
                }
            } else {
                Process process = new ProcessBuilder("pgrep", "-f", "--user-data-dir=" + userDataDir).start();
                String payload = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                for (String line : payload.split("\\R")) {
                    String pid = line.trim();
                    if (!pid.isEmpty()) {
                        new ProcessBuilder("kill", "-9", pid).start().waitFor();
                        killed += 1;
                    }
                }
            }
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return killed;
    }

    static void cleanupChromeSingletonFiles(String userDataDir) {
        Path base = Path.of(userDataDir).toAbsolutePath().normalize();
        for (String name : List.of("SingletonLock", "SingletonSocket", "SingletonCookie")) {
            try {
                Files.deleteIfExists(base.resolve(name));
            } catch (IOException ignored) {
            }
        }
    }

    static List<String> candidateChromeBinaries() {
        List<String> names = List.of("chrome", "google-chrome", "google-chrome-stable");
        List<String> binaries = new ArrayList<>();
        for (String name : names) {
            Path resolved = findOnPath(name);
            if (resolved != null) {
                binaries.add(resolved.toString());
            }
        }

        List<String> installPaths = new ArrayList<>();
        Path home = Path.of(System.getProperty("user.home", "."));
        if (isWindows()) {
            List<String> roots = List.of(
                    System.getenv("LOCALAPPDATA"),
                    System.getenv("ProgramFiles"),
                    System.getenv("ProgramFiles(x86)"),
                    System.getenv("ProgramW6432"),
                    home.resolve("AppData").resolve("Local").toString()
            );
            for (String root : roots) {
                if (root != null && !root.isBlank()) {
                    installPaths.add(Path.of(root).resolve("Google").resolve("Chrome").resolve("Application")
                            .resolve("chrome.exe").toString());
                }
            }
        } else if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            installPaths.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
            installPaths.add(home.resolve("Applications").resolve("Google Chrome.app")
                    .resolve("Contents").resolve("MacOS").resolve("Google Chrome").toString());
        } else {
            installPaths.add("/usr/bin/google-chrome");
            installPaths.add("/usr/bin/google-chrome-stable");
            installPaths.add("/opt/google/chrome/chrome");
        }
        for (String path : installPaths) {
            if (Files.exists(Path.of(path))) {
                binaries.add(path);
            }
        }

        Set<String> unique = new LinkedHashSet<>(binaries);
        return new ArrayList<>(unique);
    }

    static boolean isChromeIdentifier(String value) {
        String lowered = trim(value).replace("\\", "/").toLowerCase(Locale.ROOT);
        if (lowered.isEmpty()) {
            return false;
        }
        String fileName = Path.of(lowered).getFileName().toString();
        return fileName.contains("chrome");
    }

    private static Path findOnPath(String command) {
        String pathVar = System.getenv("PATH");
        if (pathVar == null || pathVar.isBlank()) {
            return null;
        }
        String[] suffixes = isWindows() ? new String[]{"", ".exe", ".cmd", ".bat"} : new String[]{""};
        for (String root : pathVar.split(isWindows() ? ";" : ":")) {
            if (root.isBlank()) {
                continue;
            }
            for (String suffix : suffixes) {
                Path candidate = Path.of(root).resolve(command + suffix);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    private static String blankToDefault(String value, String fallback) {
        return trim(value).isEmpty() ? fallback : trim(value);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extractJsonNumberAfter(String text, String prefix) {
        int index = text.indexOf(prefix);
        if (index < 0) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = index + prefix.length(); i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        return digits.toString();
    }
}
