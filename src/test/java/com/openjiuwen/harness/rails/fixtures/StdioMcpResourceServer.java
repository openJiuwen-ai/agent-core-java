package com.openjiuwen.harness.rails.fixtures;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StdioMcpResourceServer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StdioMcpResourceServer() {
    }

    public static void main(String[] args) throws Exception {
        while (true) {
            Map<String, Object> request = readFrame();
            if (request == null) {
                return;
            }
            Object id = request.get("id");
            String method = String.valueOf(request.get("method"));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("result", result(method, request.get("params")));
            writeFrame(response);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(String method, Object params) {
        if ("initialize".equals(method)) {
            return Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of("resources", Map.of()),
                    "serverInfo", Map.of("name", "stdio-fixture", "version", "1.0.0")
            );
        }
        if ("tools/list".equals(method)) {
            return Map.of("tools", List.of());
        }
        if ("resources/list".equals(method)) {
            return Map.of("resources", List.of(Map.of(
                    "uri", "memory://fixture/readme",
                    "name", "Fixture README",
                    "mimeType", "text/plain",
                    "description", "Local stdio MCP fixture resource"
            )));
        }
        if ("resources/read".equals(method)) {
            String uri = "";
            if (params instanceof Map<?, ?> map && map.get("uri") != null) {
                uri = String.valueOf(map.get("uri"));
            }
            return Map.of("contents", List.of(Map.of(
                    "uri", uri,
                    "mimeType", "text/plain",
                    "text", "hello from stdio fixture"
            )));
        }
        if ("tools/call".equals(method)) {
            return Map.of("content", List.of());
        }
        return Map.of();
    }

    private static Map<String, Object> readFrame() throws Exception {
        int contentLength = -1;
        String line;
        while (!(line = readHeaderLine()).isEmpty()) {
            String lower = line.toLowerCase();
            if (lower.startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        if (contentLength < 0) {
            return null;
        }
        byte[] body = System.in.readNBytes(contentLength);
        if (body.length == 0) {
            return null;
        }
        return MAPPER.readValue(body, new TypeReference<>() {});
    }

    private static String readHeaderLine() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int current;
        while ((current = System.in.read()) != -1) {
            if (current == '\r') {
                int next = System.in.read();
                if (next == '\n') {
                    break;
                }
                buffer.write(current);
                if (next != -1) {
                    buffer.write(next);
                }
                continue;
            }
            buffer.write(current);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void writeFrame(Map<String, Object> response) throws Exception {
        byte[] json = MAPPER.writeValueAsBytes(response);
        String header = "Content-Length: " + json.length + "\r\n\r\n";
        System.out.write(header.getBytes(StandardCharsets.UTF_8));
        System.out.write(json);
        System.out.flush();
    }
}
