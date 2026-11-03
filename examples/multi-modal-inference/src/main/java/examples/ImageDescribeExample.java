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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * 多模态图片理解 demo：输入一张图片，由 ReActAgent 调用视觉模型回答"这张图片描述的是什么"。
 *
 * <p>整体流程与 {@code ReActWeatherAgentExample} 一致：创建 ReActAgent → 注册本地工具
 * → {@link Runner#runAgent} 驱动 ReAct 循环。图片注入方式参考 multi-modal-audit-demo 的
 * LoadImagesTool：工具返回 {@code data.multimodal} 列表（{@code type=image}、
 * {@code data_url} 以 {@code data:image/} 开头的 base64 data URI），
 * ReActAgent 会将其转换为带 {@code image_url} 内容的 UserMessage 发给视觉模型。</p>
 *
 * <p>运行前需在 {@code src/main/resources/apiconfig.json} 中配置一个支持视觉理解的模型
 * （如 qwen-vl-plus、qwen3-vl-plus、glm-4.5v 等），纯文本模型无法处理图片消息。</p>
 *
 * @since 2026-10-09
 */
public final class ImageDescribeExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "multimodal_image_describe_agent";
    private static final String TOOL_ID = "load_image_tool";
    private static final String DEFAULT_CONVERSATION_ID = "multimodal_image_describe_001";
    private static final String DEFAULT_IMAGE_RESOURCE = "/1080p.jpeg";
    private static final String DEFAULT_QUESTION = "这张图片描述的是什么内容？请基于图片实际可见内容详细描述。";
    private static final String SYSTEM_PROMPT = "你是一个图片理解助手。"
            + "当用户消息中包含图片路径时，必须先调用 LoadImageTool 工具加载图片（path 参数传入图片路径），"
            + "图片加载成功后，基于图片实际可见内容回答用户问题。"
            + "严禁仅凭路径或文件名猜测图片内容。"
            + "每次只调用一次 LoadImageTool，不要重复调用。";
    private static final long MAX_BYTES_PER_IMAGE = 10L * 1024 * 1024;
    private static final int MAX_EDGE_PX = 1024;
    private static final float JPEG_QUALITY = 0.85f;

    private ImageDescribeExample() {
    }

    /**
     * demo 入口：解析图片路径与问题，创建 agent 与加载图片工具并执行一轮 ReAct 推理。
     *
     * <p>args[0] 为图片路径（可选），args[1] 为针对图片的问题（可选）。</p>
     *
     * @param args 命令行参数
     * @throws Exception agent 执行失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Tool loadImageTool = null;

        try {
            // 本地调试：关闭敏感过滤后，"Before request chat model" 日志会带上完整 messages/tools
            // （第二轮含约19万字符的 base64 图片数据）。需要静默时加 -Dopenjiuwen.example.quietLog=true
            if (!Boolean.parseBoolean(System.getProperty("openjiuwen.example.quietLog", "false"))) {
                UserConfig.setIsSensitive(false);
            }

            String imagePath = resolveImagePath(args);
            String query = buildQuery(imagePath, args);
            System.out.println("待理解图片: " + imagePath);

            ReActAgent agent = createAgent();
            loadImageTool = createLoadImageTool();
            registerTool(agent, loadImageTool);

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
            if (loadImageTool != null) {
                Runner.resourceMgr().removeTool(loadImageTool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL,
                        true);
            }
            Runner.release(DEFAULT_CONVERSATION_ID);
            Runner.stop();
        }
    }

    /**
     * 创建配置好视觉模型的 ReActAgent。
     *
     * <p>模型连接信息来自 apiconfig.json，模型必须是支持视觉理解的多模态模型。</p>
     *
     * @return 配置完成的 agent 实例
     */
    private static ReActAgent createAgent() {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("图片理解助手：加载用户指定的图片并描述图片内容")
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
     * 创建加载图片工具：接收图片路径，返回 ReActAgent 可识别的 multimodal 图片数据。
     *
     * @return 工具实例
     */
    private static Tool createLoadImageTool() {
        Map<String, Object> pathProperty = new LinkedHashMap<>();
        pathProperty.put("type", "string");
        pathProperty.put("description", "本地图片文件的绝对路径");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", pathProperty);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("path"));

        ToolCard card = ToolCard.builder()
                .id(TOOL_ID)
                .name("LoadImageTool")
                .description("加载一张本地图片，返回图片内容供AI视觉分析")
                .inputParams(schema)
                .build();
        return new LocalFunction(card, ImageDescribeExample::loadImage);
    }

    /**
     * 工具执行入口：加载图片并返回 multimodal 数据。
     *
     * <p>输出格式需满足 ReActAgent.iterMultimodalImageItems 的筛选条件：
     * {@code data.multimodal} 列表中每个 item 的 {@code type} 为 {@code image}，
     * 且 {@code data_url} 以 {@code data:image/} 开头。</p>
     *
     * @param inputs 模型传入参数，包含path
     * @return 工具执行结果（success + data 或 success + error）
     */
    private static Map<String, Object> loadImage(Map<String, Object> inputs) {
        Object rawPath = inputs == null ? null : inputs.get("path");
        if (!(rawPath instanceof String path) || path.isBlank()) {
            return buildToolFailure("path参数不能为空，必须是图片路径字符串");
        }
        String imagePath = path.trim();
        try {
            return buildImageLoadedResult(imagePath, toDataUrl(imagePath));
        } catch (IOException ex) {
            return buildToolFailure("加载图片失败[" + imagePath + "]: " + ex.getMessage());
        }
    }

    /**
     * 将本地图片读取后等比缩放压缩为 JPEG，再编码为模型可用的 data URI。
     *
     * @param imagePath 本地图片路径
     * @return data URI
     * @throws IOException 图片读取或压缩失败时抛出
     */
    private static String toDataUrl(String imagePath) throws IOException {
        Path filePath = Path.of(imagePath).toAbsolutePath().normalize();
        ImagePayload payload = compress(filePath);
        return "data:" + payload.mime() + ";base64," + Base64.getEncoder().encodeToString(payload.data());
    }

    /**
     * 压缩产物：mime 必须与 data 实际格式一致（压缩路径恒为 JPEG，回退原始字节时为原格式）。
     *
     * @param mime 图片 MIME 类型
     * @param data 图片字节
     */
    private record ImagePayload(String mime, byte[] data) {
    }

    /**
     * 读取并压缩指定路径的图片。
     *
     * @param filePath 图片文件路径
     * @return 压缩产物（解码或压缩失败时回退原始字节）
     * @throws IOException 文件不存在、超限或读取失败时抛出
     */
    private static ImagePayload compress(Path filePath) throws IOException {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new FileNotFoundException("图片文件不存在或不是常规文件: " + filePath);
        }
        long size = Files.size(filePath);
        if (size > MAX_BYTES_PER_IMAGE) {
            throw new IllegalArgumentException(
                    "图片大小" + (size / 1024 / 1024) + "MB超过上限" + (MAX_BYTES_PER_IMAGE / 1024 / 1024) + "MB");
        }
        byte[] bytes = Files.readAllBytes(filePath);
        try {
            return new ImagePayload("image/jpeg", compressToJpeg(bytes));
        } catch (IOException | IllegalArgumentException ex) {
            // 解码或压缩失败时回退原始字节，mime 与原文件格式保持一致
            return new ImagePayload(mimeOf(filePath), bytes);
        }
    }

    /**
     * 将图片字节解码后等比缩放压缩为 JPEG。
     *
     * @param original 原始图片字节
     * @return 压缩后的 JPEG 字节
     * @throws IOException 图片解码或编码失败时抛出
     */
    private static byte[] compressToJpeg(byte[] original) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
        if (source == null) {
            throw new IllegalArgumentException("无法解码图片数据");
        }
        int longestEdge = Math.max(source.getWidth(), source.getHeight());
        int targetWidth = source.getWidth();
        int targetHeight = source.getHeight();
        if (longestEdge > MAX_EDGE_PX) {
            // 整数等比缩放：先乘后除，避免浮点精度问题
            targetWidth = Math.max(1, source.getWidth() * MAX_EDGE_PX / longestEdge);
            targetHeight = Math.max(1, source.getHeight() * MAX_EDGE_PX / longestEdge);
        }
        BufferedImage rgb = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, targetWidth, targetHeight);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream imageStream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageStream);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * 根据文件扩展名猜测图片 MIME 类型。
     *
     * @param filePath 图片文件路径
     * @return MIME 类型（无法识别时默认 image/png）
     */
    private static String mimeOf(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String guessed = URLConnection.guessContentTypeFromName(fileName);
        if (guessed != null && guessed.startsWith("image/")) {
            return guessed;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerName.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerName.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lowerName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    /**
     * 构造图片加载成功结果。
     *
     * @param imagePath 本地图片路径
     * @param dataUrl   图片 data URI
     * @return 工具执行结果
     */
    private static Map<String, Object> buildImageLoadedResult(String imagePath, String dataUrl) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "image");
        item.put("source_path", imagePath);
        item.put("label", "图片1");
        item.put("data_url", dataUrl);

        // content 键会被 buildToolMessageContent 用作工具消息文本，
        // 缺失时框架会 String.valueOf 整个结果，把 base64 带进文本上下文
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", "图片已加载成功: " + imagePath + "，请立即基于图片可见内容回答，禁止重复调用工具");
        data.put("loaded", List.of(imagePath));
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
     * 解析待描述图片路径：args[0] → 系统属性 openjiuwen.example.imagePath → 环境变量
     * IMAGE_PATH → 默认路径。
     *
     * @param args 命令行参数
     * @return 图片路径
     */
    private static String resolveImagePath(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return args[0];
        }
        String propertyValue = System.getProperty("openjiuwen.example.imagePath");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv("IMAGE_PATH");
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return extractResourceToTempFile(DEFAULT_IMAGE_RESOURCE, "openjiuwen-image", ".jpeg");
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
        try (InputStream inputStream = ImageDescribeExample.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("默认素材缺失: classpath " + resourcePath
                        + "（可通过 args[0] 或 -Dopenjiuwen.example.imagePath 指定其他文件）");
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
     * 拼装发送给 agent 的用户消息：问题 + 图片路径，路径供模型填入 LoadImageTool 的 path 参数。
     *
     * @param imagePath 图片路径
     * @param args      命令行参数，args[1] 可选自定义问题
     * @return 用户消息文本
     */
    private static String buildQuery(String imagePath, String[] args) {
        String question = DEFAULT_QUESTION;
        if (args.length > 1 && !args[1].isBlank()) {
            question = args[1];
        }
        return question + "\n图片路径：" + imagePath;
    }
}
