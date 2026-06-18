/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * OpenAPI-file backed MCP client.
 *
 * <p>Mirrors Python's {@code OpenApiClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
 */
public class OpenApiClient implements McpClient {

    public static final String CLIENT_NAME = "openapi";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace"
    );

    private final McpServerConfig config;
    private final ToolManager toolManager = new ToolManager();
    private final Map<String, Integer> usedNames = new HashMap<>();
    private HttpClient httpClient = HttpClient.newHttpClient();
    private Map<String, Object> openapiSpec;

    public OpenApiClient(McpServerConfig config) {
        this.config = config;
    }

    @Override
    public boolean connect(int retryTimes, float timeout) {
        toolManager.clear();
        usedNames.clear();
        String serverPath = config.getServerPath();
        String[] files = serverPath == null ? new String[0] : serverPath.split(",");
        for (String rawPath : files) {
            String filePath = rawPath.trim();
            if (filePath.isEmpty()) {
                continue;
            }
            openapiSpec = loadConf(filePath);
            try {
                loadSpec(openapiSpec, timeout);
            } catch (BaseError e) {
                throw e;
            } catch (Exception e) {
                Loggers.TOOL.error("Invalid openapi spec: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean disconnect(float timeout) {
        toolManager.clear();
        usedNames.clear();
        httpClient = HttpClient.newHttpClient();
        return true;
    }

    @Override
    public List<Object> listTools(float timeout) {
        List<Object> toolsInfo = new ArrayList<>();
        for (Map.Entry<String, OpenApiTool> entry : toolManager.getTools().entrySet()) {
            OpenApiTool tool = entry.getValue();
            toolsInfo.add(new McpToolCard(null, entry.getKey(), safeString(tool.getDescription()),
                    firstNonEmpty(tool.getParameters(), tool.getInputSchema()),
                    config.getServerName(), config.getServerId()));
        }
        return toolsInfo;
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
        try {
            ToolResult result = toolManager.callTool(toolName, arguments);
            return result.toMcpResult();
        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            throw openApiError(e, String.valueOf(e));
        }
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) {
        OpenApiTool tool = toolManager.getTool(toolName);
        Map<String, Object> inputParams = tool == null
                ? Map.of()
                : firstNonEmpty(tool.getParameters(), tool.getInputSchema());
        String description = tool == null ? "" : safeString(tool.getDescription());
        return Optional.of(new McpToolCard(null, toolName, description, inputParams,
                config.getServerName(), config.getServerId()));
    }

    @Override
    public List<Object> listResources(float timeout) {
        return List.of();
    }

    @Override
    public Object readResource(String uri, float timeout) {
        return null;
    }

    @Override
    public String getServerPath() {
        return config.getServerPath();
    }

    /**
     * Load an OpenAPI spec from JSON/YAML.
     *
     * <p>Mirrors Python's {@code load_conf()} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
     *
     * @param file file path
     * @return parsed spec dictionary
     */
    public static Map<String, Object> loadConf(String file) {
        Path path = normalizePath(file);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw openApiError(null, "path not exists: " + path);
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw openApiError(null, "the " + path + " is not a file");
        }
        if (Files.isSymbolicLink(path)) {
            throw openApiError(null, "symbolic link not allowed:" + path);
        }

        String suffix = suffix(path);
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            Object data;
            if (".json".equals(suffix)) {
                data = MAPPER.readValue(content, Object.class);
            } else if (".yaml".equals(suffix) || ".yml".equals(suffix)) {
                data = new Yaml().load(content);
            } else {
                throw openApiError(null, "only supports. json/. yaml/. yml, current extension: " + suffix);
            }
            if (!(data instanceof Map<?, ?> map)) {
                String typeName = data == null ? "null" : data.getClass().getName();
                throw openApiError(null, "only support dict type: " + typeName);
            }
            return castMap(map);
        } catch (BaseError e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw openApiError(e, String.valueOf(e));
        }
    }

    private void loadSpec(Map<String, Object> spec, float timeout) {
        String baseUrl = extractBaseUrl(spec);
        Map<String, Object> paths = valueAsMap(spec.get("paths"));
        Map<String, Object> componentSchemas = valueAsMap(valueAsMap(spec.get("components")).get("schemas"));

        if (!baseUrl.isBlank()) {
            httpClient = HttpClient.newHttpClient();
        }

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> methodDefinitions = valueAsMap(pathEntry.getValue());
            for (Map.Entry<String, Object> methodEntry : methodDefinitions.entrySet()) {
                String methodKey = methodEntry.getKey().toLowerCase();
                if (!HTTP_METHODS.contains(methodKey)) {
                    continue;
                }
                Map<String, Object> operation = valueAsMap(methodEntry.getValue());
                String httpMethod = methodKey.toUpperCase();
                String originalName = generateToolName(operation, httpMethod, pathEntry.getKey());
                Set<String> tags = stringSet(operation.get("tags"));
                createOpenApiTool(operation, pathEntry.getKey(), httpMethod, baseUrl, originalName, tags,
                        componentSchemas, timeout);
            }
        }
    }

    private String generateToolName(Map<String, Object> operation, String httpMethod, String path) {
        Object operationId = operation.get("operationId");
        String name;
        if (operationId != null && !String.valueOf(operationId).isBlank()) {
            name = String.valueOf(operationId).split("__", -1)[0];
        } else {
            Object summary = operation.get("summary");
            name = summary != null && !String.valueOf(summary).isBlank()
                    ? String.valueOf(summary)
                    : httpMethod + "_" + path;
        }
        if (name.length() > 64) {
            name = name.substring(0, 64);
        }
        return name;
    }

    private String getUniqueName(String name) {
        int count = usedNames.merge(name, 1, Integer::sum);
        if (count == 1) {
            return name;
        }
        String newName = name + "_" + count;
        Loggers.TOOL.debug("tool_ame collision: '{}' already used,using '{}' instead. ", name, newName);
        return newName;
    }

    private void createOpenApiTool(Map<String, Object> operation, String path, String httpMethod, String baseUrl,
                                   String originalName, Set<String> httpTags,
                                   Map<String, Object> componentSchemas, float timeout) {
        String baseDescription = firstText(operation.get("description"), operation.get("summary"),
                "Executes " + httpMethod + " " + path);
        Map<String, Object> parameters = buildInputSchema(operation, componentSchemas);
        Map<String, Object> outputSchema = extractOutputSchema(operation, componentSchemas);
        String generatedName = getUniqueName(originalName);
        OpenApiTool tool = new OpenApiTool(generatedName, httpMethod, path, baseUrl,
                extractPathParamNames(path), baseDescription, parameters, outputSchema,
                new LinkedHashSet<>(httpTags), timeout);
        toolManager.put(generatedName, tool);
    }

    private Map<String, Object> buildInputSchema(Map<String, Object> operation,
                                                 Map<String, Object> componentSchemas) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        Object paramsObj = operation.get("parameters");
        if (paramsObj instanceof List<?> params) {
            for (Object paramObj : params) {
                Map<String, Object> param = valueAsMap(paramObj);
                if (param.isEmpty()) {
                    continue;
                }
                String paramName = safeString(param.get("name"));
                if (paramName.isBlank()) {
                    continue;
                }
                Map<String, Object> schema = param.get("schema") instanceof Map<?, ?> schemaMap
                        ? resolveRef(castMap(schemaMap), componentSchemas)
                        : new LinkedHashMap<>(Map.of("type", "string"));
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

        Map<String, Object> requestBody = valueAsMap(operation.get("requestBody"));
        Map<String, Object> content = valueAsMap(requestBody.get("content"));
        for (Object mediaValue : content.values()) {
            Map<String, Object> mediaType = valueAsMap(mediaValue);
            Map<String, Object> schema = valueAsMap(mediaType.get("schema"));
            if (schema.isEmpty()) {
                continue;
            }
            Map<String, Object> resolved = resolveRef(schema, componentSchemas);
            Map<String, Object> bodyProps = valueAsMap(resolved.get("properties"));
            for (Map.Entry<String, Object> bodyProp : bodyProps.entrySet()) {
                Map<String, Object> propSchema = valueAsMap(bodyProp.getValue());
                properties.put(bodyProp.getKey(), propSchema.isEmpty()
                        ? new LinkedHashMap<>(Map.of("type", "string"))
                        : resolveRef(propSchema, componentSchemas));
            }
            Object bodyRequired = resolved.get("required");
            if (bodyRequired instanceof List<?> requiredList) {
                for (Object item : requiredList) {
                    required.add(String.valueOf(item));
                }
            }
            break;
        }

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }
        return inputSchema;
    }

    private Map<String, Object> extractOutputSchema(Map<String, Object> operation,
                                                    Map<String, Object> componentSchemas) {
        Map<String, Object> responses = valueAsMap(operation.get("responses"));
        Map<String, Object> successResponse = null;
        for (String code : List.of("200", "201")) {
            Map<String, Object> candidate = valueAsMap(responses.get(code));
            if (!candidate.isEmpty()) {
                successResponse = candidate;
                break;
            }
        }
        if (successResponse == null) {
            for (Map.Entry<String, Object> entry : responses.entrySet()) {
                if (entry.getKey().startsWith("2")) {
                    Map<String, Object> candidate = valueAsMap(entry.getValue());
                    if (!candidate.isEmpty()) {
                        successResponse = candidate;
                        break;
                    }
                }
            }
        }
        if (successResponse == null) {
            return Map.of();
        }
        Map<String, Object> content = valueAsMap(successResponse.get("content"));
        for (Object mediaValue : content.values()) {
            Map<String, Object> mediaType = valueAsMap(mediaValue);
            Map<String, Object> schema = valueAsMap(mediaType.get("schema"));
            if (!schema.isEmpty()) {
                return resolveRef(schema, componentSchemas);
            }
        }
        return Map.of();
    }

    private Map<String, Object> resolveRef(Map<String, Object> schema, Map<String, Object> componentSchemas) {
        Object ref = schema.get("$ref");
        if (ref instanceof String refStr) {
            String[] parts = refStr.split("/");
            String refKey = parts.length == 0 ? refStr : parts[parts.length - 1];
            Object resolved = componentSchemas.get(refKey);
            if (resolved instanceof Map<?, ?> resolvedMap) {
                return new LinkedHashMap<>(castMap(resolvedMap));
            }
        }
        return new LinkedHashMap<>(schema);
    }

    private String resolveUrl(OpenApiTool operation, Map<String, Object> arguments) {
        String path = operation.getPath();
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        for (String pathParam : operation.getPathParams()) {
            if (args.containsKey(pathParam)) {
                path = path.replace("{" + pathParam + "}",
                        URLEncoder.encode(String.valueOf(args.get(pathParam)), StandardCharsets.UTF_8));
            }
        }
        return operation.getBaseUrl() + path;
    }

    private String extractBaseUrl(Map<String, Object> spec) {
        Object serversObj = spec.get("servers");
        if (serversObj instanceof List<?> servers && !servers.isEmpty()) {
            Map<String, Object> server = valueAsMap(servers.get(0));
            Object url = server.get("url");
            if (url != null) {
                return trimTrailingSlash(String.valueOf(url));
            }
        }
        Object explicitBaseUrl = config.getParams().get("base_url");
        return explicitBaseUrl == null ? "" : trimTrailingSlash(String.valueOf(explicitBaseUrl));
    }

    private static List<String> extractPathParamNames(String pathTemplate) {
        List<String> params = new ArrayList<>();
        int start = pathTemplate.indexOf('{');
        while (start >= 0) {
            int end = pathTemplate.indexOf('}', start);
            if (end > start) {
                params.add(pathTemplate.substring(start + 1, end));
            }
            start = pathTemplate.indexOf('{', Math.max(end + 1, start + 1));
        }
        return params;
    }

    private static Map<String, Object> firstNonEmpty(Map<String, Object> first, Map<String, Object> fallback) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return fallback != null ? fallback : Map.of();
    }

    private static String firstText(Object first, Object second, String fallback) {
        if (first != null && !String.valueOf(first).isBlank()) {
            return String.valueOf(first);
        }
        if (second != null && !String.valueOf(second).isBlank()) {
            return String.valueOf(second);
        }
        return fallback;
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static Set<String> stringSet(Object value) {
        Set<String> result = new HashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static Path normalizePath(String file) {
        String value = file == null ? "" : file;
        if (value.equals("~") || value.startsWith("~/") || value.startsWith("~\\")) {
            value = System.getProperty("user.home") + value.substring(1);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static String suffix(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
    }

    private static Map<String, Object> valueAsMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return Map.of();
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static BaseError openApiError(Throwable cause, Object reason) {
        return ErrorHelper.buildError(StatusCode.TOOL_OPENAPI_CLIENT_EXECUTION_ERROR, null, null, cause,
                Map.of("reason", String.valueOf(reason)));
    }

    /**
     * OpenAPI tool registry.
     *
     * <p>Mirrors Python's {@code ToolManager} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
     */
    static final class ToolManager {
        private final Map<String, OpenApiTool> tools = new LinkedHashMap<>();

        OpenApiTool getTool(String toolName) {
            return tools.get(toolName);
        }

        Map<String, OpenApiTool> getTools() {
            return tools;
        }

        void put(String toolName, OpenApiTool tool) {
            tools.put(toolName, tool);
        }

        void clear() {
            tools.clear();
        }

        ToolResult callTool(String key, Map<String, Object> arguments) throws Exception {
            OpenApiTool tool = getTool(key);
            if (tool == null) {
                return new ToolResult(null, null);
            }
            try {
                return tool.run(arguments);
            } catch (Exception e) {
                throw openApiError(e, "call tool " + key + " failed: " + e);
            }
        }
    }

    /**
     * Minimal OpenAPI route tool.
     *
     * <p>Mirrors Python's FastMCP {@code OpenAPITool} usage in
     * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
     */
    final class OpenApiTool {
        private final String name;
        private final String method;
        private final String path;
        private final String baseUrl;
        private final List<String> pathParams;
        private final String description;
        private final Map<String, Object> parameters;
        private final Map<String, Object> inputSchema;
        private final Map<String, Object> outputSchema;
        private final Set<String> tags;
        private final float timeout;

        OpenApiTool(String name, String method, String path, String baseUrl, List<String> pathParams,
                    String description, Map<String, Object> parameters, Map<String, Object> outputSchema,
                    Set<String> tags, float timeout) {
            this.name = name;
            this.method = method;
            this.path = path;
            this.baseUrl = baseUrl;
            this.pathParams = List.copyOf(pathParams);
            this.description = description;
            this.parameters = Map.copyOf(parameters);
            this.inputSchema = Map.copyOf(parameters);
            this.outputSchema = Map.copyOf(outputSchema);
            this.tags = Set.copyOf(tags);
            this.timeout = timeout;
        }

        ToolResult run(Map<String, Object> arguments) throws Exception {
            String url = resolveUrl(this, arguments);
            Map<String, Object> bodyArgs = arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments);
            for (String pathParam : pathParams) {
                bodyArgs.remove(pathParam);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");
            float effectiveTimeout = timeout != McpServerConfig.NO_TIMEOUT ? timeout : McpServerConfig.NO_TIMEOUT;
            if (effectiveTimeout != McpServerConfig.NO_TIMEOUT && effectiveTimeout > 0) {
                builder.timeout(Duration.ofMillis((long) (effectiveTimeout * 1000)));
            }

            if ("GET".equals(method)) {
                if (!bodyArgs.isEmpty()) {
                    builder.uri(URI.create(appendQuery(url, bodyArgs)));
                }
                builder.GET();
            } else {
                builder.method(method,
                        HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(bodyArgs),
                                StandardCharsets.UTF_8));
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ToolResult(response.body(), outputSchema);
        }

        String getDescription() {
            return description;
        }

        Map<String, Object> getParameters() {
            return parameters;
        }

        Map<String, Object> getInputSchema() {
            return inputSchema;
        }

        String getPath() {
            return path;
        }

        String getBaseUrl() {
            return baseUrl;
        }

        List<String> getPathParams() {
            return pathParams;
        }

        @SuppressWarnings("unused")
        String getName() {
            return name;
        }

        @SuppressWarnings("unused")
        Set<String> getTags() {
            return tags;
        }

        private String appendQuery(String url, Map<String, Object> queryArgs) {
            StringBuilder builder = new StringBuilder(url);
            builder.append(url.contains("?") ? "&" : "?");
            boolean first = true;
            for (Map.Entry<String, Object> entry : queryArgs.entrySet()) {
                if (!first) {
                    builder.append("&");
                }
                builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                first = false;
            }
            return builder.toString();
        }
    }

    /**
     * FastMCP-style tool result wrapper.
     *
     * <p>Mirrors Python's {@code ToolResult.to_mcp_result()} consumption in
     * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
     */
    static final class ToolResult {
        private final Object result;
        private final Map<String, Object> outputSchema;

        ToolResult(Object result, Map<String, Object> outputSchema) {
            this.result = result;
            this.outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        }

        Object toMcpResult() {
            return result;
        }

        @SuppressWarnings("unused")
        Map<String, Object> getOutputSchema() {
            return outputSchema;
        }
    }
}
