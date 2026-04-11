/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.sdk.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal stdio MCP fixture that proves command/args/env/cwd are forwarded intact.
 */
public final class OfficialMcpFixtureMain {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private OfficialMcpFixtureMain() {
    }

    public static void main(String[] args) throws Exception {
        String token = System.getenv("STDIO_FIXTURE_TOKEN");
        String cwd = Path.of(".").toAbsolutePath().normalize().toString();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            while (true) {
                Map<String, Object> request = readFrame(input);
                if (request == null) {
                    return;
                }
                String method = String.valueOf(request.get("method"));
                if ("notifications/initialized".equals(method)) {
                    continue;
                }
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("jsonrpc", "2.0");
                response.put("id", request.get("id"));
                response.put("result", switch (method) {
                    case "initialize" -> Map.of(
                            "protocolVersion", "2024-11-05",
                            "serverInfo", Map.of("name", "OfficialMcpFixtureMain", "version", "1.0.0"),
                            "capabilities", Map.of("tools", Map.of())
                    );
                    case "tools/list" -> Map.of(
                            "tools", List.of(Map.of(
                                    "name", "fixture_stdio_tool",
                                    "description", "token=" + token + ";cwd=" + cwd + ";args=" + String.join(",", args),
                                    "inputSchema", Map.of("type", "object", "properties", Map.of())
                            ))
                    );
                    default -> throw new IllegalArgumentException("Unsupported method: " + request.get("method"));
                });
                writeFrame(output, response);
            }
        }
    }

    private static Map<String, Object> readFrame(BufferedReader input) throws Exception {
        String line = input.readLine();
        if (line == null) {
            return null;
        }
        return MAPPER.readValue(line, MAP_TYPE);
    }

    private static void writeFrame(BufferedWriter output, Map<String, Object> body) throws Exception {
        output.write(MAPPER.writeValueAsString(body));
        output.newLine();
        output.flush();
    }
}
