/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helpers for the skill omni creation scripts.
 * <p>
 * Mirrors Python's {@code openjiuwen/dev_tools/skill_creator/skills/skill_omni_creation/scripts/common.py}.
 */
public final class SkillOmniCommon {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public static final String API_BASE = System.getenv().getOrDefault("API_BASE", "");
    public static final String API_KEY = System.getenv().getOrDefault("API_KEY", "");
    public static final String MODEL = System.getenv().getOrDefault("MODEL_NAME", "");

    public static final String STEALTH_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public static final Set<String> SUPPORTED_EXTS = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif");
    public static final Set<String> SUPPORTED_MIMES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    public static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    public static final int MIN_DIMENSION = 80;
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    public static final int FETCH_WORKERS = 10;
    public static final int FILTER_BATCH = 5;
    public static final int FILTER_WORKERS = 3;
    public static final int VIDEO_FRAMES = 4;

    public static final String FILTER_PROMPT = """
            You are reviewing screenshots from a software guide or manual.
            Keep images that clearly show software UI, windows, menus, dialogs, controls, or workflow steps.
            Skip decorative icons, logos, ads, and unrelated imagery.
            If an image comes from a subpage, apply a stricter relevance check.
            Use both image content and the nearby text context.
            Reply with ONLY a JSON array of strings, each exactly "KEEP" or "SKIP".
            """;

    public static final String SKILL_PROMPT = """
            You are building a Skill file for an AI agent to learn and execute a software task.

            Output format:
            ---
            name: <snake_case_skill_name>
            description: <1-3 sentences in English>
            ---

            # <Skill Name>

            ## Steps

            Rules:
            - Use heading and text blocks in DOM order.
            - Every valid image path must be referenced somewhere in the output.
            - Never invent image paths, buttons, labels, or steps not grounded in the blocks.
            - If there are h2 headings, group by h2. If there are h3 headings inside an h2 group, group by h3 too.
            - If there are no h2 or h3 headings, emit one flat numbered list.
            - Put each image on its own line with a blank line before and after it.
            - Output ONLY the Skill markdown.
            """;

    public static final String SCRAPE_FALLBACK_PROMPT = """
            You are a web scraper assistant. Fetch the page URL and extract its main content as JSON with:
            {
              "title": "<page title>",
              "blocks": [
                {"type": "heading", "level": 1-4, "text": "..."},
                {"type": "text", "text": "..."},
                {"type": "image", "url": "...", "alt": "..."}
              ],
              "video_urls": []
            }
            Keep DOM order and skip navigation, footers, cookie banners, ads, and decorative icons.
            Return ONLY valid JSON.
            """;

