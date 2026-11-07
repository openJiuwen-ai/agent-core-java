/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.media;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.designer.execution.DesignerNodeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * MiniMax image-01 and MiniMax-H3 adapter.
 *
 * @since 2026-11-06
 */
public final class MiniMaxMediaPort implements DesignerMediaPort {

    private static final Logger LOG = LoggerFactory.getLogger(MiniMaxMediaPort.class);

    private static final int IMAGE_PROMPT_LIMIT = 1500;
    private static final int VIDEO_PROMPT_LIMIT = 7000;
    private static final int IMAGE_MIN_SIDE = 512;
    private static final int IMAGE_MAX_SIDE = 2048;
    private static final int IMAGE_MAX_REFERENCES = 9;
    private static final long VIDEO_POLL_INTERVAL_SECONDS = 15L;
    private static final long VIDEO_POLL_DEADLINE_SECONDS = 1800L;
    private static final long MAX_DOWNLOAD_BYTES = 500L * 1024L * 1024L;
    private static final Set<String> IMAGE_RATIOS =
            Set.of("1:1", "16:9", "4:3", "3:2", "2:3", "3:4", "9:16", "21:9");
    private static final Set<String> VIDEO_RATIOS =
            Set.of("21:9", "16:9", "4:3", "1:1", "3:4", "9:16");
    private static final Set<String> REGION_ROOTS = new LinkedHashSet<>(List.of(
            "https://api.minimax.cn", "https://api.minimax.io", "https://api.minimaxi.com"));
    private static final String IMAGE_CREATE_PATH = "/v1/image_generation";
    private static final String IMAGE_PAYLOAD_PATH = "data.image_base64";
    private static final String VIDEO_CREATE_PATH = "/v2/video_generation";
    private static final String VIDEO_POLL_PATH_TEMPLATE = "/v1/query/video_generation?task_id={taskId}";
    private static final String VIDEO_FILE_RETRIEVE_PATH =
            "/v1/files/retrieve?file_id={fileId}&purpose=video_generation";
    private static final String VIDEO_FILE_URL_PATH = "file.download_url";
    private static final String VIDEO_STATUS_PATH = "task.status";
    private static final String VIDEO_RESULT_URL_PATH = "task.content.url";

    private final Supplier<MiniMaxMediaConfig> configSupplier;
    private final HttpClient httpClient;
    private final DesignerFfmpeg ffmpeg;

    /**
     * Create the adapter.
     *
     * @param config connection settings
     */
    public MiniMaxMediaPort(MiniMaxMediaConfig config) {
        this(() -> config, new DesignerFfmpeg());
    }

    /**
     * Create the adapter with an explicit ffmpeg wrapper.
     *
     * @param config connection settings
     * @param ffmpeg ffmpeg wrapper
     */
    public MiniMaxMediaPort(MiniMaxMediaConfig config, DesignerFfmpeg ffmpeg) {
        this(() -> config, ffmpeg);
    }

