/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 多模态视频生成 demo：输入一段提示词（可选首帧图片），调用生视频模型并把视频保存到本地。
 *
 * <p>复用 {@link ImageGenerationExample} 的配置与文件落盘方式，调用 core 的
 * {@code Model.generateVideo} 通用任务式链路。core 不感知任何厂商——厂商端点差异由本类按
 * {@code API_BASE} 域名匹配 {@link #VIDEO_PRESETS} 预设表后，以通用 kwargs 形式传给 core；
 * 未命中的域名走 core 默认的 OpenAI 官方流程。接入新厂商 = 在预设表加一行，或用
 * {@code -Dopenjiuwen.example.video*} 手工传参，无需改动 core。</p>
 *
 * <p>apiconfig.json 保持最简五键（API_BASE/API_KEY/MODEL_PROVIDER/MODEL_NAME/LLM_SSL_VERIFY），
 * 厂商差异不进配置文件。MINIMAX 预设对应：{@code MODEL_PROVIDER=OpenAI}、
 * {@code MODEL_NAME=MiniMax-H3}、{@code API_BASE=https://api.minimax.cn}。</p>
 *
 * @since 2026-10-10
 */
public final class VideoGenerationExample {

    private static final String DEFAULT_PROMPT = "海浪拍打礁石，夕阳西下，电影感运镜";
    private static final String DEFAULT_OUTPUT_DIR = "generated";
    private static final String DEFAULT_RATIO = "16:9";
    private static final int DEFAULT_DURATION_SECONDS = 4;
    private static final long MAX_BYTES_PER_IMAGE = 10L * 1024 * 1024;
    private static final int HTTP_DOWNLOAD_TIMEOUT_SECONDS = 120;
    private static final int HTTP_CONNECT_TIMEOUT_SECONDS = 30;

    /**
     * 厂商视频端点预设：键为 API_BASE 域名，值为传给 core 的通用 kwargs（及 demo 内部的
     * resolution/duration 默认值，这两个键在组装 kwargs 前会被移除）。
     */
    private static final Map<String, Map<String, String>> VIDEO_PRESETS = Map.of(
            "api.minimax.cn", Map.of(
                    "video_body", "reference",
                    "video_create_path", "/v2/video_generation",
                    "video_poll_path", "/v1/query/video_generation?task_id={taskId}",
                    "video_file_retrieve_path", "/v1/files/retrieve?file_id={fileId}&purpose=video_generation",
                    "video_file_url_path", "file.download_url",
                    "resolution", "768P",
                    "duration", "4",
                    "preset_name", "MINIMAX"),
            "ark.cn-beijing.volces.com", Map.of(
                    "video_body", "reference",
                    "video_create_path", "/contents/generation/tasks",
                    "video_result_url_path", "content.video_url",
                    "resolution", "720p",
                    "duration", "5",
                    "preset_name", "ARK"));

    private VideoGenerationExample() {
    }

    /**
     * demo 入口：解析提示词与可选首帧图片，调用生视频模型并将视频保存到本地。
     *
     * <p>args[0] 为生视频提示词（可选），args[1] 为可选首帧图片（本地路径或 http URL）。</p>
     *
     * @param args 命令行参数
     * @throws Exception 生成、轮询或下载失败时抛出
     */
    public static void main(String[] args) throws Exception {
        String prompt = resolveString(args, 0, "openjiuwen.example.prompt", DEFAULT_PROMPT);
        String imgUrl = resolveString(args, 1, "openjiuwen.example.imgUrl", null);
        String localImgUrl = toLocalImageUrl(imgUrl);
        Path outputDir = Path.of(System.getProperty("openjiuwen.example.outputDir", DEFAULT_OUTPUT_DIR));

        Map<String, String> preset = resolveVideoPreset();
        String presetName = preset.getOrDefault("preset_name", "默认 OpenAI 官方端点");
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.putAll(preset);
        kwargs.remove("resolution");
        kwargs.remove("duration");
        kwargs.remove("preset_name");
        applyOverride(kwargs, "video_body", "openjiuwen.example.videoBody");
        applyOverride(kwargs, "video_create_path", "openjiuwen.example.videoCreatePath");
        applyOverride(kwargs, "video_poll_path", "openjiuwen.example.videoPollPath");
        applyOverride(kwargs, "video_result_url_path", "openjiuwen.example.videoResultUrlPath");
        applyOverride(kwargs, "video_result_download_path", "openjiuwen.example.videoResultDownloadPath");
        applyOverride(kwargs, "video_file_retrieve_path", "openjiuwen.example.videoFileRetrievePath");
        applyOverride(kwargs, "video_file_url_path", "openjiuwen.example.videoFileUrlPath");
        String resolution = System.getProperty("openjiuwen.example.resolution", preset.get("resolution"));
        int duration = Integer.parseInt(System.getProperty("openjiuwen.example.duration",
                preset.getOrDefault("duration", String.valueOf(DEFAULT_DURATION_SECONDS))));

        System.out.println("生视频提示词: " + prompt);
        System.out.println("视频端点预设: " + presetName + ", 时长: " + duration + "s, 分辨率: "
                + (resolution == null ? "模型默认" : resolution) + ", 首帧图片: "
                + (localImgUrl == null ? "无" : "已加载"));
        System.out.println("输出目录: " + outputDir.toAbsolutePath().normalize());
        if ("reference".equals(kwargs.get("video_body"))) {
            kwargs.put("ratio", System.getProperty("openjiuwen.example.ratio", DEFAULT_RATIO));
        }

        Model model = createModel();
        VideoGenerationResponse response = model.generateVideo(
                List.of(new UserMessage(prompt)), localImgUrl, null, null, null, resolution,
                duration, true, false, null, null, kwargs)
                .toCompletableFuture().get(30, TimeUnit.MINUTES);

        Path target = saveVideo(response, outputDir);
        System.out.println("生成完成: " + target.toAbsolutePath().normalize());
    }

    /**
     * 创建用于生视频的 Model 实例，连接信息来自 apiconfig.json。
     *
     * @return 配置完成的 Model
     */
    private static Model createModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ExampleApiConfigLoader.getModelProvider())
                .apiKey(ExampleApiConfigLoader.getApiKey())
                .apiBase(ExampleApiConfigLoader.getApiBase())
                .timeout(120.0f)
                .maxRetries(2)
                .verifySsl(ExampleApiConfigLoader.getSslVerify())
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(ExampleApiConfigLoader.getModelName())
                .build();
        return new Model(clientConfig, requestConfig);
    }

    /**
     * 按 apiconfig.json 的 API_BASE 域名匹配厂商端点预设；未命中返回空 Map（走 OpenAI 官方默认）。
     *
     * @return 预设键值
     */
    private static Map<String, String> resolveVideoPreset() {
        String host = URI.create(ExampleApiConfigLoader.getApiBase()).getHost();
        if (host == null) {
            return Map.of();
        }
        return VIDEO_PRESETS.getOrDefault(host.toLowerCase(Locale.ROOT), Map.of());
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
     * 将本地图片路径转换为 base64 data URI；http URL 与空值原样返回。
     *
     * @param imgUrl 图片路径或URL
     * @return 可放入 image_url 的图片引用
     * @throws IOException 图片读取失败时抛出
     */
    private static String toLocalImageUrl(String imgUrl) throws IOException {
        if (imgUrl == null || imgUrl.isBlank() || imgUrl.toLowerCase(Locale.ROOT).startsWith("http")) {
            return imgUrl;
        }
        Path filePath = Path.of(imgUrl).toAbsolutePath().normalize();
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("首帧图片不存在: " + filePath);
        }
        long size = Files.size(filePath);
        if (size > MAX_BYTES_PER_IMAGE) {
            throw new IOException("首帧图片超过" + (MAX_BYTES_PER_IMAGE / 1024 / 1024) + "MB上限");
        }
        return "data:image/jpeg;base64,"
                + Base64.getEncoder().encodeToString(Files.readAllBytes(filePath));
    }

    /**
     * 将生视频结果保存到本地：优先 base64 数据，其次下载 URL。
     *
     * @param response  生视频响应
     * @param outputDir 输出目录
     * @return 已保存文件路径
     * @throws IOException 创建目录、写文件或下载失败时抛出
     * @throws InterruptedException 下载被中断时抛出
     */
    private static Path saveVideo(VideoGenerationResponse response, Path outputDir)
            throws IOException, InterruptedException {
        Files.createDirectories(outputDir);
        Path target = outputDir.resolve("video-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".mp4");
        if (response.getVideoData() != null && response.getVideoData().length > 0) {
            Files.write(target, response.getVideoData());
            return target;
        }
        if (response.getVideoUrl() == null || response.getVideoUrl().isBlank()) {
            throw new IOException("生视频响应既无二进制数据也无视频URL");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(response.getVideoUrl()))
                .timeout(Duration.ofSeconds(HTTP_DOWNLOAD_TIMEOUT_SECONDS))
                .GET()
                .build();
        HttpResponse<byte[]> download = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (download.statusCode() != 200) {
            throw new IOException("下载视频失败, HTTP 状态码 " + download.statusCode());
        }
        Files.write(target, download.body());
        return target;
    }

    /**
     * 解析字符串参数：args[index] → 系统属性 propertyKey → 默认值。
     *
     * @param args        命令行参数
     * @param index       参数下标
     * @param propertyKey 系统属性键
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private static String resolveString(String[] args, int index, String propertyKey, String defaultValue) {
        if (args.length > index && !args[index].isBlank()) {
            return args[index];
        }
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        return defaultValue;
    }
}
