package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.harness.schema.config.AudioModelConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Package-local helpers that mirror Python's audio module functions.
 *
 * <p>Mirrors Python's helper functions in
 * {@code openjiuwen.harness.tools.multimodal.audio}.
 */
final class AudioSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SANDBOX_PATH_MARKER = "home/user";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private AudioSupport() {
    }

    static AudioModelConfig requireAudioModelConfig(AudioModelConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Audio model config is not set. Pass "
                    + "DeepAgentConfig.audio_model_config or construct the tool with AudioModelConfig.");
        }
        if (isBlank(config.getBaseUrl())) {
            throw new IllegalArgumentException("Audio model config missing base_url.");
        }
        return config;
    }

    static ResolvedAudioPath resolveAudioPath(String audioPathOrUrl, AudioModelConfig config) throws Exception {
        String value = audioPathOrUrl == null ? "" : audioPathOrUrl;
        if (value.contains(SANDBOX_PATH_MARKER)) {
            throw new IllegalArgumentException("Audio tools cannot access sandbox-only paths. "
                    + "Use a local path outside the sandbox or an https URL.");
        }
        if (isHttpUrl(value)) {
            return downloadAudio(value, config);
        }

        Path audioPath = Path.of(value).toAbsolutePath().normalize();
        if (!Files.exists(audioPath) || !Files.isRegularFile(audioPath)) {
            throw new IOException("Audio path does not exist or is not a file: " + audioPathOrUrl);
        }
        return new ResolvedAudioPath(audioPath, false);
    }

    static double getAudioDuration(String audioPath) {
        Exception audioError = null;
        try {
            File file = new File(audioPath);
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
                AudioFileFormat format = AudioSystem.getAudioFileFormat(file);
                long frames = stream.getFrameLength();
                float rate = format.getFormat().getFrameRate();
                double duration = rate > 0 ? frames / rate : 0.0;
                if (duration > 0) {
                    return duration;
                }
            }
        } catch (Exception exc) {
            audioError = exc;
        }
        String detail = audioError == null ? "no parser succeeded" : "wave=" + audioError.getMessage();
        throw new IllegalArgumentException("Unable to determine audio duration: " + detail);
    }

    static EncodedAudio encodeAudioFile(String audioPath) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(audioPath));
        String encoded = Base64.getEncoder().encodeToString(data);
        return new EncodedAudio(encoded, fileFormat(audioPath, ""));
    }

    static <T> T callWithRetries(AudioModelConfig config, Callable<T> callable) throws Exception {
        Exception lastError = null;
        int attempts = Math.max(1, config.getMaxRetries());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return callable.call();
            } catch (Exception exc) {
                lastError = exc;
                if (attempt == attempts || !isRetryable(exc)) {
                    break;
                }
                try {
                    Thread.sleep((long) Math.pow(2, attempt - 1) * 1000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
        if (lastError == null) {
            throw new IllegalStateException("Audio model call failed without a captured exception.");
        }
        throw lastError;
    }

    static String invokeAudioTranscription(AudioModelConfig config, String audioPath) throws Exception {
        requireApiKey(config);
        String boundary = "openjiuwen-audio-" + System.nanoTime();
        List<byte[]> body = new ArrayList<>();
        addFormField(body, boundary, "model", config.getTranscriptionModel());
        addFileField(body, boundary, "file", Path.of(audioPath), "application/octet-stream");
        body.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(endpoint(config.getBaseUrl(), "/audio/transcriptions"))
                .timeout(Duration.ofSeconds(config.getHttpTimeout()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Audio transcription request failed: HTTP " + response.statusCode()
                    + " " + response.body());
        }
        JsonNode payload = MAPPER.readTree(response.body());
        String text = payload.path("text").asText("").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Audio transcription returned empty content.");
        }
        return text;
    }

    static AudioQuestionAnsweringTool.AudioQaResult invokeAudioQuestionAnswering(
            AudioModelConfig config,
            String audioPath,
            String question) throws Exception {
        requireApiKey(config);
        EncodedAudio encoded = encodeAudioFile(audioPath);
        double duration = getAudioDuration(audioPath);
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("data", encoded.data());
        audio.put("format", encoded.format());

        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "Answer the following question based on the given audio information:\n\n" + question);
        Map<String, Object> audioContent = new LinkedHashMap<>();
        audioContent.put("type", "input_audio");
        audioContent.put("input_audio", audio);

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content",
                        "You are a helpful assistant specializing in audio analysis."),
                Map.of("role", "user", "content", List.of(textContent, audioContent))
        );
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.getQuestionAnsweringModel());
        requestBody.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(endpoint(config.getBaseUrl(), "/chat/completions"))
                .timeout(Duration.ofSeconds(config.getHttpTimeout()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Audio question answering request failed: HTTP " + response.statusCode()
                    + " " + response.body());
        }
        String answer = extractMessageText(MAPPER.readTree(response.body()));
        if (answer.isEmpty()) {
            throw new IllegalArgumentException("Audio question answering returned empty content.");
        }
        return new AudioQuestionAnsweringTool.AudioQaResult(answer, duration);
    }

    static Map<String, Object> invokeAudioMetadata(AudioModelConfig config, String audioPath) throws Exception {
        double duration = getAudioDuration(audioPath);
        Map<String, Object> result = baseMetadataResult(roundTwo(duration));
        if (isBlank(config.getAcrAccessKey()) || isBlank(config.getAcrAccessSecret())) {
            result.put("note", "Title and artist identification is disabled because "
                    + "ACR credentials are not configured.");
            return result;
        }
        if (duration > 15) {
            result.put("note", "Audio metadata identification works best for clips shorter than 15 seconds.");
            return result;
        }
        return identifyWithAcr(config, audioPath, result);
    }

    static void deleteIfTemporary(ResolvedAudioPath resolved) {
        if (resolved != null && resolved.temporary()) {
            try {
                Files.deleteIfExists(resolved.path());
            } catch (IOException ignored) {
                // Python also treats cleanup failures as non-fatal.
            }
        }
    }

    private static ResolvedAudioPath downloadAudio(String url, AudioModelConfig config) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(config.getHttpTimeout()))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to fetch audio URL: HTTP " + response.statusCode());
        }
        byte[] body = response.body();
        if (body.length > config.getMaxAudioBytes()) {
            throw new IllegalArgumentException("Audio file exceeds size limit.");
        }
        String contentType = response.headers().firstValue("content-type").orElse("");
        Path temp = Files.createTempFile("openjiuwen-audio-", audioExtension(url, contentType));
        Files.write(temp, body);
        return new ResolvedAudioPath(temp, true);
    }

    private static Map<String, Object> identifyWithAcr(AudioModelConfig config, String audioPath,
            Map<String, Object> result) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String stringToSign = String.join("\n", List.of(
                "POST",
                "/v1/identify",
                config.getAcrAccessKey(),
                "audio",
                "1",
                timestamp
        ));
        String signature = hmacSha1Base64(config.getAcrAccessSecret(), stringToSign);
        String boundary = "openjiuwen-acr-" + System.nanoTime();
        List<byte[]> body = new ArrayList<>();
        addFormField(body, boundary, "access_key", config.getAcrAccessKey());
        addFormField(body, boundary, "sample_bytes", String.valueOf(Files.size(Path.of(audioPath))));
        addFormField(body, boundary, "timestamp", timestamp);
        addFormField(body, boundary, "signature", signature);
        addFormField(body, boundary, "data_type", "audio");
        addFormField(body, boundary, "signature_version", "1");
        addFileField(body, boundary, "sample", Path.of(audioPath), "application/octet-stream");
        body.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getAcrBaseUrl()))
                .timeout(Duration.ofSeconds(config.getHttpTimeout()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode metadata = MAPPER.readTree(response.body()).path("metadata");
        JsonNode humming = metadata.path("humming");
        if (humming.isArray() && !humming.isEmpty()) {
            Optional<JsonNode> best = iterable(humming).stream()
                    .max(Comparator.comparingLong(node -> node.path("duration_ms").asLong(0)));
            best.ifPresent(node -> fillIdentifiedResult(result, node, true));
            return result;
        }
        JsonNode music = metadata.path("music");
        if (music.isArray() && !music.isEmpty()) {
            fillIdentifiedResult(result, music.get(0), false);
            return result;
        }
        result.put("note", "No metadata found for the given audio file.");
        return result;
    }

    private static List<JsonNode> iterable(JsonNode array) {
        List<JsonNode> nodes = new ArrayList<>();
        array.forEach(nodes::add);
        return nodes;
    }

    private static void fillIdentifiedResult(Map<String, Object> result, JsonNode node, boolean includeScore) {
        result.put("title", textOrNull(node.path("title")));
        JsonNode artists = node.path("artists");
        result.put("artist", artists.isArray() && !artists.isEmpty() ? textOrNull(artists.get(0).path("name")) : null);
        result.put("release_date", textOrNull(node.path("release_date")));
        if (includeScore) {
            result.put("score", node.path("score").isMissingNode() ? null : node.path("score").asText());
        }
        result.put("identified", true);
    }

    private static Map<String, Object> baseMetadataResult(double duration) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("duration_seconds", duration);
        result.put("title", null);
        result.put("artist", null);
        result.put("release_date", null);
        result.put("score", null);
        result.put("identified", false);
        result.put("note", null);
        return result;
    }

    private static String extractMessageText(JsonNode payload) {
        JsonNode content = payload.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText().trim();
        }
        if (content.isArray()) {
            List<String> chunks = new ArrayList<>();
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText())) {
                    chunks.add(item.path("text").asText("").trim());
                } else if (item.has("text")) {
                    chunks.add(item.path("text").asText("").trim());
                }
            }
            return String.join("\n", chunks).trim();
        }
        return content.isMissingNode() || content.isNull() ? "" : content.asText("").trim();
    }

    private static URI endpoint(String baseUrl, String path) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(trimmed + path);
    }

    private static void requireApiKey(AudioModelConfig config) {
        if (isBlank(config.getApiKey())) {
            throw new IllegalArgumentException("Audio model config missing api_key.");
        }
    }

    private static boolean isRetryable(Exception exc) {
        String text = String.valueOf(exc.getMessage());
        return text.contains("429") || text.contains("500") || text.contains("502")
                || text.contains("503") || text.contains("504");
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

    private static String audioExtension(String url, String contentType) {
        String loweredUrl = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        for (String ext : List.of(".mp3", ".wav", ".m4a", ".aac", ".ogg", ".flac", ".wma")) {
            if (loweredUrl.endsWith(ext)) {
                return ext;
            }
        }
        String loweredType = contentType.toLowerCase(Locale.ROOT);
        if (loweredType.contains("mp3") || loweredType.contains("mpeg")) {
            return ".mp3";
        }
        if (loweredType.contains("wav")) {
            return ".wav";
        }
        if (loweredType.contains("m4a")) {
            return ".m4a";
        }
        if (loweredType.contains("aac")) {
            return ".aac";
        }
        if (loweredType.contains("ogg")) {
            return ".ogg";
        }
        if (loweredType.contains("flac")) {
            return ".flac";
        }
        return ".mp3";
    }

    private static String fileFormat(String audioPath, String contentType) {
        String suffix = audioExtension("file:///" + Path.of(audioPath).getFileName(), contentType);
        return switch (suffix) {
            case ".wav" -> "wav";
            case ".mp3" -> "mp3";
            default -> "mp3";
        };
    }

    private static void addFormField(List<byte[]> body, String boundary, String name, String value) {
        body.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.add(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.add((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        body.add("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void addFileField(List<byte[]> body, String boundary, String name, Path path, String contentType)
            throws IOException {
        body.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.add(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                + path.getFileName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.add(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.add(Files.readAllBytes(path));
        body.add("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha1Base64(String secret, String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.US_ASCII), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static double roundTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    record ResolvedAudioPath(Path path, boolean temporary) {
    }

    record EncodedAudio(String data, String format) {
    }
}