    /**
     * Create the adapter that resolves settings per request, so WebUI config
     * saves take effect without restarting the AgentServer.
     *
     * @param configSupplier current connection settings
     * @param ffmpeg ffmpeg wrapper
     */
    public MiniMaxMediaPort(Supplier<MiniMaxMediaConfig> configSupplier, DesignerFfmpeg ffmpeg) {
        this.configSupplier = configSupplier;
        this.ffmpeg = ffmpeg;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public boolean isConfigured() {
        return resolvedConfig().isConfigured();
    }

    @Override
    public Path generateImage(ImageRequest request) {
        MiniMaxMediaConfig config = resolvedConfig();
        requireConfigured(config);
        DesignerNodeException lastFailure = null;
        for (String root : roots(config)) {
            try {
                return generateImageViaModel(root, request, config);
            } catch (DesignerNodeException ex) {
                lastFailure = ex;
                if (!isAuthFailureMessage(ex.getMessage())) {
                    throw ex;
                }
                LOG.warn("MiniMax image auth failure on host={}: {}", host(root), ex.getMessage());
            }
        }
        throw lastFailure == null
                ? new DesignerNodeException("MiniMax image generation failed")
                : lastFailure;
    }

    /**
     * Generate one image through the core {@link Model#generateImage} endpoint
     * flow. The MiniMax protocol is expressed with the endpoint kwargs:
     * {@code image_create_path}, {@code image_base64_path} and the body fields
     * {@code response_format}/{@code n} plus {@code width}/{@code height} or
     * {@code aspect_ratio}.
     *
     * @param root region API root without the {@code /v1} suffix
     * @param request image request
     * @return saved image file
     */
    private Path generateImageViaModel(String root, ImageRequest request, MiniMaxMediaConfig config) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("image_create_path", IMAGE_CREATE_PATH);
        kwargs.put("image_base64_path", IMAGE_PAYLOAD_PATH);
        kwargs.put("response_format", "base64");
        kwargs.put("n", 1);
        if (request.width() > 0 && request.height() > 0) {
            kwargs.put("width", clampImageSide(request.width()));
            kwargs.put("height", clampImageSide(request.height()));
        } else {
            kwargs.put("aspect_ratio", IMAGE_RATIOS.contains(request.aspectRatio())
                    ? request.aspectRatio()
                    : "1:1");
        }
        LOG.info("MiniMax image start: model={}, promptChars={}, width={}, height={}, aspect={}, "
                        + "baseHost={}", config.imageModel(), request.prompt().length(),
                kwargs.get("width"), kwargs.get("height"), kwargs.get("aspect_ratio"), host(root));
        ImageGenerationResponse response;
        try {
            response = model(root, config.imageModel(), config)
                    .generateImage(List.of(new UserMessage(truncate(request.prompt(), IMAGE_PROMPT_LIMIT))),
                            config.imageModel(), null, null, 1, true, false, 0, kwargs)
                    .toCompletableFuture().join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new DesignerNodeException("MiniMax image generation failed: " + cause.getMessage(), cause);
        }
        List<String> encoded = response.getImagesBase64() == null ? List.of() : response.getImagesBase64();
        if (encoded.isEmpty()) {
            throw new DesignerNodeException("MiniMax returned no images");
        }
        try {
            Files.createDirectories(request.outputDir());
            byte[] bytes = Base64.getDecoder().decode(encoded.get(0));
            Path target = request.outputDir().resolve(request.fileName());
            Files.write(target, bytes);
            LOG.info("MiniMax image saved: host={}, path={}, bytes={}", host(root),
                    target.getFileName(), bytes.length);
            return target;
        } catch (IOException | IllegalArgumentException ex) {
            throw new DesignerNodeException("failed to save MiniMax image: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Path generateVideo(VideoRequest request) {
        MiniMaxMediaConfig config = resolvedConfig();
        requireConfigured(config);
        DesignerNodeException lastFailure = null;
        for (String root : roots(config)) {
            try {
                return generateVideoViaModel(root, request, config);
            } catch (DesignerNodeException ex) {
                lastFailure = ex;
                if (!isAuthFailureMessage(ex.getMessage())) {
                    throw ex;
                }
                LOG.warn("MiniMax video auth failure on host={}: {}", host(root), ex.getMessage());
            }
        }
        throw lastFailure == null
                ? new DesignerNodeException("MiniMax video generation failed")
                : lastFailure;
    }

    /**
     * Generate one video through the core {@link Model#generateVideo} task
     * engine. The MiniMax protocol is expressed with the vendor-neutral task
     * kwargs: create/poll/status/result-url paths, the {@code content} body
     * that keeps the first-frame and reference-image roles, and {@code ratio}.
     *
     * @param root region API root without the {@code /v1} suffix
     * @param request video request
     * @return saved video file
     */
    private Path generateVideoViaModel(String root, VideoRequest request, MiniMaxMediaConfig config) {
        int duration = Math.max(4, Math.min(15, request.durationSec()));
        String resolution = resolution(request.resolution());
        String ratio = VIDEO_RATIOS.contains(request.aspectRatio()) ? request.aspectRatio() : "16:9";
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("video_body", "reference");
        kwargs.put("content", videoContent(request));
        kwargs.put("video_create_path", VIDEO_CREATE_PATH);
        kwargs.put("video_poll_path", VIDEO_POLL_PATH_TEMPLATE);
        kwargs.put("video_status_path", VIDEO_STATUS_PATH);
        kwargs.put("video_result_url_path", VIDEO_RESULT_URL_PATH);
        kwargs.put("video_file_retrieve_path", VIDEO_FILE_RETRIEVE_PATH);
        kwargs.put("video_file_url_path", VIDEO_FILE_URL_PATH);
        kwargs.put("ratio", ratio);
        kwargs.put("poll_interval_seconds", VIDEO_POLL_INTERVAL_SECONDS);
        kwargs.put("max_wait_seconds", VIDEO_POLL_DEADLINE_SECONDS);
        LOG.info("MiniMax video start: model={}, promptChars={}, durationSec={}, resolution={}, "
                        + "ratio={}, references={}, firstFrame={}, baseHost={}",
                config.videoModel(), request.prompt().length(), duration, resolution, ratio,
                request.referencePaths().size(), request.firstFramePath() != null, host(root));
        VideoGenerationResponse response;
        try {
            response = model(root, config.videoModel(), config)
                    .generateVideo(List.of(new UserMessage(truncate(request.prompt(), VIDEO_PROMPT_LIMIT))),
                            null, null, config.videoModel(), null, resolution, duration, true, false,
                            null, null, kwargs)
                    .toCompletableFuture().join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new DesignerNodeException("MiniMax video generation failed: " + cause.getMessage(), cause);
        }
        Path output;
        if (response.getVideoUrl() != null && !response.getVideoUrl().isBlank()) {
            output = download(response.getVideoUrl(), request, config);
        } else if (response.getVideoData() != null && response.getVideoData().length > 0) {
            output = saveVideoData(response.getVideoData(), request);
        } else {
            throw new DesignerNodeException("MiniMax video job returned no video payload");
        }
        LOG.info("MiniMax video done: host={}, path={}", host(root), output);
        return output;
    }

    private Model model(String root, String modelName, MiniMaxMediaConfig config) {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(config.provider())
                .apiKey(config.apiKey())
                .apiBase(root)
                .timeout((double) config.timeoutSeconds())
                .verifySsl(config.verifySsl())
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(modelName)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private Path saveVideoData(byte[] data, VideoRequest request) {
        if (data.length > MAX_DOWNLOAD_BYTES) {
            throw new DesignerNodeException("MiniMax video exceeds the download limit");
        }
        try {
            Files.createDirectories(request.outputDir());
            Path target = request.outputDir().resolve(request.fileName());
            Files.write(target, data);
            return target;
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to save MiniMax video: " + ex.getMessage(), ex);
        }
    }

    private static boolean isAuthFailureMessage(String message) {
        String lower = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("401") || lower.contains("403") || lower.contains("unauthorized")
                || lower.contains("invalid api key") || lower.contains("authorized");
    }

    @Override
    public Path compose(List<Path> clips, Path output) {
        return ffmpeg.compose(clips, output);
    }

    @Override
    public Optional<Path> extractLastFrame(Path video, Path output) {
        return ffmpeg.extractLastFrame(video, output);
    }

    private Path download(String url, VideoRequest request, MiniMaxMediaConfig config) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new DesignerNodeException("unsupported video URL scheme");
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .GET().build();
            HttpResponse<InputStream> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new DesignerNodeException("failed to download MiniMax video: " + response.statusCode());
            }
            Files.createDirectories(request.outputDir());
            Path target = request.outputDir().resolve(request.fileName());
            try (InputStream stream = response.body()) {
                copyLimited(stream, target);
            }
            LOG.info("MiniMax video downloaded: path={}, bytes={}", target.getFileName(),
                    Files.size(target));
            return target;
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to download MiniMax video: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DesignerNodeException("MiniMax video download interrupted", ex);
        }
    }