    private static final Pattern LEADING_FENCE = Pattern.compile("^```[a-zA-Z]*\\R?");
    private static final Pattern TRAILING_FENCE = Pattern.compile("\\R?```$");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)]\\(([^)]+)\\)");

    private SkillOmniCommon() {
    }

    public interface ChatClient {
        String chat(
                String systemPrompt,
                Object userContent,
                double temperature,
                int maxTokens,
                Map<String, Object> extraBody
        ) throws IOException, InterruptedException;
    }

    public static final class OpenAiHttpChatClient implements ChatClient {
        private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

        @Override
        public String chat(
                String systemPrompt,
                Object userContent,
                double temperature,
                int maxTokens,
                Map<String, Object> extraBody
        ) throws IOException, InterruptedException {
            if (API_BASE.isBlank() || MODEL.isBlank()) {
                throw new IllegalStateException("API_BASE and MODEL_NAME must be configured");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", MODEL);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)
            ));
            payload.put("temperature", temperature);
            payload.put("max_tokens", maxTokens);
            if (extraBody != null && !extraBody.isEmpty()) {
                payload.putAll(extraBody);
            }

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(API_BASE) + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(toJson(payload), StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> response = httpClient.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() >= 400) {
                throw new IOException("OpenAI HTTP error: " + response.statusCode() + " body=" + response.body());
            }
            Map<String, Object> body = fromJson(response.body());
            List<?> choices = (List<?>) body.getOrDefault("choices", List.of());
            if (choices.isEmpty()) {
                return "";
            }
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            Object content = message == null ? "" : message.get("content");
            return content == null ? "" : String.valueOf(content).trim();
        }
    }

    public static Map<String, Object> loadJson(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), new TypeReference<>() {
        });
    }

    public static void writeJson(Path path, Map<String, Object> data) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, toJson(data), StandardCharsets.UTF_8);
    }

    public static String toJson(Object data) throws JsonProcessingException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    }

    public static Map<String, Object> fromJson(String data) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(data, new TypeReference<>() {
        });
    }

    public static List<Object> fromJsonList(String data) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(data, new TypeReference<>() {
        });
    }

    public static String stripJsonFence(String text) {
        String cleaned = text == null ? "" : text.trim();
        cleaned = LEADING_FENCE.matcher(cleaned).replaceFirst("");
        cleaned = TRAILING_FENCE.matcher(cleaned).replaceFirst("");
        return cleaned.trim();
    }

    public static String encodeB64(byte[] data, String mime) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
    }

    public static String slugify(String text) {
        String cleaned = text == null ? "" : text.toLowerCase().trim();
        cleaned = cleaned.replaceAll("[^\\w\\s-]", "");
        cleaned = cleaned.replaceAll("[\\s_-]+", "_");
        return cleaned.length() <= 80 ? cleaned : cleaned.substring(0, 80);
    }

    public static String urlToSlug(String url) {
        try {
            URI uri = new URI(url);
            String raw = (safe(uri.getHost()) + safe(uri.getPath())).replaceAll("^/+", "").replaceAll("/+$", "");
            return slugify(raw);
        } catch (URISyntaxException exception) {
            return slugify(url);
        }
    }

    public static Path workPath(String slug, String filename) {
        Path base = Path.of("work").resolve(slug);
        return filename == null || filename.isEmpty() ? base : base.resolve(filename);
    }

    public static String imageExt(String url, String mime) {
        String ext = "";
        try {
            ext = Path.of(new URI(url).getPath()).getFileName().toString();
            int index = ext.lastIndexOf('.');
            ext = index >= 0 ? ext.substring(index).toLowerCase() : "";
        } catch (Exception ignored) {
            ext = "";
        }
        if (!SUPPORTED_EXTS.contains(ext)) {
            return MIME_TO_EXT.getOrDefault(mime, ".png");
        }
        return ext;
    }

    public static Map<String, Map<String, String>> saveFetchedAssets(
            Map<String, AssetPayload> fetched,
            Path assetDir,
            String prefix
    ) throws IOException {
        Files.createDirectories(assetDir);
        Map<String, Map<String, String>> manifest = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, AssetPayload> entry : fetched.entrySet()) {
            String rel = prefix + "_" + String.format("%03d", index) + imageExt(entry.getKey(), entry.getValue().mime());
            Path outputPath = assetDir.resolve(rel);
            Files.write(outputPath, entry.getValue().data());
            manifest.put(entry.getKey(), Map.of("path", rel, "mime", entry.getValue().mime()));
            index++;
        }
        return manifest;
    }

    public static Map<String, AssetPayload> loadFetchedAssets(Path assetDir, Map<String, Map<String, String>> manifest)
            throws IOException {
        Map<String, AssetPayload> fetched = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : manifest.entrySet()) {
            Map<String, String> meta = entry.getValue();
            Path path = assetDir.resolve(meta.get("path"));
            fetched.put(entry.getKey(), new AssetPayload(Files.readAllBytes(path), meta.get("mime")));
        }
        return fetched;
    }

    public static List<Map<String, Object>> blocksWithPathsAsString(List<Map<String, Object>> blocks) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> block : blocks) {
            Map<String, Object> copy = new LinkedHashMap<>(block);
            if ("image".equals(copy.get("type")) && copy.get("path") instanceof Path path) {
                copy.put("path", path.toString().replace('\\', '/'));
            }
            result.add(copy);
        }
        return result;
    }

    public static List<Map<String, Object>> blocksWithPathsAsPath(List<Map<String, Object>> blocks) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> block : blocks) {
            Map<String, Object> copy = new LinkedHashMap<>(block);
            if ("image".equals(copy.get("type")) && copy.get("path") instanceof String pathString) {
                copy.put("path", Path.of(pathString));
            }
            result.add(copy);
        }
        return result;
    }

    public static String stripHallucinatedImages(String markdown, Set<String> validPaths) {
        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(2).trim();
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(validPaths.contains(path) ? matcher.group(0) : ""));
        }
        matcher.appendTail(buffer);

        List<String> lines = new ArrayList<>();
        for (String line : buffer.toString().split("\\R", -1)) {
            if (!line.isBlank()) {
                lines.add(line);
            } else if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
        }
        return String.join(System.lineSeparator(), lines).trim();
    }

    public record AssetPayload(byte[] data, String mime) {
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
