/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight image caption helper aligned with the Python retrieval parser stack.
 */
public class ImageCaptioner {

    public static final String IMAGE_CAPTION_PROMPT = "Write a short caption describing the provided image.";
    public static final String SAVED_IMAGE_DIR = "images";
    private static final String SAVED_IMAGES_ENV = "OPENJIUWEN_SAVED_IMAGES_DIR";

    private final BaseModelClient llmClient;

    public ImageCaptioner(BaseModelClient llmClient) {
        this.llmClient = llmClient;
    }

    public String cpImage(String imageLoc) {
        String targetDir = System.getenv(SAVED_IMAGES_ENV);
        if (targetDir == null || targetDir.isBlank()) {
            targetDir = SAVED_IMAGE_DIR;
        }
        return cpImage(imageLoc, targetDir);
    }

    public static String cpImage(String imageLoc, String targetDir) {
        Path source = Path.of(imageLoc);
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Image not found at: " + imageLoc);
        }
        try {
            Path directory = Path.of(targetDir);
            Files.createDirectories(directory);
            Path destination = directory.resolve(source.getFileName().toString());
            if (!source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            return destination.toString();
        } catch (IOException ex) {
            return source.toString();
        }
    }

    public List<String> captionImages(List<String> imageLocs) {
        List<String> captions = new ArrayList<>();
        if (imageLocs == null) {
            return captions;
        }
        for (String imageLoc : imageLocs) {
            if (imageLoc == null || !Files.exists(Path.of(imageLoc))) {
                captions.add("");
                continue;
            }
            captions.add(llmCall(imageLoc));
        }
        return captions;
    }

    protected String llmCall(String imageLoc) {
        if (llmClient == null) {
            return "";
        }
        try {
            String mimeType = probeMimeType(Path.of(imageLoc));
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(imageLoc)));
            String imageUrl = "data:" + mimeType + ";base64," + base64;

            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "text", "text", IMAGE_CAPTION_PROMPT));
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));

            List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", content));
            AssistantMessage response = llmClient.invoke(messages, null, null, null, null, null, null, null, null, null);
            Object responseContent = response == null ? null : response.getContent();
            return responseContent == null ? "" : responseContent.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private static String probeMimeType(Path imagePath) throws IOException {
        String mimeType = Files.probeContentType(imagePath);
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType;
        }
        String lower = imagePath.getFileName() == null ? "" : imagePath.getFileName().toString().toLowerCase();
        Map<String, String> fallbackTypes = new LinkedHashMap<>();
        fallbackTypes.put(".png", "image/png");
        fallbackTypes.put(".jpg", "image/jpeg");
        fallbackTypes.put(".jpeg", "image/jpeg");
        fallbackTypes.put(".jfif", "image/jpeg");
        fallbackTypes.put(".gif", "image/gif");
        fallbackTypes.put(".webp", "image/webp");
        for (Map.Entry<String, String> entry : fallbackTypes.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "image/png";
    }
}