    private static void copyLimited(InputStream stream, Path target) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".designer-video-", ".tmp");
        try (var output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DOWNLOAD_BYTES) {
                    throw new DesignerNodeException("MiniMax video exceeds the download limit");
                }
                output.write(buffer, 0, read);
            }
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private List<Map<String, Object>> videoContent(VideoRequest request) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", truncate(request.prompt(), VIDEO_PROMPT_LIMIT)));
        if (request.firstFramePath() != null) {
            content.add(imagePart(request.firstFramePath(), "first_frame"));
        }
        List<Path> refs = request.referencePaths();
        for (int index = 0; index < refs.size() && index < IMAGE_MAX_REFERENCES; index++) {
            content.add(imagePart(refs.get(index), "reference_image"));
        }
        return content;
    }

    private static Map<String, Object> imagePart(Path path, String role) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "image_url");
        part.put("image_url", Map.of("url", dataUri(path)));
        part.put("role", role);
        return part;
    }

    private static String dataUri(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            String mime = mimeType(path);
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to read reference image: " + path.getFileName(), ex);
        }
    }

    private static String mimeType(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }

    private static List<String> roots(MiniMaxMediaConfig config) {
        String primary = config.apiBase().replaceAll("/v\\d+/?$", "");
        List<String> roots = new ArrayList<>();
        roots.add(primary);
        for (String root : REGION_ROOTS) {
            if (!root.equals(primary)) {
                roots.add(root);
            }
        }
        return roots;
    }


    private static int clampImageSide(int side) {
        int clamped = Math.max(IMAGE_MIN_SIDE, Math.min(IMAGE_MAX_SIDE, side));
        return clamped / 8 * 8;
    }

    private static String resolution(String value) {
        String text = value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
        return "2K".equals(text) ? "2K" : "768P";
    }

    private static void requireConfigured(MiniMaxMediaConfig config) {
        if (!config.isConfigured()) {
            throw new DesignerNodeException("MiniMax image/video generation is not configured");
        }
    }

    private MiniMaxMediaConfig resolvedConfig() {
        MiniMaxMediaConfig config = configSupplier.get();
        return config == null ? new MiniMaxMediaConfig("", "", "image-01", "MiniMax-H3", 120) : config;
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
