/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stdio transport MCP client using content-length framed JSON-RPC.
 */
public class StdioClient implements McpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final AtomicLong requestCounter = new AtomicLong();

    private Process process;
    private BufferedInputStream stdout;
    private BufferedOutputStream stdin;

    public StdioClient(McpServerConfig config) {
        this.config = config;
    }

    @Override
    public boolean connect(int retryTimes, float timeout) throws Exception {
        String command = config.getParams().containsKey("command")
                ? String.valueOf(config.getParams().get("command"))
                : config.getServerPath();
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        Object argsObj = config.getParams().get("args");
        if (argsObj instanceof List<?> args) {
            for (Object arg : args) {
                commandLine.add(String.valueOf(arg));
            }
        }
        processBuilder.command(commandLine);
        Object envObj = config.getParams().get("env");
        if (envObj instanceof Map<?, ?> env) {
            for (Map.Entry<?, ?> entry : env.entrySet()) {
                processBuilder.environment().put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        Object cwd = config.getParams().get("cwd");
        if (cwd != null) {
            processBuilder.directory(new java.io.File(String.valueOf(cwd)));
        }

        this.process = processBuilder.start();
        this.stdout = new BufferedInputStream(process.getInputStream());
        this.stdin = new BufferedOutputStream(process.getOutputStream());
        try {
            request("initialize", Map.of(
                    "protocolVersion", "2024-11-05",
                    "clientInfo", Map.of("name", "agent-core-java", "version", "0.1.7"),
                    "capabilities", Map.of()
            ), timeout);
        } catch (Exception ignored) {
            // Some local MCP servers do not require an explicit initialize response.
        }
        return true;
    }

    @Override
    public boolean disconnect(float timeout) throws Exception {
        if (stdin != null) {
            stdin.close();
        }
        if (stdout != null) {
            stdout.close();
        }
        if (process != null) {
            process.destroy();
            process.waitFor(1, TimeUnit.SECONDS);
        }
        return true;
    }

    @Override
    public List<Object> listTools(float timeout) throws Exception {
        Map<String, Object> result = request("tools/list", Map.of(), timeout);
        List<Object> tools = new ArrayList<>();
        Object rawTools = result.get("tools");
        if (rawTools instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> map) {
                    Object name = map.get("name");
                    Object description = map.get("description");
                    tools.add(McpToolCard.builder()
                            .name(name != null ? String.valueOf(name) : "")
                            .description(description != null ? String.valueOf(description) : "")
                            .serverName(config.getServerName())
                            .serverId(config.getServerId())
                            .inputParams(castMap(map.get("inputSchema")))
                            .build());
                }
            }
        }
        return tools;
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        return request("tools/call", Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments), timeout);
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        for (Object tool : listTools(timeout)) {
            if (tool instanceof McpToolCard card && toolName.equals(card.getName())) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    private synchronized Map<String, Object> request(String method, Map<String, Object> params, float timeout) throws Exception {
        long requestId = requestCounter.incrementAndGet();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", requestId);
        body.put("method", method);
        body.put("params", params);
        writeFrame(MAPPER.writeValueAsBytes(body));

        while (true) {
            Map<String, Object> frame = readFrame();
            Object responseId = frame.get("id");
            if (!(responseId instanceof Number number) || number.longValue() != requestId) {
                continue;
            }
            if (frame.containsKey("error")) {
                throw new IllegalStateException(String.valueOf(frame.get("error")));
            }
            Object result = frame.get("result");
            if (result instanceof Map<?, ?> map) {
                return castMap(map);
            }
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("result", result);
            return wrapped;
        }
    }

    private void writeFrame(byte[] jsonBytes) throws Exception {
        String header = "Content-Length: " + jsonBytes.length + "\r\n\r\n";
        stdin.write(header.getBytes(StandardCharsets.UTF_8));
        stdin.write(jsonBytes);
        stdin.flush();
    }

    private Map<String, Object> readFrame() throws Exception {
        int contentLength = -1;
        String line;
        while (!(line = readHeaderLine()).isEmpty()) {
            String lower = line.toLowerCase();
            if (lower.startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        if (contentLength < 0) {
            throw new IllegalStateException("Missing Content-Length in stdio MCP response");
        }
        byte[] body = stdout.readNBytes(contentLength);
        return MAPPER.readValue(body, new TypeReference<>() {
        });
    }

    private String readHeaderLine() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int current;
        while ((current = stdout.read()) != -1) {
            if (current == '\r') {
                int next = stdout.read();
                if (next == '\n') {
                    break;
                }
                buffer.write(current);
                buffer.write(next);
                continue;
            }
            buffer.write(current);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
