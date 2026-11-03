/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 多模态视频理解 demo：输入一段本地视频，由 ReActAgent 调用视觉模型回答"这个视频描述的是什么"。
 *
 * <p>整体流程与 {@link ImageDescribeExample} 一致：创建 ReActAgent → 注册本地工具
 * → {@link Runner#runAgent} 驱动 ReAct 循环。视频注入依赖 ReActAgent 的 multimodal 视频链路：
 * 工具返回 {@code data.multimodal} 列表（{@code type=video}、{@code data_url} 以
 * {@code data:video/} 开头的 base64 data URI），ReActAgent 会将其转换为带 {@code video_url}
 * 内容的 UserMessage 发给视觉模型。</p>
 *
 * <p>视频不做转码压缩（JDK 标准库无法重编码 MP4），仅做 10MB 大小上限校验；视频整段 base64
 * 随请求发送，8MB 视频约产生 11MB 请求体。运行前需在 {@code src/main/resources/apiconfig.json}
 * 中配置一个支持视频理解的模型（如 qwen3-vl-plus、qwen-vl-max 等），纯文本或仅支持图片的
 * 视觉模型无法处理视频消息。</p>
 *
 * @since 2026-10-10
 */
public final class VideoDescribeExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "multimodal_video_describe_agent";
    private static final String TOOL_ID = "load_video_tool";
    private static final String DEFAULT_CONVERSATION_ID = "multimodal_video_describe_001";
    private static final String DEFAULT_VIDEO_RESOURCE = "/video.mp4";
    private static final String DEFAULT_QUESTION = "这个视频描述的是什么内容？请基于视频实际可见内容详细描述。";
    private static final String SYSTEM_PROMPT = "你是一个视频理解助手。"
            + "当用户消息中包含视频路径时，必须先调用 LoadVideoTool 工具加载视频（path 参数传入视频路径），"
            + "视频加载成功后，基于视频实际可见内容回答用户问题。"
            + "严禁仅凭路径或文件名猜测视频内容。"
            + "每次只调用一次 LoadVideoTool，不要重复调用。";
    private static final long MAX_BYTES_PER_VIDEO = 10L * 1024 * 1024;

    private VideoDescribeExample() {
    }

    /**
     * demo 入口：解析视频路径与问题，创建 agent 与加载视频工具并执行一轮 ReAct 推理。
     *
     * <p>args[0] 为视频路径（可选），args[1] 为针对视频的问题（可选）。</p>
     *
     * @param args 命令行参数
     * @throws Exception agent 执行失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Tool loadVideoTool = null;

        try {
            // 本地调试：关闭敏感过滤后，"Before request chat model" 日志会带上完整 messages/tools
            // （视频请求含约 11MB 的 base64 数据）。需要静默时加 -Dopenjiuwen.example.quietLog=true
            if (!Boolean.parseBoolean(System.getProperty("openjiuwen.example.quietLog", "false"))) {
                UserConfig.setIsSensitive(false);
            }

            String videoPath = resolveVideoPath(args);
            String query = buildQuery(videoPath, args);
            System.out.println("待理解视频: " + videoPath);

            ReActAgent agent = createAgent();
            loadVideoTool = createLoadVideoTool();
            registerTool(agent, loadVideoTool);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
                    agent,
                    Map.of(
                            "query", query,
                            "conversation_id", DEFAULT_CONVERSATION_ID
                    ),
                    null,
                    null
            );

            System.out.println("Agent result:");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } finally {
            if (loadVideoTool != null) {
                Runner.resourceMgr().removeTool(loadVideoTool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL,
                        true);
            }
            Runner.release(DEFAULT_CONVERSATION_ID);
            Runner.stop();
        }
    }

    /**
     * 创建配置好视觉模型的 ReActAgent。
     *
     * <p>模型连接信息来自 apiconfig.json，模型必须是支持视频理解的多模态模型。</p>
     *
     * @return 配置完成的 agent 实例
     */
    private static ReActAgent createAgent() {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("视频理解助手：加载用户指定的视频并描述视频内容")
                .build();

        ReActAgent agent = new ReActAgent(agentCard);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .maxIterations(3)
                .build()
                .configureModelClient(
                        ExampleApiConfigLoader.getModelProvider(),
                        ExampleApiConfigLoader.getApiKey(),
                        ExampleApiConfigLoader.getApiBase(),
                        ExampleApiConfigLoader.getModelName(),
                        ExampleApiConfigLoader.getSslVerify()
                );

        ModelRequestConfig requestConfig = config.getModelConfigObj();
        requestConfig.setTemperature(0.6);
        requestConfig.setTopP(0.8);
        requestConfig.setMaxTokens(1024);

        agent.configure(config);
        return agent;
    }

    /**
     * 创建加载视频工具：接收视频路径，返回 ReActAgent 可识别的 multimodal 视频数据。
     *
     * @return 工具实例
     */
    private static Tool createLoadVideoTool() {
        Map<String, Object> pathProperty = new LinkedHashMap<>();
        pathProperty.put("type", "string");
        pathProperty.put("description", "本地视频文件的绝对路径");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", pathProperty);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("path"));

        ToolCard card = ToolCard.builder()
                .id(TOOL_ID)
                .name("LoadVideoTool")
                .description("加载一段本地视频，返回视频内容供AI视觉分析")
                .inputParams(schema)
                .build();
        return new LocalFunction(card, VideoDescribeExample::loadVideo);
    }

    /**
     * 工具执行入口：加载视频并返回 multimodal 数据。
     *
     * <p>输出格式需满足 ReActAgent.iterMultimodalVideoItems 的筛选条件：
     * {@code data.multimodal} 列表中每个 item 的 {@code type} 为 {@code video}，
     * 且 {@code data_url} 以 {@code data:video/} 开头。</p>
     *
     * @param inputs 模型传入参数，包含path
     * @return 工具执行结果（success + data 或 success + error）
     */
    private static Map<String, Object> loadVideo(Map<String, Object> inputs) {
        Object rawPath = inputs == null ? null : inputs.get("path");
        if (!(rawPath instanceof String path) || path.isBlank()) {
            return buildToolFailure("path参数不能为空，必须是视频路径字符串");
        }
        String videoPath = path.trim();
        try {
            return buildVideoLoadedResult(videoPath, toDataUrl(videoPath));
        } catch (IOException ex) {
            return buildToolFailure("加载视频失败[" + videoPath + "]: " + ex.getMessage());
        }
    }

    /**
     * 将本地视频读取后编码为模型可用的 data URI。
     *
     * @param videoPath 本地视频路径
     * @return data URI
     * @throws IOException 视频不存在、超限或读取失败时抛出
     */
    private static String toDataUrl(String videoPath) throws IOException {
        Path filePath = Path.of(videoPath).toAbsolutePath().normalize();
        byte[] bytes = readVideo(filePath);
        return "data:" + mimeOf(filePath) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 读取指定路径的视频字节并校验大小上限。
     *
     * @param filePath 视频文件路径
     * @return 视频原始字节
     * @throws IOException 文件不存在、超限或读取失败时抛出
     */
    private static byte[] readVideo(Path filePath) throws IOException {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new FileNotFoundException("视频文件不存在或不是常规文件: " + filePath);
        }
        long size = Files.size(filePath);
        if (size > MAX_BYTES_PER_VIDEO) {
            throw new IllegalArgumentException(
                    "视频大小" + (size / 1024 / 1024) + "MB超过上限" + (MAX_BYTES_PER_VIDEO / 1024 / 1024) + "MB");
        }
        return Files.readAllBytes(filePath);
    }

    /**
     * 根据文件扩展名猜测视频 MIME 类型。
     *
     * @param filePath 视频文件路径
     * @return MIME 类型（无法识别时默认 video/mp4）
     */
    private static String mimeOf(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String guessed = URLConnection.guessContentTypeFromName(fileName);
        if (guessed != null && guessed.startsWith("video/")) {
            return guessed;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".mp4") || lowerName.endsWith(".m4v")) {
            return "video/mp4";
        }
        if (lowerName.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lowerName.endsWith(".webm")) {
            return "video/webm";
        }
        return "video/mp4";
    }

    /**
     * 构造视频加载成功结果。
     *
     * @param videoPath 本地视频路径
     * @param dataUrl   视频 data URI
     * @return 工具执行结果
     */
    private static Map<String, Object> buildVideoLoadedResult(String videoPath, String dataUrl) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "video");
        item.put("source_path", videoPath);
        item.put("label", "视频1");
        item.put("data_url", dataUrl);

        // content 键会被 buildToolMessageContent 用作工具消息文本，
        // 缺失时框架会 String.valueOf 整个结果，把 base64 带进文本上下文
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", "视频已加载成功: " + videoPath + "，请立即基于视频实际可见内容回答，禁止重复调用工具");
        data.put("loaded", List.of(videoPath));
        data.put("count", 1);
        data.put("multimodal", List.of(item));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }

    /**
     * 构造工具执行失败结果。
     *
     * @param errorMessage 错误描述
     * @return 工具执行结果
     */
    private static Map<String, Object> buildToolFailure(String errorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", errorMessage);
        return result;
    }

    /**
     * 注册工具到全局资源管理器与 agent 能力管理器。
     *
     * @param agent agent 实例
     * @param tool  待注册工具
     */
    private static void registerTool(ReActAgent agent, Tool tool) {
        Runner.resourceMgr().removeTool(tool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addTool(tool, AGENT_ID);
        agent.getAbilityManager().add(tool.getCard());
    }

    /**
     * 解析待描述视频路径：args[0] → 系统属性 openjiuwen.example.videoPath → 环境变量
     * VIDEO_PATH → 默认路径。
     *
     * @param args 命令行参数
     * @return 视频路径
     */
    private static String resolveVideoPath(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return args[0];
        }
        String propertyValue = System.getProperty("openjiuwen.example.videoPath");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv("VIDEO_PATH");
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return extractResourceToTempFile(DEFAULT_VIDEO_RESOURCE, "openjiuwen-video", ".mp4");
    }

    /**
     * 将 classpath 中的默认素材解包为临时文件并返回其路径（加载工具按文件路径读取）。
     *
     * <p>临时文件注册了 deleteOnExit，进程退出时自动清理。</p>
     *
     * @param resourcePath classpath 资源路径
     * @param prefix       临时文件前缀
     * @param suffix       临时文件后缀
     * @return 临时文件绝对路径
     * @throws IllegalStateException 资源缺失或解包失败时抛出
     */
    private static String extractResourceToTempFile(String resourcePath, String prefix, String suffix) {
        try (InputStream inputStream = VideoDescribeExample.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("默认素材缺失: classpath " + resourcePath
                        + "（可通过 args[0] 或 -Dopenjiuwen.example.videoPath 指定其他文件）");
            }
            Path tempFile = Files.createTempFile(prefix, suffix);
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile.toAbsolutePath().toString();
        } catch (IOException ex) {
            throw new IllegalStateException("默认素材解包失败: classpath " + resourcePath, ex);
        }
    }

    /**
     * 拼装发送给 agent 的用户消息：问题 + 视频路径，路径供模型填入 LoadVideoTool 的 path 参数。
     *
     * @param videoPath 视频路径
     * @param args      命令行参数，args[1] 可选自定义问题
     * @return 用户消息文本
     */
    private static String buildQuery(String videoPath, String[] args) {
        String question = DEFAULT_QUESTION;
        if (args.length > 1 && !args[1].isBlank()) {
            question = args[1];
        }
        return question + "\n视频路径：" + videoPath;
    }
}
