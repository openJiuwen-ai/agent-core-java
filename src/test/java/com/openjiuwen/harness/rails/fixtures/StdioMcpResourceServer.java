
package com.openjiuwen.harness.rails.fixtures;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal MCP stdio fixture that speaks NDJSON (one JSON object per line),
 * matching {@link com.openjiuwen.core.foundation.tool.mcp.client.StdioClient}.
 */
public final class StdioMcpResourceServer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StdioMcpResourceServer() {
    }

    public static void main(String[] args) throws Exception {
        BufferedInputStream in = new BufferedInputStream(System.in);
        BufferedOutputStream out = new BufferedOutputStream(System.out);
        while (true) {
            Map<String, Object> request = readFrame(in);
            if (request == null) {
                return;
            }
            Object id = request.get("id");
            String method = String.valueOf(request.get("method"));
            // Notifications have no id; ignore them.
            if (id == null) {
                continue;
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("result", result(method, request.get("params")));
            writeFrame(out, response);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(String method, Object params) {
        if ("initialize".equals(method)) {
            return Map.of("protocolVersion", "2024-11-05", "capabilities", Map.of("resources", Map.of()), "serverInfo",
                    Map.of("name", "stdio-fixture", "version", "1.0.0"));
        }
        if ("tools/list".equals(method)) {
            return Map.of("tools", List.of());
        }
        if ("resources/list".equals(method)) {
            return Map.of("resources", List.of(Map.of("uri", "memory://fixture/readme", "name", "Fixture README",
                    "mimeType", "text/plain", "description", "Local stdio MCP fixture resource")));
        }
        if ("resources/read".equals(method)) {
            String uri = "";
            if (params instanceof Map<?, ?> map && map.get("uri") != null) {
                uri = String.valueOf(map.get("uri"));
            }
            return Map.of("contents",
                    List.of(Map.of("uri", uri, "mimeType", "text/plain", "text", "hello from stdio fixture")));
        }
        if ("tools/call".equals(method)) {
            return Map.of("content", List.of());
        }
        return Map.of();
    }

    private static Map<String, Object> readFrame(BufferedInputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int current;
        while ((current = in.read()) != -1) {
            if (current == '\n') {
                break;
            }
            if (current != '\r') {
                buffer.write(current);
            }
        }
        if (current == -1 && buffer.size() == 0) {
            return null;
        }
        byte[] lineBytes = buffer.toByteArray();
        if (lineBytes.length == 0) {
            return readFrame(in);
        }
        return MAPPER.readValue(lineBytes, new TypeReference<>() {
        });
    }

    private static void writeFrame(BufferedOutputStream out, Map<String, Object> response) throws Exception {
        out.write(MAPPER.writeValueAsBytes(response));
        out.write('\n');
        out.flush();
    }
}
