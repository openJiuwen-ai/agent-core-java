/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAPI-file backed MCP-style client.
 * <p>
 * Parses OpenAPI spec files (JSON/YAML) and converts each route into an MCP tool card
 * with proper parameter schemas, descriptions, and output schemas. Mirrors Python's
 * {@code OpenApiClient} capabilities including {@code load_conf()}, {@code ToolManager},
 * parameter schema extraction, and output schema extraction.
 */
public class OpenApiClient implements McpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, Operation> operations = new LinkedHashMap<>();
    private final Map<String, Integer> usedNames = new HashMap<>();

    public OpenApiClient(McpServerConfig config) {
        this.config = config;
    }

    @Override
    public boolean connect(int retryTimes, float timeout) throws Exception {
        operations.clear();
        usedNames.clear();
        for (String rawPath : config.getServerPath().split(",")) {
            Map<String, Object> spec = loadConf(rawPath.trim());
            loadSpec(spec, rawPath.trim());
        }
        return true;
    }

    @Override
    public boolean disconnect(float timeout) {
        operations.clear();
        usedNames.clear();
        return true;
    }

    @Override
    public List<Object> listTools(float timeout) {
        return new ArrayList<>(operations.values().stream().map(Operation::card).toList());
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        Operation operation = operations.get(toolName);
        if (operation == null) {
            throw new IllegalArgumentException("OpenAPI tool not found: " + toolName);
        }
        String url = resolveUrl(operation, arguments);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");
        if (timeout != McpServerConfig.NO_TIMEOUT && timeout > 0) {
            builder.timeout(Duration.ofMillis((long) (timeout * 1000)));
        }

        // Separate path params from body params
        Map<String, Object> bodyArgs = arguments == null
                ? Map.of()
                : new LinkedHashMap<>(arguments);
        // Remove path parameters from body
        if (operation.pathParams != null) {
            for (String pp : operation.pathParams) {
                bodyArgs.remove(pp);
            }
        }

        if ("GET".equals(operation.method)) {
            // For GET, append remaining args as query params
            if (!bodyArgs.isEmpty()) {
                StringBuilder sb = new StringBuilder(url);
                sb.append(url.contains("?") ? "&" : "?");
                boolean first = true;
                for (var entry : bodyArgs.entrySet()) {
                    if (!first) {
                        sb.append("&");
                    }
                    sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                            .append("=")
                            .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                    first = false;
                }
                builder.uri(URI.create(sb.toString()));
            }
            builder.GET();
        } else {
            builder.method(operation.method,
                    HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(bodyArgs), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) {
        return Optional.ofNullable(operations.get(toolName)).map(Operation::card);
    }

    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    /**
     * Load and parse an OpenAPI spec from a file path.
     * Mirrors Python's {@code load_conf()} function.
     *
     * @param filePath path to the JSON/YAML file
     * @return parsed spec as a Map
     * @throws Exception if file doesn't exist, is a symlink, or has unsupported format
     */
    public static Map<String, Object> loadConf(String filePath) throws Exception {
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Path not exists: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("The path is not a file: " + path);
        }
        if (Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Symbolic link not allowed: " + path);
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        String suffix = filePath.toLowerCase();

        Map<String, Object> data;
        if (suffix.endsWith(".json")) {
            data = MAPPER.readValue(content, new TypeReference<>() {});
        } else if (suffix.endsWith(".yaml") || suffix.endsWith(".yml")) {
            data = new Yaml().load(content);
        } else {
            throw new IllegalArgumentException(
                    "Only supports .json/.yaml/.yml, current extension: " + suffix);
        }

        if (data == null || !(data instanceof Map)) {
            throw new IllegalArgumentException("Only support dict type for OpenAPI spec");
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private void loadSpec(Map<String, Object> spec, String sourcePath) {
        String baseUrl = extractBaseUrl(spec);
        Map<String, Object> paths = spec.get("paths") instanceof Map<?, ?> map ? castMap(map) : Map.of();
        Map<String, Object> components = spec.get("components") instanceof Map<?, ?> map ? castMap(map) : Map.of();
        Map<String, Object> componentSchemas = components.get("schemas") instanceof Map<?, ?> map
                ? castMap(map) : Map.of();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            if (!(pathEntry.getValue() instanceof Map<?, ?> methodsMap)) {
                continue;
            }
            for (Map.Entry<String, Object> methodEntry : castMap(methodsMap).entrySet()) {
                String httpMethod = methodEntry.getKey().toUpperCase();
                if (!(methodEntry.getValue() instanceof Map<?, ?> operationMap)) {
                    continue;
                }
                Map<String, Object> operation = castMap(operationMap);
                Object operationId = operation.get("operationId");
                String baseName = operationId != null
                        ? String.valueOf(operationId).split("__")[0]
                        : (operation.get("summary") != null
                        ? String.valueOf(operation.get("summary"))
                        : httpMethod + "_" + pathEntry.getKey().replace('/', '_'));
                // Truncate to 64 characters
                if (baseName.length() > 64) {
                    baseName = baseName.substring(0, 64);
                }
                String toolName = getUniqueName(baseName);

                // Build input parameter schema from parameters + requestBody
                Map<String, Object> inputSchema = buildInputSchema(
                        operation, pathEntry.getKey(), componentSchemas);

                // Extract output schema from responses
                Map<String, Object> outputSchema = extractOutputSchema(operation, componentSchemas);

                // Build description
                String description = buildDescription(operation);

                // Collect path parameter names
                List<String> pathParams = extractPathParamNames(pathEntry.getKey());

                operations.put(toolName, new Operation(
                        httpMethod,
                        pathEntry.getKey(),
                        baseUrl,
                        pathParams,
                        McpToolCard.builder()
                                .name(toolName)
                                .description(description)
                                .serverName(config.getServerName())
                                .serverId(config.getServerId())
                                .inputParams(inputSchema)
                                .build()
                ));
            }
        }
    }

    private String getUniqueName(String name) {
        int count = usedNames.merge(name, 1, Integer::sum);
        if (count == 1) {
            return name;
        }
        return name + "_" + count;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildInputSchema(Map<String, Object> operation,
                                                  String pathTemplate,
                                                  Map<String, Object> componentSchemas) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        // Process parameters (path, query, header)
        Object paramsObj = operation.get("parameters");
        if (paramsObj instanceof List<?> params) {
            for (Object paramObj : params) {
                if (!(paramObj instanceof Map<?, ?> paramMap)) {
                    continue;
                }
                Map<String, Object> param = castMap(paramMap);
                String paramName = String.valueOf(param.getOrDefault("name", ""));
                if (paramName.isEmpty()) {
                    continue;
                }
                Map<String, Object> schema = param.get("schema") instanceof Map<?, ?> s
                        ? resolveRef(castMap(s), componentSchemas) : new LinkedHashMap<>(Map.of("type", "string"));
                Object desc = param.get("description");
                if (desc != null) {
                    schema.put("description", String.valueOf(desc));
                }
                properties.put(paramName, schema);
                if (Boolean.TRUE.equals(param.get("required"))) {
                    required.add(paramName);
                }
            }
        }

        // Process requestBody
        Object requestBodyObj = operation.get("requestBody");
        if (requestBodyObj instanceof Map<?, ?> requestBody) {
            Map<String, Object> bodyMap = castMap(requestBody);
            Object contentObj = bodyMap.get("content");
            if (contentObj instanceof Map<?, ?> contentMap) {
                for (var contentEntry : castMap(contentMap).entrySet()) {
                    if (contentEntry.getValue() instanceof Map<?, ?> mediaType) {
                        Map<String, Object> mtMap = castMap(mediaType);
                        Object schemaObj = mtMap.get("schema");
                        if (schemaObj instanceof Map<?, ?> schemaMap) {
                            Map<String, Object> resolved = resolveRef(castMap(schemaMap), componentSchemas);
                            // Flatten body properties into the top-level schema
                            Object bodyProps = resolved.get("properties");
                            if (bodyProps instanceof Map<?, ?> bpMap) {
                                for (var bp : castMap(bpMap).entrySet()) {
                                    Object propVal = bp.getValue();
                                    Map<String, Object> propSchema = (propVal instanceof Map<?, ?> pm)
                                            ? resolveRef(castMap(pm), componentSchemas)
                                            : new LinkedHashMap<>(Map.of("type", "string"));
                                    properties.put(bp.getKey(), propSchema);
                                }
                            }
                            Object bodyRequired = resolved.get("required");
                            if (bodyRequired instanceof List<?> reqList) {
                                for (Object r : reqList) {
                                    required.add(String.valueOf(r));
                                }
                            }
                        }
                        break; // Use first content type
                    }
                }
            }
        }

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }
        return inputSchema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOutputSchema(Map<String, Object> operation,
                                                     Map<String, Object> componentSchemas) {
        Object responsesObj = operation.get("responses");
        if (!(responsesObj instanceof Map<?, ?> responses)) {
            return Map.of();
        }
        // Look for 200 or 201 response first, then any 2xx
        Map<String, Object> responseMap = castMap(responses);
        Map<String, Object> successResponse = null;
        for (String code : List.of("200", "201")) {
            if (responseMap.get(code) instanceof Map<?, ?> r) {
                successResponse = castMap(r);
                break;
            }
        }
        if (successResponse == null) {
            for (var entry : responseMap.entrySet()) {
                if (entry.getKey().startsWith("2") && entry.getValue() instanceof Map<?, ?> r) {
                    successResponse = castMap(r);
                    break;
                }
            }
        }
        if (successResponse == null) {
            return Map.of();
        }
        Object contentObj = successResponse.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) {
            return Map.of();
        }
        for (var contentEntry : castMap(contentMap).entrySet()) {
            if (contentEntry.getValue() instanceof Map<?, ?> mediaType) {
                Object schemaObj = castMap(mediaType).get("schema");
                if (schemaObj instanceof Map<?, ?> schemaMap) {
                    return resolveRef(castMap(schemaMap), componentSchemas);
                }
            }
        }
        return Map.of();
    }

    private static String buildDescription(Map<String, Object> operation) {
        Object description = operation.get("description");
        Object summary = operation.get("summary");
        if (description != null && !String.valueOf(description).isBlank()) {
            return String.valueOf(description);
        }
        if (summary != null && !String.valueOf(summary).isBlank()) {
            return String.valueOf(summary);
        }
        return "";
    }

    private static List<String> extractPathParamNames(String pathTemplate) {
        List<String> params = new ArrayList<>();
        int start = pathTemplate.indexOf('{');
        while (start >= 0) {
            int end = pathTemplate.indexOf('}', start);
            if (end > start) {
                params.add(pathTemplate.substring(start + 1, end));
            }
            start = pathTemplate.indexOf('{', end + 1);
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveRef(Map<String, Object> schema, Map<String, Object> componentSchemas) {
        Object ref = schema.get("$ref");
        if (ref instanceof String refStr) {
            // Handle "#/components/schemas/ModelName"
            String[] parts = refStr.split("/");
            String refKey = parts[parts.length - 1];
            Object resolved = componentSchemas.get(refKey);
            if (resolved instanceof Map<?, ?> resolvedMap) {
                return new LinkedHashMap<>(castMap(resolvedMap));
            }
        }
        return new LinkedHashMap<>(schema);
    }

    private String resolveUrl(Operation operation, Map<String, Object> arguments) {
        String path = operation.path;
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            path = path.replace("{" + entry.getKey() + "}",
                    URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return operation.baseUrl + path;
    }

    @SuppressWarnings("unchecked")
    private String extractBaseUrl(Map<String, Object> spec) {
        Object serversObj = spec.get("servers");
        if (serversObj instanceof List<?> servers && !servers.isEmpty()
                && servers.get(0) instanceof Map<?, ?> server) {
            Object url = server.get("url");
            if (url != null) {
                return String.valueOf(url).replaceAll("/+$", "");
            }
        }
        Object explicitBaseUrl = config.getParams().get("base_url");
        return explicitBaseUrl == null ? "" : String.valueOf(explicitBaseUrl).replaceAll("/+$", "");
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private record Operation(String method, String path, String baseUrl,
                              List<String> pathParams, McpToolCard card) {
    }
}
