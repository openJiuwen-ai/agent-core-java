/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight image caption helper aligned with the Python retrieval parser stack.
 * 
 * @since 0.1.7
 */
public class ImageCaptioner {
    /**
     * IMAGE_CAPTION_PROMPT.
     * 
     * @since 0.1.7
     */
    public static final String IMAGE_CAPTION_PROMPT = "Write a short caption describing the provided image.";

    /**
     * SAVED_IMAGE_DIR.
     * 
     * @since 0.1.7
     */
    public static final String SAVED_IMAGE_DIR = "images";
    private static final String SAVED_IMAGES_ENV = "OPENJIUWEN_SAVED_IMAGES_DIR";

    private final BaseModelClient llmClient;
    private final Path allowedBaseDir;

    /**
     * ImageCaptioner.
     * 
     * @param llmClient llmClient
     * @since 0.1.7
     */
    public ImageCaptioner(BaseModelClient llmClient) {
        this(llmClient, Path.of(""));
    }

    /**
     * ImageCaptioner with an explicit trusted image-cache root.
     *
     * @param llmClient llmClient
     * @param allowedBaseDir trusted base directory for copied images
     * @since 0.1.13
     */
    public ImageCaptioner(BaseModelClient llmClient, Path allowedBaseDir) {
        if (allowedBaseDir == null) {
            throw new IllegalArgumentException("Allowed image base directory must not be null.");
        }
        this.llmClient = llmClient;
        this.allowedBaseDir = allowedBaseDir.toAbsolutePath().normalize();
    }

    /**
     * cpImage.
     * 
     * @param imageLoc imageLoc
     * @return the result
     * @since 0.1.7
     */
    public String cpImage(String imageLoc) {
        String targetDir = System.getenv(SAVED_IMAGES_ENV);
        if (targetDir == null || targetDir.isBlank()) {
            targetDir = SAVED_IMAGE_DIR;
        }
        return cpImage(imageLoc, targetDir, allowedBaseDir);
    }

    /**
     * cpImage.
     * 
     * @param imageLoc imageLoc
     * @param targetDir targetDir
     * @return the result
     * @since 0.1.7
     */
    public static String cpImage(String imageLoc, String targetDir) {
        return cpImage(imageLoc, targetDir, Path.of(""));
    }

    /**
     * Copy an image only to a directory within a trusted base directory.
     *
     * @param imageLoc source image path
     * @param targetDir target directory, absolute or relative to allowedBaseDir
     * @param allowedBaseDir trusted target-directory root
     * @return copied image path
     * @since 0.1.13
     */
    public static String cpImage(String imageLoc, String targetDir, Path allowedBaseDir) {
        try {
            Path source = resolveSafeSourcePath(imageLoc);
            Path directory = resolveSafeTargetDirectory(targetDir, allowedBaseDir);
            Path destination = directory.resolve(source.getFileName()).normalize();
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                Path realDestination = destination.toRealPath();
                if (!realDestination.startsWith(allowedBaseDir.toRealPath())) {
                    throw new SecurityException("Image destination is outside the allowed base directory.");
                }
                destination = realDestination;
            }
            if (!source.equals(destination)) {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            return destination.toString();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to copy image from: " + imageLoc, ex);
        }
    }

    static Path resolveSafeSourcePath(String imageLoc) throws IOException {
        if (imageLoc == null || imageLoc.isBlank()) {
            throw new IllegalArgumentException("Image path must not be blank.");
        }
        Path source = Path.of(imageLoc).toRealPath();
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Image path is not a regular file: " + imageLoc);
        }
        return source;
    }

    static Path resolveSafeTargetDirectory(String targetDir, Path allowedBaseDir) throws IOException {
        if (targetDir == null || targetDir.isBlank() || allowedBaseDir == null) {
            throw new IllegalArgumentException("Target directory and allowed base directory must not be blank.");
        }
        Files.createDirectories(allowedBaseDir);
        Path normalizedBaseDir = allowedBaseDir.toAbsolutePath().normalize();
        Path requestedDirectory = Path.of(targetDir);
        Path targetDirectory = requestedDirectory.isAbsolute()
                ? requestedDirectory.toAbsolutePath().normalize()
                : normalizedBaseDir.resolve(requestedDirectory).normalize();
        if (!targetDirectory.startsWith(normalizedBaseDir)) {
            throw new SecurityException("Image target directory is outside the allowed base directory.");
        }

        Path existingAncestor = targetDirectory;
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        Path realBaseDir = normalizedBaseDir.toRealPath();
        if (existingAncestor == null || !existingAncestor.toRealPath().startsWith(realBaseDir)) {
            throw new SecurityException("Image target directory is outside the allowed base directory.");
        }

        Files.createDirectories(targetDirectory);
        Path realTargetDirectory = targetDirectory.toRealPath();
        if (!realTargetDirectory.startsWith(realBaseDir)) {
            throw new SecurityException("Image target directory is outside the allowed base directory.");
        }
        return realTargetDirectory;
    }

    /**
     * captionImages.
     * 
     * @param imageLocs imageLocs
     * @return the result
     * @since 0.1.7
     */
    public List<String> captionImages(List<String> imageLocs) {
        List<String> captions = new ArrayList<>();
        if (imageLocs == null) {
            return captions;
        }
        for (String imageLoc : imageLocs) {
            try {
                captions.add(llmCall(resolveSafeSourcePath(imageLoc)));
            } catch (IOException | IllegalArgumentException ex) {
                captions.add("");
            }
        }
        return captions;
    }

    /**
     * llmCall.
     * 
     * @param imageLoc imageLoc
     * @return the result
     * @since 0.1.7
     */
    protected String llmCall(String imageLoc) {
        try {
            return llmCall(resolveSafeSourcePath(imageLoc));
        } catch (IOException | IllegalArgumentException ex) {
            return "";
        }
    }

    private String llmCall(Path imagePath) {
        if (llmClient == null) {
            return "";
        }
        try {
            String mimeType = probeMimeType(imagePath);
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            String imageUrl = "data:" + mimeType + ";base64," + base64;

            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "text", "text", IMAGE_CAPTION_PROMPT));
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));

            List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", content));
            AssistantMessage response =
                llmClient.invoke(messages, null, null, null, null, null, null, null, null, null);
            Object responseContent = response == null ? null : response.getContent();
            return responseContent == null ? "" : responseContent.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * probeMimeType.
     * 
     * @param imagePath imagePath
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static String probeMimeType(Path imagePath) throws IOException {
        String mimeType = Files.probeContentType(imagePath);
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType;
        }
        String lower =
            imagePath.getFileName() == null ? "" : imagePath.getFileName().toString().toLowerCase(Locale.ROOT);
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
