package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code create_vision_tools} and helper functions in
 * {@code openjiuwen.harness.tools.multimodal.vision}.
 */
public final class VisionTools {

    static final String DEFAULT_OCR_PROMPT = """
            You are a meticulous OCR assistant.
            Extract all visible text from the image.
            Preserve structure, line breaks, numbers, symbols, and uncertain text when possible.
            If no text is visible, reply with 'No text found'.""";

    private static final String SANDBOX_PATH_MARKER = "home/user";
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_OPENROUTER_VISION_MODEL = "google/gemini-2.5-pro";
    private static final String DEFAULT_OPENAI_VISION_MODEL = "gpt-4.1-mini";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private VisionTools() {
    }

    public static List<Tool> createVisionTools(String language, VisionModelConfig visionModelConfig) {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ImageOCRTool(language, visionModelConfig));
        tools.add(new VisualQuestionAnsweringTool(language, visionModelConfig));
        return tools;
    }

    public static VisionModelConfig visionModelConfigFromEnv() {
        return visionModelConfigFromEnv(System.getenv());
    }

    public static VisionModelConfig visionModelConfigFromEnv(Map<String, String> env) {
        VisionModelConfig config = new VisionModelConfig();
        String apiKey = firstNonBlank(env, "VISION_API_KEY", "OPENROUTER_API_KEY", "OPENAI_API_KEY");
        String baseUrl = firstNonBlank(
                env,
                "VISION_BASE_URL",
                "VISION_API_BASE",
                "OPENROUTER_BASE_URL",
                "OPENAI_BASE_URL"
        );
        if (baseUrl.isBlank()) {
            baseUrl = DEFAULT_OPENAI_BASE_URL;
        }

        String model = firstNonBlank(env, "VISION_MODEL", "VISION_MODEL_NAME");
        if (model.isBlank()) {
            model = baseUrl.contains("openrouter.ai") ? DEFAULT_OPENROUTER_VISION_MODEL : DEFAULT_OPENAI_VISION_MODEL;
        }

        config.setApiKey(apiKey);
        config.setBaseUrl(baseUrl);
        config.setModel(model);
        return config;
    }

    static VisionModelConfig requireVisionModelConfig(VisionModelConfig visionModelConfig) {
        if (visionModelConfig == null) {
            throw new IllegalArgumentException(
                    "Vision model config is not set. Pass DeepAgentConfig.vision_model_config "
                            + "or construct the tool with VisionModelConfig."
            );
        }
        if (visionModelConfig.getApiKey().isBlank()) {
            throw new IllegalArgumentException("Vision model config missing api_key.");
        }
        if (visionModelConfig.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Vision model config missing base_url.");
        }
        if (visionModelConfig.getModel().isBlank()) {
            throw new IllegalArgumentException("Vision model config missing model.");
        }
        return visionModelConfig;
    }

    static String callVisionModel(
            String imagePathOrUrl,
            String prompt,
            VisionModelConfig visionModelConfig
    ) throws Exception {
        VisionModelConfig config = requireVisionModelConfig(visionModelConfig);
        Map<String, Object> imageContent = buildImageContent(imagePathOrUrl);
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return invokeChatCompletion(config, prompt, imageContent);
            } catch (Exception exc) {
                lastError = exc;
                if (attempt == 3 || !isRetryable(exc)) {
                    break;
                }
                Thread.sleep((long) Math.pow(2, attempt - 1) * 1000L);
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("Vision model call failed without a captured exception.");
    }

    static Map<String, Object> buildImageContent(String imagePathOrUrl) throws Exception {
        if (imagePathOrUrl == null || imagePathOrUrl.isBlank()) {
            throw new FileNotFoundException("Image path does not exist or is not a file: " + imagePathOrUrl);
        }
        if (imagePathOrUrl.contains(SANDBOX_PATH_MARKER)) {
            throw new IllegalArgumentException(
                    "Vision tools cannot access sandbox-only paths. Use a local path outside the sandbox or an https URL."
            );
        }
        if (isHttpUrl(imagePathOrUrl)) {
            return imageContent(imagePathOrUrl);
        }

        Path imagePath = Path.of(imagePathOrUrl).toAbsolutePath().normalize();
        if (!Files.exists(imagePath) || !Files.isRegularFile(imagePath)) {
            throw new FileNotFoundException("Image path does not exist or is not a file: " + imagePathOrUrl);
        }

        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        return imageContent("data:" + guessMimeType(imagePath) + ";base64," + base64);
    }

    static String defaultVqaPrompt(String ocrText, String question) {
        return """
                You are a careful visual analysis assistant.
                Use the image and the OCR result below to answer the user's question accurately.

                OCR result:
                %s

                Question:
                %s

                Provide a concise but complete answer. If something is uncertain, say so explicitly."""
                .formatted(ocrText, question);
    }

    private static String invokeChatCompletion(
            VisionModelConfig config,
            String prompt,
            Map<String, Object> imageContent
    ) throws Exception {
        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "text");
        textContent.put("text", prompt);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(textContent, imageContent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", List.of(message));

        HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri(config.getBaseUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Vision model request failed with status " + response.statusCode() + ": " + response.body());
        }

        String responseText = extractResponseText(MAPPER.readTree(response.body()));
        if (responseText.isBlank()) {
            throw new IllegalArgumentException("Vision model returned empty content.");
        }
        return responseText;
    }

    private static URI chatCompletionsUri(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (trimmed.endsWith("/chat/completions")) {
            return URI.create(trimmed);
        }
        return URI.create(trimmed + "/chat/completions");
    }

    private static String extractResponseText(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText().trim();
        }
        if (content.isArray()) {
            List<String> chunks = new ArrayList<>();
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText()) && item.has("text")) {
                    chunks.add(item.path("text").asText().trim());
                } else if (item.has("text")) {
                    chunks.add(item.path("text").asText().trim());
                }
            }
            return String.join("\n", chunks).trim();
        }
        return content.asText("").trim();
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null;
        } catch (IllegalArgumentException exc) {
            return false;
        }
    }

    private static boolean isRetryable(Exception exc) {
        String text = String.valueOf(exc.getMessage());
        return text.contains("429")
                || text.contains("500")
                || text.contains("502")
                || text.contains("503")
                || text.contains("504");
    }

    private static Map<String, Object> imageContent(String imageUrl) {
        Map<String, Object> imageUrlPayload = new LinkedHashMap<>();
        imageUrlPayload.put("url", imageUrl);

        Map<String, Object> imageContent = new LinkedHashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", imageUrlPayload);
        return imageContent;
    }

    private static String guessMimeType(Path imagePath) throws IOException {
        String fileName = imagePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        }
        String probed = Files.probeContentType(imagePath);
        return probed == null || probed.isBlank() ? "image/jpeg" : probed;
    }

    private static String firstNonBlank(Map<String, String> env, String... keys) {
        for (String key : keys) {
            String value = env.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
