/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.prompts.tools.VideoUnderstandingMetadataProvider;
import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use VisionModelConfig-backed model to understand a video and answer a user query.
 *
 * <p>Mirrors Python's VideoUnderstandingTool in
 * {@code openjiuwen.harness.tools.multimodal.video_understanding}.
 *
 * <p>This tool supports:
 * <ul>
 *   <li>Remote video URLs (http/https)</li>
 *   <li>Local video files (converted to base64)</li>
 *   <li>Configurable model, max_tokens, temperature, and timeout</li>
 * </ul>
 */
public class VideoUnderstandingTool extends Tool {

    private final String language;
    private final VisionModelConfig visionModelConfig;
    private final int defaultTimeoutSeconds;
    private final int defaultMaxTokens;
    private final double defaultTemperature;

    /**
     * Creates a VideoUnderstandingTool with default settings.
     *
     * @param language the language for tool description ("cn" or "en")
     * @param visionModelConfig the vision model configuration
     */
    public VideoUnderstandingTool(String language, VisionModelConfig visionModelConfig) {
        this(language, visionModelConfig, 120, 2048, 0.2, null);
    }

    /**
     * Creates a VideoUnderstandingTool with full configuration.
     *
     * @param language the language for tool description
     * @param visionModelConfig the vision model configuration
     * @param defaultTimeoutSeconds default timeout in seconds
     * @param defaultMaxTokens default max output tokens
     * @param defaultTemperature default sampling temperature
     * @param agentId optional agent identifier
     */
    public VideoUnderstandingTool(
            String language,
            VisionModelConfig visionModelConfig,
            int defaultTimeoutSeconds,
            int defaultMaxTokens,
            double defaultTemperature,
            String agentId
    ) {
        super(buildToolCard(language, agentId));
        this.language = language;
        this.visionModelConfig = visionModelConfig;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.defaultMaxTokens = defaultMaxTokens;
        this.defaultTemperature = defaultTemperature;
    }

    private static ToolCard buildToolCard(String language, String agentId) {
        ToolCard card = new ToolCard();
        assignCardField(card, "id", "harness.video_understanding");
        assignCardField(card, "name", "VideoUnderstandingTool");
        assignCardField(card, "description", VideoUnderstandingMetadataProvider.getStaticDescription(language));
        if (agentId != null) {
            assignCardField(card, "agentId", agentId);
        }
        return card;
    }

    private static void assignCardField(Object target, String fieldName, Object value) {
        if (target == null || value == null) return;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to assign field '" + fieldName + "'", e);
            }
        }
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        if (visionModelConfig == null) {
            return new ToolOutput(false, null, "vision_model_config is not configured.");
        }

        String query = inputs.containsKey("query")
                ? String.valueOf(inputs.get("query")).trim()
                : "";
        String videoPath = inputs.containsKey("video_path")
                ? String.valueOf(inputs.get("video_path")).trim()
                : "";
        String modelName = inputs.containsKey("model")
                ? String.valueOf(inputs.get("model")).trim()
                : visionModelConfig.getModel();

        int maxTokens = inputs.containsKey("max_tokens")
                ? ((Number) inputs.get("max_tokens")).intValue()
                : defaultMaxTokens;
        double temperature = inputs.containsKey("temperature")
                ? ((Number) inputs.get("temperature")).doubleValue()
                : defaultTemperature;
        int timeoutSeconds = inputs.containsKey("timeout_seconds")
                ? ((Number) inputs.get("timeout_seconds")).intValue()
                : defaultTimeoutSeconds;

        if (query.isEmpty()) {
            return new ToolOutput(false, null, "query cannot be empty.");
        }

        if (videoPath.isEmpty()) {
            return new ToolOutput(false, null, "video_path cannot be empty.");
        }

        if (modelName.isEmpty()) {
            return new ToolOutput(false, null, "video understanding model name is empty.");
        }

        // Clamp values
        maxTokens = Math.max(128, Math.min(maxTokens, 8192));
        temperature = Math.max(0.0, Math.min(temperature, 2.0));
        timeoutSeconds = Math.max(10, Math.min(timeoutSeconds, 600));

        try {
            String videoUrl = normalizeVideoUrl(videoPath);

            // Build messages for vision model
            Map<String, Object> videoContent = new LinkedHashMap<>();
            videoContent.put("type", "video_url");
            videoContent.put("video_url", Map.of("url", videoUrl));

            Map<String, Object> textContent = new LinkedHashMap<>();
            textContent.put("type", "text");
            textContent.put("text", query);

            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", List.of(videoContent, textContent));

            // Invoke model (placeholder - actual implementation would call vision model)
            String answer = invokeVisionModel(
                    List.of(userMessage),
                    modelName,
                    maxTokens,
                    temperature,
                    timeoutSeconds
            );

            if (answer == null || answer.isEmpty()) {
                return new ToolOutput(false, null, "model returned empty answer.");
            }

            Map<String, Object> outputData = new LinkedHashMap<>();
            outputData.put("query", query);
            outputData.put("video_path", videoPath);
            outputData.put("model", modelName);
            outputData.put("answer", answer);

            return new ToolOutput(true, outputData, null);

        } catch (Exception e) {
            return new ToolOutput(false, null, "video understanding failed: " + e.getMessage());
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        // Video understanding doesn't support streaming in current implementation
        return java.util.List.of(invoke(inputs, kwargs)).iterator();
    }

    /**
     * Normalize input video path to URL format.
     *
     * @param videoPath the video path (URL or local file)
     * @return normalized URL (original URL or base64 data URL)
     */
    private String normalizeVideoUrl(String videoPath) throws IOException {
        String value = videoPath.trim();

        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }

        // Convert local file to base64 data URL
        Path path = Path.of(value);
        if (!Files.exists(path)) {
            throw new IOException("video file does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("video_path is not a file: " + path);
        }

        byte[] fileBytes = Files.readAllBytes(path);
        String encoded = Base64.getEncoder().encodeToString(fileBytes);

        String mimeType = guessMimeType(path);
        return "data:" + mimeType + ";base64," + encoded;
    }

    /**
     * Guess MIME type from file path.
     *
     * @param path the file path
     * @return guessed MIME type (default: video/mp4)
     */
    private String guessMimeType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".mp4")) return "video/mp4";
        if (fileName.endsWith(".webm")) return "video/webm";
        if (fileName.endsWith(".avi")) return "video/avi";
        if (fileName.endsWith(".mov")) return "video/quicktime";
        if (fileName.endsWith(".mkv")) return "video/x-matroska";
        return "video/mp4";
    }

    /**
     * Invoke vision model with messages.
     *
     * <p>Placeholder implementation - actual implementation would use
     * VisionModelConfig to call the appropriate LLM API.
     *
     * @param messages the chat messages
     * @param modelName the model name
     * @param maxTokens max output tokens
     * @param temperature sampling temperature
     * @param timeoutSeconds timeout in seconds
     * @return the model response text
     */
    private String invokeVisionModel(
            List<Map<String, Object>> messages,
            String modelName,
            int maxTokens,
            double temperature,
            int timeoutSeconds
    ) {
        // Placeholder: In production, this would call the vision model API
        // using visionModelConfig.getApiKey(), visionModelConfig.getBaseUrl()
        // For now, return a placeholder response indicating the tool is ready
        return "[VideoUnderstandingTool] Tool configured for model: " + modelName
                + ". Actual vision model invocation requires API integration.";
    }
}