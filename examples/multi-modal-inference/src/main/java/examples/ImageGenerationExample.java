/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 多模态图片生成 demo：输入一段提示词，调用生图模型生成图片并保存到本地。
 *
 * <p>与 {@link ImageDescribeExample} 共用 apiconfig.json 配置加载方式，但直接使用
 * {@link Model} 门面的 {@code generateImage} 接口，不经过 ReActAgent——纯生成场景无需工具循环。
 * core 的 OpenAI 兼容链路已原生实现生图（默认 {@code POST {base}/images/generations}），
 * 并支持两个通用 kwargs：{@code image_create_path}（覆盖端点路径）与
 * {@code image_urls_path}（响应中图片 URL 数组的点分 JSON 路径）。</p>
 *
 * <p>厂商端点差异不进 core 也不进配置文件——本类按 {@code API_BASE} 域名匹配
 * {@link #IMAGE_PRESETS} 预设表（如 MINIMAX 的 {@code /v1/image_generation} 与
 * {@code data.image_urls}），以 kwargs 传给 core；未命中走 OpenAI 官方默认。
 * apiconfig.json 保持最简五键即可（MINIMAX 预设对应 {@code API_BASE=https://api.minimax.cn}，
 * 生图模型由预设覆盖为 {@code image-01}，可用 {@code -Dopenjiuwen.example.model} 覆盖）。</p>
 *
 * <p>生成结果优先读取 base64，其次读取 URL 并通过 HTTP 下载，保存为 PNG 后打印绝对路径
 * （部分厂商返回的图片 URL 有时效性，demo 会当场下载落地）。</p>
 *
 * @since 2026-10-10
 */
public final class ImageGenerationExample {

    private static final String DEFAULT_PROMPT = "画一只在雪山草地上奔跑的柯基犬，阳光明媚，写实风格";
    private static final String DEFAULT_IMAGE_SIZE = "1664x928";
    private static final String DEFAULT_OUTPUT_DIR = "generated";
    private static final long GENERATION_WAIT_SECONDS = 300L;
    private static final int HTTP_DOWNLOAD_TIMEOUT_SECONDS = 60;
    private static final int HTTP_CONNECT_TIMEOUT_SECONDS = 30;

    /**
     * 厂商图片端点预设：键为 API_BASE 域名，值为传给 core 的通用 kwargs
     * （及 demo 内部的 model 默认值，该键在组装 kwargs 前会被移除）。
     */
    private static final Map<String, Map<String, String>> IMAGE_PRESETS = Map.of(
            "api.minimax.cn", Map.of(
                    "preset_name", "MINIMAX",
                    "model", "image-01",
                    "image_create_path", "/v1/image_generation",
                    "image_urls_path", "data.image_urls",
                    "aspect_ratio", "1:1",
                    "response_format", "url",
                    "prompt_optimizer", "true"));

    private ImageGenerationExample() {
    }

    /**
     * demo 入口：解析提示词与尺寸（或宽高比），调用生图模型并将结果图片保存到本地。
     *
     * <p>args[0] 为生图提示词（可选）；args[1] 在命中厂商预设时为宽高比
     * （如 1:1、16:9），未命中时为图片尺寸（如 1024x1024）。</p>
     *
     * @param args 命令行参数
     * @throws Exception 生成、等待或下载失败时抛出
     */
    public static void main(String[] args) throws Exception {
        String prompt = resolvePrompt(args);
        Path outputDir = Path.of(System.getProperty("openjiuwen.example.outputDir", DEFAULT_OUTPUT_DIR));

        Map<String, String> preset = resolveImagePreset();
        String presetName = preset.getOrDefault("preset_name", "默认 OpenAI 官方端点");
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.putAll(preset);
        kwargs.remove("preset_name");
        String presetModel = preset.get("model");
        kwargs.remove("model");
        coerceBooleanKwargs(kwargs);
        applyOverride(kwargs, "image_create_path", "openjiuwen.example.imageCreatePath");
        applyOverride(kwargs, "image_urls_path", "openjiuwen.example.imageUrlsPath");
        applyOverride(kwargs, "aspect_ratio", "openjiuwen.example.aspectRatio");
        applyOverride(kwargs, "response_format", "openjiuwen.example.responseFormat");
        applyOverride(kwargs, "prompt_optimizer", "openjiuwen.example.promptOptimizer");
        String modelName = System.getProperty("openjiuwen.example.model", presetModel);
        int n = Integer.parseInt(System.getProperty("openjiuwen.example.n", "1"));
        boolean presetMatched = preset.containsKey("image_create_path");
        String aspectRatio = null;
        String size = null;
        if (presetMatched) {
            aspectRatio = System.getProperty("openjiuwen.example.aspectRatio",
                    preset.getOrDefault("aspect_ratio", "1:1"));
            if (args.length > 1 && !args[1].isBlank()) {
                aspectRatio = args[1];
            }
        } else {
            size = resolveSize(args);
        }

        System.out.println("生图提示词: " + prompt);
        System.out.println("生图端点预设: " + presetName + ", 数量: " + n + ", "
                + (presetMatched ? "宽高比: " + aspectRatio : "尺寸: " + size));
        System.out.println("输出目录: " + outputDir.toAbsolutePath().normalize());

        Model model = createModel();

        CompletionStage<ImageGenerationResponse> stage = model.generateImage(
                List.of(new UserMessage(prompt)), modelName, size, null, n, true, false, 0, kwargs);

        ImageGenerationResponse response = waitForResponse(stage);
        List<Path> saved = saveGeneratedImages(response, outputDir);
        System.out.println("生成完成，共 " + saved.size() + " 张图片：");
        for (Path path : saved) {
            System.out.println("  " + path.toAbsolutePath().normalize());
        }
    }

    /**
     * 将 kwargs 中形如 true/false 的字符串值规范化为布尔类型
     * （预设表均为字符串，布尔请求字段以字符串发送会被严格端点拒绝）。
     *
     * @param kwargs 传给 core 的 kwargs
     */
    private static void coerceBooleanKwargs(Map<String, Object> kwargs) {
        for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
            if (entry.getValue() instanceof String value
                    && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                entry.setValue(Boolean.parseBoolean(value));
            }
        }
    }

    /**
     * 按 apiconfig.json 的 API_BASE 域名匹配厂商图片端点预设；
     * 未命中返回空 Map（走 OpenAI 官方默认端点与响应解析）。
     *
     * @return 预设键值
     */
    private static Map<String, String> resolveImagePreset() {
        String host = URI.create(ExampleApiConfigLoader.getApiBase()).getHost();
        if (host == null) {
            return Map.of();
        }
        return IMAGE_PRESETS.getOrDefault(host.toLowerCase(Locale.ROOT), Map.of());
    }

    /**
     * 用系统属性覆盖 kwargs 中的同名通用配置项（显式传参优先于厂商预设）。
     *
     * @param kwargs      传给 core 的 kwargs
     * @param kwargKey    kwargs 键
     * @param propertyKey 系统属性键
     */
    private static void applyOverride(Map<String, Object> kwargs, String kwargKey, String propertyKey) {
        String value = System.getProperty(propertyKey);
        if (value != null && !value.isBlank()) {
            kwargs.put(kwargKey, value);
        }
    }

    /**
     * 创建用于生图的 Model 实例，连接信息来自 apiconfig.json。
     *
     * @return 配置完成的 Model
     */
    private static Model createModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ExampleApiConfigLoader.getModelProvider())
                .apiKey(ExampleApiConfigLoader.getApiKey())
                .apiBase(ExampleApiConfigLoader.getApiBase())
                // 生图接口耗时远长于对话，超时与等待上限放大
                .timeout(300.0f)
                .maxRetries(2)
                .verifySsl(ExampleApiConfigLoader.getSslVerify())
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(ExampleApiConfigLoader.getModelName())
                .build();
        return new Model(clientConfig, requestConfig);
    }

    /**
     * 等待生图异步结果完成，并把执行异常还原为原始错误信息。
     *
     * @param stage 生图异步阶段
     * @return 生图响应
     * @throws Exception 等待超时或被中断时抛出；执行失败时抛出携带原因的异常
     */
    private static ImageGenerationResponse waitForResponse(CompletionStage<ImageGenerationResponse> stage)
            throws Exception {
        try {
            return stage.toCompletableFuture().get(GENERATION_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IllegalStateException("图片生成失败: " + cause.getMessage(), cause);
        }
    }

    /**
     * 将生图结果保存到本地：优先使用 base64 数据，其次下载 URL 列表。
     *
     * @param response  生图响应
     * @param outputDir 输出目录
     * @return 成功保存的文件路径列表
     * @throws IOException 创建目录或写文件失败时抛出
     * @throws InterruptedException 下载被中断时抛出
     */
    private static List<Path> saveGeneratedImages(ImageGenerationResponse response, Path outputDir)
            throws IOException, InterruptedException {
        Files.createDirectories(outputDir);
        List<Path> saved = new ArrayList<>();

        List<String> base64Images = response.getImagesBase64();
        if (base64Images != null && !base64Images.isEmpty()) {
            for (int i = 0; i < base64Images.size(); i++) {
                Path target = buildTargetPath(outputDir, i);
                Files.write(target, Base64.getDecoder().decode(base64Images.get(i)));
                saved.add(target);
            }
            return saved;
        }

        List<String> imageUrls = response.getImages();
        if (imageUrls == null || imageUrls.isEmpty()) {
            return saved;
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
                .build();
        for (int i = 0; i < imageUrls.size(); i++) {
            Path target = buildTargetPath(outputDir, i);
            try {
                downloadImage(client, imageUrls.get(i), target);
                saved.add(target);
            } catch (IOException ex) {
                System.out.println("下载失败[" + imageUrls.get(i) + "]: " + ex.getMessage());
            }
        }
        return saved;
    }

    /**
     * 通过 HTTP 下载生成的图片文件。
     *
     * @param client HTTP 客户端
     * @param url    图片 URL
     * @param target 目标文件路径
     * @throws IOException 请求失败或响应非 200 时抛出
     * @throws InterruptedException 下载被中断时抛出
     */
    private static void downloadImage(HttpClient client, String url, Path target)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_DOWNLOAD_TIMEOUT_SECONDS))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP 状态码 " + response.statusCode());
        }
        Files.write(target, response.body());
    }

    /**
     * 按运行时间与序号构造输出文件路径。
     *
     * @param outputDir 输出目录
     * @param index     图片序号（从0开始）
     * @return 目标文件路径
     */
    private static Path buildTargetPath(Path outputDir, int index) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return outputDir.resolve("generated-" + timestamp + "-" + (index + 1) + ".png");
    }

    /**
     * 解析生图提示词：args[0] → 系统属性 openjiuwen.example.prompt → 默认提示词。
     *
     * @param args 命令行参数
     * @return 生图提示词
     */
    private static String resolvePrompt(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return args[0];
        }
        String propertyValue = System.getProperty("openjiuwen.example.prompt");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        return DEFAULT_PROMPT;
    }

    /**
     * 解析图片尺寸：args[1] → 系统属性 openjiuwen.example.imageSize → 默认尺寸。
     *
     * @param args 命令行参数
     * @return 图片尺寸（宽x高）
     */
    private static String resolveSize(String[] args) {
        if (args.length > 1 && !args[1].isBlank()) {
            return args[1];
        }
        String propertyValue = System.getProperty("openjiuwen.example.imageSize");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        return DEFAULT_IMAGE_SIZE;
    }
}
