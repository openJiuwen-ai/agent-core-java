// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.yaml.snakeyaml.Yaml;

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
import java.util.concurrent.CompletableFuture;

/**
 * OpenAPI规范客户端
 *
 * <p>解析OpenAPI规范文件（JSON/YAML），将HTTP端点转换为MCP工具。
 * 使用Swagger Parser解析规范，使用OkHttp执行HTTP请求。
 *
 * <p>对应Python: openapi_client.py - OpenApiClient
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class OpenApiClient implements McpClient {

    private static final LoggerProtocol logger = LogManager.getLogger("OpenApiClient");

    private final String serverPath;
    private final String name;
    private final OkHttpClient httpClient;
    private final ToolManager toolManager;
    private final Map<String, Integer> usedNames;
    private OpenAPI openApiSpec;
    private boolean connected = false;

    /**
     * 构造OpenAPI客户端
     *
     * @param serverPath OpenAPI规范文件路径（逗号分隔支持多文件）
     * @param name 客户端名称
     */
    public OpenApiClient(String serverPath, String name) {
        this.serverPath = serverPath;
        this.name = name;
        this.httpClient = new OkHttpClient();
        this.toolManager = new ToolManager();
        this.usedNames = new HashMap<>();
    }

    @Override
    public CompletableFuture<Boolean> connect(int retryTimes, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] files = serverPath.split(",");
                for (String filePath : files) {
                    filePath = filePath.trim();

                    // 加载配置文件
                    Map<String, Object> specData = loadConf(filePath);

                    // 使用Swagger Parser解析OpenAPI规范
                    SwaggerParseResult parseResult = new OpenAPIV3Parser().readContents(
                            new ObjectMapper().writeValueAsString(specData), null, null);
                    openApiSpec = parseResult.getOpenAPI();
                    if (openApiSpec == null) {
                        logger.error("Invalid OpenAPI spec: " + filePath);
                        return false;
                    }

                    // 获取base URL
                    String baseUrl = "";
                    if (openApiSpec.getServers() != null && !openApiSpec.getServers().isEmpty()) {
                        baseUrl = openApiSpec.getServers().get(0).getUrl();
                    }

                    // 遍历路径，创建工具
                    if (openApiSpec.getPaths() != null) {
                        for (Map.Entry<String, PathItem> pathEntry : openApiSpec.getPaths().entrySet()) {
                            String path = pathEntry.getKey();
                            PathItem pathItem = pathEntry.getValue();
                            processPathItem(path, pathItem, baseUrl, timeout);
                        }
                    }
                }
                connected = true;
                logger.info("OpenAPI client connected successfully, loaded " + toolManager.size() + " tools");
                return true;
            } catch (Exception e) {
                logger.error("OpenAPI connection failed: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> disconnect(Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            connected = false;
            logger.info("OpenAPI client disconnected");
            return true;
        });
    }

    @Override
    public CompletableFuture<List<McpToolCard>> listTools(Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            List<McpToolCard> toolsInfo = new ArrayList<>();
            for (Map.Entry<String, OpenApiToolEntry> entry : toolManager.getTools().entrySet()) {
                String toolName = entry.getKey();
                OpenApiToolEntry tool = entry.getValue();
                McpToolCard card = new McpToolCard(
                        toolName,
                        tool.description() != null ? tool.description() : "",
                        tool.inputSchema() != null ? tool.inputSchema() : Map.of()
                );
                card.setServerName(name);
                toolsInfo.add(card);
            }
            return toolsInfo;
        });
    }

    @Override
    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Object result = toolManager.callTool(toolName, arguments, httpClient);
                return result;
            } catch (Exception e) {
                throw new JiuWenBaseException(
                        StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getCode(),
                        StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getMessage()
                                .replace("{error_msg}", e.getMessage())
                );
            }
        });
    }

    @Override
    public CompletableFuture<Optional<McpToolCard>> getToolInfo(String toolName, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            OpenApiToolEntry tool = toolManager.getTool(toolName);
            if (tool == null) {
                return Optional.empty();
            }
            McpToolCard card = new McpToolCard(
                    toolName,
                    tool.description() != null ? tool.description() : "",
                    tool.inputSchema() != null ? tool.inputSchema() : Map.of()
            );
            card.setServerName(name);
            return Optional.of(card);
        });
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * 获取服务器路径
     */
    public String getServerPath() {
        return serverPath;
    }

    /**
     * 获取客户端名称
     */
    public String getName() {
        return name;
    }

    // ==================== 内部方法 ====================

    /**
     * 处理PathItem，为每个HTTP方法创建工具
     */
    private void processPathItem(String path, PathItem pathItem, String baseUrl, Duration timeout) {
        Map<String, Operation> operations = new LinkedHashMap<>();
        if (pathItem.getGet() != null) operations.put("GET", pathItem.getGet());
        if (pathItem.getPost() != null) operations.put("POST", pathItem.getPost());
        if (pathItem.getPut() != null) operations.put("PUT", pathItem.getPut());
        if (pathItem.getDelete() != null) operations.put("DELETE", pathItem.getDelete());
        if (pathItem.getPatch() != null) operations.put("PATCH", pathItem.getPatch());

        for (Map.Entry<String, Operation> opEntry : operations.entrySet()) {
            String method = opEntry.getKey();
            Operation operation = opEntry.getValue();

            String toolName = generateToolName(operation, method, path);
            String uniqueName = getUniqueName(toolName);
            String description = buildDescription(operation, method, path);
            Map<String, Object> inputSchema = buildInputSchema(operation);

            OpenApiToolEntry toolEntry = new OpenApiToolEntry(
                    uniqueName, description, inputSchema,
                    method, baseUrl + path, operation
            );
            toolManager.registerTool(uniqueName, toolEntry);
        }
    }

    /**
     * 从Operation生成工具名称
     * 对应Python的_generate_tool_name
     */
    private String generateToolName(Operation operation, String method, String path) {
        String name;
        if (operation.getOperationId() != null && !operation.getOperationId().isEmpty()) {
            name = operation.getOperationId().split("__")[0];
        } else if (operation.getSummary() != null && !operation.getSummary().isEmpty()) {
            name = operation.getSummary();
        } else {
            name = method.toLowerCase() + "_" + path;
        }
        // 截断到64个字符
        if (name.length() > 64) {
            name = name.substring(0, 64);
        }
        return name;
    }

    /**
     * 获取唯一名称（处理名称冲突）
     * 对应Python的_get_unique_name
     */
    private String getUniqueName(String name) {
        int count = usedNames.getOrDefault(name, 0) + 1;
        usedNames.put(name, count);

        if (count == 1) {
            return name;
        }
        String newName = name + "_" + count;
        logger.debug("Tool name collision: '" + name + "' already used, using '" + newName + "' instead.");
        return newName;
    }

    /**
     * 构建工具描述
     */
    private String buildDescription(Operation operation, String method, String path) {
        if (operation.getDescription() != null && !operation.getDescription().isEmpty()) {
            return operation.getDescription();
        }
        if (operation.getSummary() != null && !operation.getSummary().isEmpty()) {
            return operation.getSummary();
        }
        return "Executes " + method + " " + path;
    }

    /**
     * 从Operation构建输入Schema
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildInputSchema(Operation operation) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        // 处理路径/查询/头部参数
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                Map<String, Object> paramSchema = new HashMap<>();
                if (param.getSchema() != null) {
                    paramSchema.put("type", param.getSchema().getType() != null ? param.getSchema().getType() : "string");
                    if (param.getDescription() != null) {
                        paramSchema.put("description", param.getDescription());
                    }
                }
                properties.put(param.getName(), paramSchema);
                if (Boolean.TRUE.equals(param.getRequired())) {
                    required.add(param.getName());
                }
            }
        }

        // 处理请求体
        if (operation.getRequestBody() != null) {
            RequestBody body = operation.getRequestBody();
            if (body.getContent() != null && body.getContent().get("application/json") != null) {
                Schema<?> bodySchema = body.getContent().get("application/json").getSchema();
                if (bodySchema != null && bodySchema.getProperties() != null) {
                    for (Map.Entry<String, Schema> propEntry : bodySchema.getProperties().entrySet()) {
                        Map<String, Object> propSchema = new HashMap<>();
                        propSchema.put("type", propEntry.getValue().getType() != null ?
                                propEntry.getValue().getType() : "string");
                        if (propEntry.getValue().getDescription() != null) {
                            propSchema.put("description", propEntry.getValue().getDescription());
                        }
                        properties.put(propEntry.getKey(), propSchema);
                    }
                    if (bodySchema.getRequired() != null) {
                        required.addAll(bodySchema.getRequired());
                    }
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    // ==================== 静态工具方法 ====================

    /**
     * 加载配置文件（JSON/YAML）
     * 对应Python的load_conf函数
     *
     * @param file 文件路径
     * @return 解析后的Map
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> loadConf(String file) {
        Path path = Path.of(file).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            throw new JiuWenBaseException(
                    StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getCode(),
                    StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getMessage()
                            .replace("{error_msg}", "path not exists: " + path)
            );
        }
        if (!Files.isRegularFile(path)) {
            throw new JiuWenBaseException(
                    StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getCode(),
                    StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getMessage()
                            .replace("{error_msg}", "the " + path + " is not a file")
            );
        }
        if (Files.isSymbolicLink(path)) {
            throw new JiuWenBaseException(
                    StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getCode(),
                    StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getMessage()
                            .replace("{error_msg}", "symbolic link not allowed: " + path)
            );
        }

        String suffix = "";
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            suffix = fileName.substring(dotIndex).toLowerCase();
        }

        try {
            String content = Files.readString(path);
            Object data;

            if (".json".equals(suffix)) {
                data = new ObjectMapper().readValue(content, Map.class);
            } else if (".yaml".equals(suffix) || ".yml".equals(suffix)) {
                data = new Yaml().load(content);
            } else {
                throw new JiuWenBaseException(
                        StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getCode(),
                        StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getMessage()
                                .replace("{error_msg}",
                                        "only supports .json/.yaml/.yml, current extension: " + suffix)
                );
            }

            if (!(data instanceof Map)) {
                throw new JiuWenBaseException(
                        StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getCode(),
                        StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getMessage()
                                .replace("{error_msg}", "only support dict type: " + data.getClass().getName())
                );
            }
            return (Map<String, Object>) data;
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(
                    StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getCode(),
                    StatusCode.PLUGIN_EXECUTION_RUNTIME_ERROR.getMessage()
                            .replace("{error_msg}", e.getMessage())
            );
        }
    }

    // ==================== 内部类 ====================

    /**
     * OpenAPI工具条目
     */
    record OpenApiToolEntry(
            String name,
            String description,
            Map<String, Object> inputSchema,
            String method,
            String url,
            Operation operation
    ) {}

    /**
     * 工具管理器
     * 对应Python的ToolManager
     */
    static class ToolManager {
        private final Map<String, OpenApiToolEntry> tools = new LinkedHashMap<>();

        void registerTool(String name, OpenApiToolEntry tool) {
            tools.put(name, tool);
        }

        OpenApiToolEntry getTool(String name) {
            return tools.get(name);
        }

        Map<String, OpenApiToolEntry> getTools() {
            return tools;
        }

        int size() {
            return tools.size();
        }

        /**
         * 调用工具（执行HTTP请求）
         */
        Object callTool(String toolName, Map<String, Object> arguments, OkHttpClient httpClient) {
            OpenApiToolEntry tool = tools.get(toolName);
            if (tool == null) {
                return null;
            }
            try {
                // 构建URL（替换路径参数）
                String url = tool.url();
                if (arguments != null) {
                    for (Map.Entry<String, Object> arg : arguments.entrySet()) {
                        url = url.replace("{" + arg.getKey() + "}", String.valueOf(arg.getValue()));
                    }
                }

                Request.Builder requestBuilder = new Request.Builder().url(url);

                switch (tool.method().toUpperCase()) {
                    case "GET":
                        requestBuilder.get();
                        break;
                    case "POST":
                        requestBuilder.post(buildRequestBody(arguments));
                        break;
                    case "PUT":
                        requestBuilder.put(buildRequestBody(arguments));
                        break;
                    case "DELETE":
                        requestBuilder.delete();
                        break;
                    case "PATCH":
                        requestBuilder.patch(buildRequestBody(arguments));
                        break;
                    default:
                        requestBuilder.get();
                }

                try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                    if (response.body() != null) {
                        return response.body().string();
                    }
                    return null;
                }
            } catch (Exception e) {
                throw new JiuWenBaseException(
                        StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode(),
                        "call tool " + toolName + " failed: " + e.getMessage()
                );
            }
        }

        private okhttp3.RequestBody buildRequestBody(Map<String, Object> arguments) {
            try {
                String json = new ObjectMapper().writeValueAsString(arguments != null ? arguments : Map.of());
                return okhttp3.RequestBody.create(json, MediaType.parse("application/json"));
            } catch (Exception e) {
                return okhttp3.RequestBody.create("{}", MediaType.parse("application/json"));
            }
        }
    }
}
