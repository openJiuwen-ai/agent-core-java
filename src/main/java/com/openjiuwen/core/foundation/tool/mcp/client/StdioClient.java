/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stdio transport MCP client using newline-delimited JSON-RPC (NDJSON),
 * compatible with the MCP Python SDK's stdio transport protocol.
 * 
 * @since 0.1.7
 */
public class StdioClient implements McpClient {
    private static final LoggerProtocol LOG = Loggers.MCP;

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;

    /**
     * AtomicLong.
     * 
     * @since 0.1.7
     */
    private final AtomicLong requestCounter = new AtomicLong();

    private Process process;
    private BufferedInputStream stdout;
    private BufferedOutputStream stdin;
    private Thread stderrDrainer;

    /**
     * 构造函数.
     * 
     * @param config MCP服务器配置
     * @since 0.1.7
     */
    public StdioClient(McpServerConfig config) {
        this.config = config;
    }

    /**
     * connect.
     * 
     * @param retryTimes retryTimes
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
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
        startStderrDrainer();
        long deadlineMs = computeDeadlineMs(timeout);
        try {
            request("initialize",
                    Map.of("protocolVersion", "2024-11-05", "clientInfo",
                            Map.of("name", "agent-core-java", "version", "0.1.7"), "capabilities", Map.of()),
                    deadlineMs);
            // Send initialized notification only after successful initialize (per MCP spec 2024-11-05)
            writeLine(MAPPER.writeValueAsBytes(
                    Map.of("jsonrpc", "2.0", "method", "notifications/initialized", "params", Map.of())));
        } catch (JsonProcessingException e) {
            LOG.warn("MCP STDIO initialize request failed: {}", e.getMessage());
        }
        return true;
    }

    /**
     * disconnect.
     * 
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean disconnect(float timeout) throws Exception {
        stopStderrDrainer();
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

    /**
     * listTools.
     * 
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<Object> listTools(float timeout) throws Exception {
        long deadlineMs = computeDeadlineMs(timeout);
        Map<String, Object> result = request("tools/list", Map.of(), deadlineMs);
        List<Object> tools = new ArrayList<>();
        Object rawTools = result.get("tools");
        if (rawTools instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> map) {
                    Object name = map.get("name");
                    Object description = map.get("description");
                    tools.add(McpToolCard.builder().name(name != null ? String.valueOf(name) : "")
                            .description(description != null ? String.valueOf(description) : "")
                            .serverName(config.getServerName()).serverId(config.getServerId())
                            .inputParams(castMap(map.get("inputSchema"))).build());
                }
            }
        }
        return tools;
    }

    /**
     * listResources.
     * 
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<Object> listResources(float timeout) throws Exception {
        long deadlineMs = computeDeadlineMs(timeout);
        Map<String, Object> result = request("resources/list", Map.of(), deadlineMs);
        List<Object> resources = new ArrayList<>();
        Object rawResources = result.get("resources");
        if (rawResources instanceof List<?> list) {
            resources.addAll(list);
        }
        return resources;
    }

    /**
     * readResource.
     * 
     * @param uri uri
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<Object> readResource(String uri, float timeout) throws Exception {
        long deadlineMs = computeDeadlineMs(timeout);
        Map<String, Object> result = request("resources/read", Map.of("uri", uri), deadlineMs);
        List<Object> contents = new ArrayList<>();
        Object rawContents = result.get("contents");
        if (rawContents instanceof List<?> list) {
            contents.addAll(list);
        }
        return contents;
    }

    /**
     * callTool.
     * 
     * @param toolName toolName
     * @param arguments arguments
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        long deadlineMs = computeDeadlineMs(timeout);
        Map<String, Object> result = request("tools/call",
                Map.of("name", toolName, "arguments", arguments == null ? Map.of() : arguments), deadlineMs);
        Object content = result.get("content");
        if (content instanceof List<?> list && !list.isEmpty()) {
            List<String> textParts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text != null) {
                        textParts.add(String.valueOf(text));
                    }
                }
            }
            if (textParts.size() == 1) {
                return textParts.get(0);
            }
            if (!textParts.isEmpty()) {
                return String.join("\n", textParts);
            }
        }
        return result;
    }

    /**
     * getToolInfo.
     * 
     * @param toolName toolName
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        for (Object tool : listTools(timeout)) {
            if (tool instanceof McpToolCard card && toolName.equals(card.getName())) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    /**
     * getServerPath.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    /**
     * request.
     * 
     * @param method method
     * @param params params
     * @param deadlineMs deadlineMs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private synchronized Map<String, Object> request(String method, Map<String, Object> params, long deadlineMs)
            throws Exception {
        long requestId = requestCounter.incrementAndGet();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", requestId);
        body.put("method", method);
        body.put("params", params);
        writeLine(MAPPER.writeValueAsBytes(body));

        while (true) {
            checkDeadline(deadlineMs);
            Map<String, Object> frame = readLine(deadlineMs);
            Object responseId = frame.get("id");
            if (!(responseId instanceof Number number) || number.longValue() != requestId) {
                // Skip notifications or responses for other requests
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

    /**
     * Write a JSON line (NDJSON: json + '\n').
     * 
     * @param jsonBytes jsonBytes
     * @throws Exception 写入异常
     * @since 0.1.7
     */
    private void writeLine(byte[] jsonBytes) throws Exception {
        stdin.write(jsonBytes);
        stdin.write('\n');
        stdin.flush();
    }

