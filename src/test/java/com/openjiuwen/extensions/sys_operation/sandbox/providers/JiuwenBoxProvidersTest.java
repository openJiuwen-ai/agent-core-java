/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import com.openjiuwen.core.sys_operation.result.SearchFilesResult;
import com.openjiuwen.core.sys_operation.sandbox.SandboxRegistry;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxGateway;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox} and
 * the related jiuwenbox Python tests in
 * {@code tests/unit_tests/extensions/sys_operation/sandbox/test_jiuwenbox.py},
 * {@code test_jiuwenbox_fs_operation.py},
 * {@code test_jiuwenbox_shell_operation.py}, and
 * {@code test_jiuwenbox_code_operation.py}.
 */
class JiuwenBoxProvidersTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        JiuwenBoxProviderSupport.clearSharedSandbox("http://127.0.0.1");
    }

    @Test
    void sandboxGatewayRegistersBuiltinJiuwenBoxProviders() {
        new SandboxGateway();

        assertSame(JiuwenBoxFsProvider.class, SandboxRegistry.getProviderCls("jiuwenbox", "fs"));
        assertSame(JiuwenBoxShellProvider.class, SandboxRegistry.getProviderCls("jiuwenbox", "shell"));
        assertSame(JiuwenBoxCodeProvider.class, SandboxRegistry.getProviderCls("jiuwenbox", "code"));
    }

    @Test
    void providersShareAutoCreatedSandboxAndUploadPreservedFiles() throws Exception {
        MockJiuwenBoxState state = new MockJiuwenBoxState();
        server = startServer(state);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        Path preserveFile = tempDir.resolve("preserve.txt");
        Files.writeString(preserveFile, "preserved-content", StandardCharsets.UTF_8);

        SandboxGatewayConfig config = gatewayConfig(baseUrl, Map.of(
                "preserve_files_upload", List.of(Map.of(
                        "host_path", preserveFile.toString(),
                        "sandbox_path", "/persisted/preserve.txt",
                        "kind", "file"))));
        SandboxEndpoint endpoint = new SandboxEndpoint(baseUrl, null);

        JiuwenBoxFsProvider fsProvider = new JiuwenBoxFsProvider(endpoint, config);
        JiuwenBoxShellProvider shellProvider = new JiuwenBoxShellProvider(endpoint, config);
        JiuwenBoxCodeProvider codeProvider = new JiuwenBoxCodeProvider(endpoint, config);

        ReadFileResult readResult = fsProvider.readFile(
                "/persisted/preserve.txt",
                "text",
                null,
                null,
                null,
                "utf-8",
                0,
                null).join();
        assertEquals(StatusCode.SUCCESS.getCode(), readResult.getCode());
        assertEquals("preserved-content", readResult.getData().getContent());

        ExecuteCmdResult shellResult = shellProvider.executeCmd(
                "echo shared-sandbox",
                null,
                30,
                null,
                null).join();
        assertEquals(StatusCode.SUCCESS.getCode(), shellResult.getCode());
        assertTrue(shellResult.getData().getStdout().contains("shared-sandbox"));

        ExecuteCodeResult codeResult = codeProvider.executeCode(
                "print('code-shared')",
                "python",
                30,
                null,
                null,
                null).join();
        assertEquals(StatusCode.SUCCESS.getCode(), codeResult.getCode());
        assertTrue(codeResult.getData().getStdout().contains("code-shared"));

        SearchFilesResult searchResult = fsProvider.searchFiles("/persisted", "*.txt", null).join();
        assertEquals(StatusCode.SUCCESS.getCode(), searchResult.getCode());
        assertEquals(List.of("preserve.txt"),
                searchResult.getData().getMatchingFiles().stream().map(item -> item.getName()).toList());
        assertEquals(1, state.createCalls.get(), "fs/shell/code should reuse one shared sandbox");
    }

    @Test
    void shellProviderPreRoutesExcludedCommandToLocalHost() throws Exception {
        MockJiuwenBoxState state = new MockJiuwenBoxState();
        server = startServer(state);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        SandboxGatewayConfig config = gatewayConfig(baseUrl, Map.of("excluded_commands", List.of("echo*")));
        SandboxEndpoint endpoint = new SandboxEndpoint(baseUrl, "existing-sandbox");

        JiuwenBoxShellProvider provider = new JiuwenBoxShellProvider(endpoint, config);
        ExecuteCmdResult result = provider.executeCmd("echo local-pre-route", null, 30, null, null).join();

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().toLowerCase().contains("local-pre-route"));
        assertEquals(0, state.execCalls.get(), "pre-routed command should not hit sandbox exec");
    }

    @Test
    void shellProviderFallsBackToLocalWhenSandboxReturnsNonZeroExit() throws Exception {
        MockJiuwenBoxState state = new MockJiuwenBoxState();
        state.forceNonZeroExit = true;
        server = startServer(state);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        SandboxGatewayConfig config = gatewayConfig(baseUrl, Map.of("fallback_on_failure", true));
        SandboxEndpoint endpoint = new SandboxEndpoint(baseUrl, "existing-sandbox");

        JiuwenBoxShellProvider provider = new JiuwenBoxShellProvider(endpoint, config);
        ExecuteCmdResult result = provider.executeCmd("echo fallback-local", null, 30, null, null).join();

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().toLowerCase().contains("fallback-local"));
        assertEquals(1, state.execCalls.get());
    }

    @Test
    void codeProviderRecreatesSandboxWhenServerReturnsSandboxNotFound() throws Exception {
        MockJiuwenBoxState state = new MockJiuwenBoxState();
        state.staleSandboxId = "stale-sandbox";
        server = startServer(state);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        SandboxGatewayConfig config = gatewayConfig(baseUrl, Map.of());
        SandboxEndpoint endpoint = new SandboxEndpoint(baseUrl, "stale-sandbox");

        JiuwenBoxCodeProvider provider = new JiuwenBoxCodeProvider(endpoint, config);
        ExecuteCodeResult result = provider.executeCode(
                "print('code-ok')",
                "python",
                30,
                null,
                null,
                null).join();

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().contains("code-ok"));
        assertEquals(1, state.createCalls.get(), "sandbox should be recreated exactly once");
        assertEquals(List.of("stale-sandbox"), state.deletedSandboxIds);
    }

    private HttpServer startServer(MockJiuwenBoxState state) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> state.handle(exchange));
        httpServer.start();
        return httpServer;
    }

    private SandboxGatewayConfig gatewayConfig(String baseUrl, Map<String, Object> extraParams) {
        PreDeployLauncherConfig launcherConfig = new PreDeployLauncherConfig(baseUrl);
        launcherConfig.setSandboxType("jiuwenbox");
        launcherConfig.setExtraParams(new LinkedHashMap<>(extraParams));
        return SandboxGatewayConfig.builder()
                .launcherConfig(launcherConfig)
                .timeoutSeconds(30)
                .build();
    }

    private static final class MockJiuwenBoxState {

        private final Map<String, Map<String, byte[]>> sandboxFiles = new ConcurrentHashMap<>();
        private final AtomicInteger createCalls = new AtomicInteger();
        private final AtomicInteger execCalls = new AtomicInteger();
        private final List<String> deletedSandboxIds = new ArrayList<>();

        private volatile boolean forceNonZeroExit;
        private volatile String staleSandboxId;

        void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("/api/v1/sandboxes".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                    handleCreateSandbox(exchange);
                    return;
                }
                if (path.startsWith("/api/v1/sandboxes/") && path.endsWith("/exec")) {
                    handleExec(exchange);
                    return;
                }
                if (path.startsWith("/api/v1/sandboxes/") && path.endsWith("/upload")) {
                    handleUpload(exchange);
                    return;
                }
                if (path.startsWith("/api/v1/sandboxes/") && path.endsWith("/download")) {
                    handleDownload(exchange);
                    return;
                }
                if (path.startsWith("/api/v1/sandboxes/") && path.endsWith("/files")) {
                    handleListFiles(exchange);
                    return;
                }
                if (path.startsWith("/api/v1/sandboxes/") && path.endsWith("/search")) {
                    handleSearch(exchange);
                    return;
                }
                if (path.startsWith("/api/v1/sandboxes/") && "DELETE".equals(exchange.getRequestMethod())) {
                    handleDeleteSandbox(exchange);
                    return;
                }
                if ("/api/v1/timeout".equals(path) && "PUT".equals(exchange.getRequestMethod())) {
                    writeJson(exchange, 200, Map.of("ok", true));
                    return;
                }
                writeJson(exchange, 404, Map.of("error", "not found"));
            } finally {
                exchange.close();
            }
        }

        private void handleCreateSandbox(HttpExchange exchange) throws IOException {
            String sandboxId = "sandbox-" + createCalls.incrementAndGet();
            sandboxFiles.putIfAbsent(sandboxId, new ConcurrentHashMap<>());
            writeJson(exchange, 201, Map.of("id", sandboxId));
        }

        private void handleExec(HttpExchange exchange) throws IOException {
            execCalls.incrementAndGet();
            String sandboxId = sandboxId(exchange.getRequestURI());
            if (sandboxId.equals(staleSandboxId)) {
                writeJson(exchange, 404, Map.of("error", "Sandbox '" + sandboxId + "' not found"));
                return;
            }
            JsonNode body = readJson(exchange);
            List<String> command = new ArrayList<>();
            for (JsonNode node : body.path("command")) {
                command.add(node.asText());
            }
            if (forceNonZeroExit) {
                writeJson(exchange, 200, Map.of("stdout", "", "stderr", "sandbox failed", "exit_code", 5));
                return;
            }
            String joined = String.join(" ", command);
            String stdout;
            if (joined.contains("code-ok")) {
                stdout = "code-ok\n";
            } else if (joined.contains("code-shared")) {
                stdout = "code-shared\n";
            } else if (joined.contains("shared-sandbox")) {
                stdout = "shared-sandbox\n";
            } else {
                stdout = "ok\n";
            }
            writeJson(exchange, 200, Map.of("stdout", stdout, "stderr", "", "exit_code", 0));
        }

        private void handleUpload(HttpExchange exchange) throws IOException {
            String sandboxId = sandboxId(exchange.getRequestURI());
            String sandboxPath = firstQuery(exchange.getRequestURI(), "sandbox_path");
            sandboxFiles.computeIfAbsent(sandboxId, ignored -> new ConcurrentHashMap<>())
                    .put(sandboxPath, extractMultipartFileBytes(exchange));
            writeJson(exchange, 200, Map.of("ok", true));
        }

        private void handleDownload(HttpExchange exchange) throws IOException {
            String sandboxId = sandboxId(exchange.getRequestURI());
            String sandboxPath = firstQuery(exchange.getRequestURI(), "sandbox_path");
            byte[] content = sandboxFiles.getOrDefault(sandboxId, Map.of()).get(sandboxPath);
            if (content == null) {
                writeJson(exchange, 404, Map.of("error", "File not found: " + sandboxPath));
                return;
            }
            exchange.getResponseHeaders().add("content-type", "application/octet-stream");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
        }

        private void handleListFiles(HttpExchange exchange) throws IOException {
            String sandboxId = sandboxId(exchange.getRequestURI());
            String sandboxPath = firstQuery(exchange.getRequestURI(), "sandbox_path");
            boolean includeFiles = Boolean.parseBoolean(firstQuery(exchange.getRequestURI(), "include_files"));
            boolean includeDirs = Boolean.parseBoolean(firstQuery(exchange.getRequestURI(), "include_dirs"));
            boolean recursive = Boolean.parseBoolean(firstQuery(exchange.getRequestURI(), "recursive"));
            List<Map<String, Object>> items = buildItems(
                    sandboxFiles.getOrDefault(sandboxId, Map.of()),
                    sandboxPath,
                    recursive,
                    includeFiles,
                    includeDirs);
            writeJson(exchange, 200, Map.of("items", items));
        }

        private void handleSearch(HttpExchange exchange) throws IOException {
            String sandboxId = sandboxId(exchange.getRequestURI());
            String sandboxPath = firstQuery(exchange.getRequestURI(), "sandbox_path");
            String pattern = firstQuery(exchange.getRequestURI(), "pattern");
            List<String> excludes = query(exchange.getRequestURI()).getOrDefault("exclude_patterns", List.of());
            List<Map<String, Object>> items = buildItems(
                            sandboxFiles.getOrDefault(sandboxId, Map.of()),
                            sandboxPath,
                            true,
                            true,
                            false).stream()
                    .filter(item -> matchesGlob((String) item.get("name"), pattern))
                    .filter(item -> excludes.stream().noneMatch(exclude -> matchesGlob((String) item.get("name"), exclude)))
                    .sorted(Comparator.comparing(item -> String.valueOf(item.get("name"))))
                    .toList();
            writeJson(exchange, 200, Map.of("items", items));
        }

        private void handleDeleteSandbox(HttpExchange exchange) throws IOException {
            String sandboxId = sandboxId(exchange.getRequestURI());
            deletedSandboxIds.add(sandboxId);
            sandboxFiles.remove(sandboxId);
            exchange.sendResponseHeaders(204, -1);
        }

        private List<Map<String, Object>> buildItems(
                Map<String, byte[]> files,
                String rootPath,
                boolean recursive,
                boolean includeFiles,
                boolean includeDirs) {
            String normalizedRoot = rootPath.endsWith("/") ? rootPath : rootPath + "/";
            List<Map<String, Object>> items = new ArrayList<>();
            List<String> seenDirs = new ArrayList<>();
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String filePath = entry.getKey();
                if (!filePath.startsWith(normalizedRoot) && !filePath.equals(rootPath)) {
                    continue;
                }
                String relative = filePath.equals(rootPath) ? Path.of(filePath).getFileName().toString()
                        : filePath.substring(normalizedRoot.length());
                if (!recursive && relative.contains("/")) {
                    String firstDir = relative.substring(0, relative.indexOf('/'));
                    String dirPath = normalizedRoot + firstDir;
                    if (includeDirs && !seenDirs.contains(dirPath)) {
                        seenDirs.add(dirPath);
                        items.add(item(firstDir, dirPath, true, 0));
                    }
                    continue;
                }
                if (includeFiles) {
                    items.add(item(Path.of(filePath).getFileName().toString(), filePath, false, entry.getValue().length));
                }
                if (includeDirs) {
                    String[] segments = relative.split("/");
                    String current = rootPath;
                    for (int index = 0; index < segments.length - 1; index++) {
                        current = current.endsWith("/") ? current + segments[index] : current + "/" + segments[index];
                        if (!seenDirs.contains(current)) {
                            seenDirs.add(current);
                            items.add(item(segments[index], current, true, 0));
                        }
                    }
                }
            }
            items.sort(Comparator.comparing(item -> String.valueOf(item.get("path"))));
            return items;
        }

        private Map<String, Object> item(String name, String path, boolean isDirectory, int size) {
            return Map.of(
                    "name", name,
                    "path", path,
                    "is_directory", isDirectory,
                    "size", size,
                    "modified_time", "2026-06-09T00:00:00Z");
        }

        private byte[] extractMultipartFileBytes(HttpExchange exchange) throws IOException {
            String contentType = exchange.getRequestHeaders().getFirst("content-type");
            byte[] body = readAllBytes(exchange.getRequestBody());
            if (contentType == null || !contentType.contains("boundary=")) {
                return body;
            }
            String boundary = "--" + contentType.substring(contentType.indexOf("boundary=") + "boundary=".length());
            byte[] boundaryBytes = boundary.getBytes(StandardCharsets.UTF_8);
            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), 0);
            if (headerEnd < 0) {
                return body;
            }
            int contentStart = headerEnd + 4;
            int contentEnd = indexOf(body, boundaryBytes, contentStart) - 2;
            if (contentEnd < contentStart) {
                return new byte[0];
            }
            byte[] result = new byte[contentEnd - contentStart];
            System.arraycopy(body, contentStart, result, 0, result.length);
            return result;
        }

        private JsonNode readJson(HttpExchange exchange) throws IOException {
            try (InputStream inputStream = exchange.getRequestBody()) {
                return JSON.readTree(inputStream);
            }
        }

        private byte[] readAllBytes(InputStream inputStream) throws IOException {
            try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        }

        private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
            byte[] bytes = JSON.writeValueAsBytes(body);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        private String sandboxId(URI uri) {
            String[] segments = uri.getPath().split("/");
            return segments[4];
        }

        private String firstQuery(URI uri, String key) {
            return query(uri).getOrDefault(key, List.of("")).stream().findFirst().orElse("");
        }

        private Map<String, List<String>> query(URI uri) {
            Map<String, List<String>> values = new LinkedHashMap<>();
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) {
                return values;
            }
            for (String part : rawQuery.split("&")) {
                String[] kv = part.split("=", 2);
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            }
            return values;
        }

        private boolean matchesGlob(String text, String pattern) {
            StringBuilder regex = new StringBuilder("^");
            for (int index = 0; index < pattern.length(); index++) {
                char ch = pattern.charAt(index);
                switch (ch) {
                    case '*' -> regex.append(".*");
                    case '?' -> regex.append('.');
                    case '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> regex.append('\\').append(ch);
                    default -> regex.append(ch);
                }
            }
            regex.append('$');
            return text.matches(regex.toString());
        }

        private int indexOf(byte[] source, byte[] target, int fromIndex) {
            outer:
            for (int index = fromIndex; index <= source.length - target.length; index++) {
                for (int offset = 0; offset < target.length; offset++) {
                    if (source[index + offset] != target[offset]) {
                        continue outer;
                    }
                }
                return index;
            }
            return -1;
        }
    }
}