    /**
     * readLine.
     * 
     * @param deadlineMs deadlineMs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private Map<String, Object> readLine(long deadlineMs) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            checkDeadline(deadlineMs);
            checkProcessAlive();
            int current = stdout.read();
            if (current == -1) {
                throw new IOException("MCP STDIO subprocess stream closed unexpectedly");
            }
            if (current == '\n') {
                break;
            }
            if (current != '\r') {
                buffer.write(current);
            }
        }
        byte[] lineBytes = buffer.toByteArray();
        if (lineBytes.length == 0) {
            return readLine(deadlineMs);
        }
        return MAPPER.readValue(lineBytes, new TypeReference<>() {
        });
    }

    /**
     * computeDeadlineMs.
     * 
     * @param timeout timeout
     * @return the result
     * @since 0.1.7
     */
    private static long computeDeadlineMs(float timeout) {
        if (Float.compare(timeout, McpServerConfig.NO_TIMEOUT) == 0) {
            // NO_TIMEOUT sentinel: 0 means no deadline enforcement
            return 0L;
        }
        return Float.compare(timeout, 0) > 0
                ? System.currentTimeMillis()
                        + BigDecimal.valueOf(timeout).multiply(BigDecimal.valueOf(1000L)).longValue()
                : System.currentTimeMillis() + 30_000L;
    }

    /**
     * checkDeadline.
     * 
     * @param deadlineMs deadlineMs
     * @throws SocketTimeoutException SocketTimeoutException
     * @since 0.1.7
     */
    private void checkDeadline(long deadlineMs) throws SocketTimeoutException {
        // deadlineMs == 0 means NO_TIMEOUT, skip deadline check entirely
        if (deadlineMs != 0L && System.currentTimeMillis() > deadlineMs) {
            throw new SocketTimeoutException("MCP STDIO read timeout");
        }
    }

    /**
     * checkProcessAlive.
     * 
     * @throws IOException IOException
     * @since 0.1.7
     */
    private void checkProcessAlive() throws IOException {
        if (process != null && !process.isAlive()) {
            throw new IOException("MCP STDIO subprocess terminated unexpectedly");
        }
    }

    /**
     * startStderrDrainer.
     * 
     * @since 0.1.7
     */
    private void startStderrDrainer() {
        this.stderrDrainer = new Thread(() -> {
            try {
                byte[] buf = new byte[4096];
                while (process.getErrorStream().read(buf) != -1) {
                    // Drain stderr to prevent buffer overflow blocking the subprocess
                }
            } catch (IOException e) {
                LOG.warn("MCP STDIO stderr drain interrupted: {}", e.getMessage());
            }
        }, "mcp-stdio-stderr-drain-" + config.getServerId());
        this.stderrDrainer.setUncaughtExceptionHandler((t, e) -> {
            LOG.warn("MCP STDIO stderr drainer thread {} encountered uncaught exception: {}", t.getName(),
                    e.getMessage());
        });
        this.stderrDrainer.setDaemon(true);
        this.stderrDrainer.start();
    }

    /**
     * stopStderrDrainer.
     * 
     * @since 0.1.7
     */
    private void stopStderrDrainer() {
        if (stderrDrainer != null) {
            stderrDrainer.interrupt();
            try {
                stderrDrainer.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stderrDrainer = null;
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * castMap.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
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
