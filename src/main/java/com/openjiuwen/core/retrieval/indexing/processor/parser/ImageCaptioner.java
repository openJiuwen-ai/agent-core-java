/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Mirrors Python's {@code ImageCaptioner} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
 */
public class ImageCaptioner {

    public static final String IMAGE_CAPTION_PROMPT = "You are an assistant specialized in document and image analysis. "
            + "Your task is to provide a detailed, qualitative description of the provided image "
            + "so that it can be embedded and used for semantic retrieval. "
            + "Describe all visible content including text, figures, charts, tables, diagrams, and layout. "
            + "Focus on what the image conveys and means, not just what it literally depicts. "
            + "Do not include any preamble \u2013 output only the description.";
    public static final String SAVED_IMAGE_DIR = "images";

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageCaptioner.class);
    private static final List<String> SUPPORTED_LLM_CLIENT = List.of("gpt-4o", "gpt-5", "qwen3-vl", "qwen-vl");
    private static final Map<String, String> MIME_FALLBACKS = Map.of(
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".jfif", "image/jpeg",
            ".gif", "image/gif",
            ".webp", "image/webp"
    );
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("image-captioner-io");

    private final Model model;
    private final BaseModelClient baseModelClient;

    public ImageCaptioner() {
        this((Model) null);
    }

    public ImageCaptioner(Model llmClient) {
        this.model = llmClient;
        this.baseModelClient = null;
        warnIfLlmUnsupported();
    }

    public ImageCaptioner(BaseModelClient llmClient) {
        this.model = null;
        this.baseModelClient = llmClient;
        warnIfLlmUnsupported();
    }

    /**
     * Mirrors Python's {@code cp_image} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
     */
    public static String cpImage(String imageLoc) {
        return cpImage(imageLoc, SAVED_IMAGE_DIR);
    }

    /**
     * Mirrors Python's {@code cp_image} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
     */
    public static String cpImage(String imageLoc, String targetDir) {
        Path sourcePath = Path.of(imageLoc).toAbsolutePath().normalize();
        Path allowedBaseDir = sourcePath.getParent();
        if (allowedBaseDir == null) {
            allowedBaseDir = Path.of("").toAbsolutePath().normalize();
        }
        return cpImage(imageLoc, targetDir, allowedBaseDir);
    }

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
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            return destination.toString();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to copy image from: " + imageLoc, exception);
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
     * Mirrors Python's {@code _llm_call_async} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
     */
    public CompletableFuture<String> llmCallAsync(String imageLoc) {
        if (model == null && baseModelClient == null) {
            return CompletableFuture.completedFuture("");
        }
        try {
            Path imagePath = resolveSafeSourcePath(imageLoc);
            String mimeType = mimeType(imagePath);
            String imageBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            List<Map<String, Object>> content = List.of(
                    Map.of("type", "text", "text", IMAGE_CAPTION_PROMPT),
                    Map.of("type", "image_url", "image_url",
                            Map.of("url", "data:" + mimeType + ";base64," + imageBase64))
            );
            LOGGER.info("Calling LLM for image captioning for imageLoc={}", imageLoc);
            return invokeLlm(content)
                    .thenApply(this::contentAsString)
                    .exceptionally(error -> {
                        LOGGER.warn("LLM-based caption invocation failed for {}: {}", imageLoc, error.getMessage());
                        return "";
                    })
                    .toCompletableFuture();
        } catch (Exception exception) {
            LOGGER.warn("LLM-based caption invocation failed for {}: {}", imageLoc, exception.getMessage());
            return CompletableFuture.completedFuture("");
        }
    }

    /**
     * Mirrors Python's {@code caption_images} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
     */
    public CompletableFuture<List<String>> captionImages(List<String> imageLocs) {
        List<String> safeImageLocs = imageLocs == null ? List.of() : List.copyOf(imageLocs);
        CompletableFuture<List<String>> chain = CompletableFuture.completedFuture(new ArrayList<>());
        for (String imageLoc : safeImageLocs) {
            chain = chain.thenCompose(captions -> {
                try {
                    resolveSafeSourcePath(imageLoc);
                } catch (IOException | IllegalArgumentException exception) {
                    LOGGER.warn("Image file {} does not exist, skipping captioning.", imageLoc);
                    captions.add("");
                    return CompletableFuture.completedFuture(captions);
                }
                return llmCallAsync(imageLoc).thenApply(caption -> {
                    captions.add(caption);
                    return captions;
                });
            });
        }
        return chain;
    }

    private CompletionStage<AssistantMessage> invokeLlm(List<Map<String, Object>> content) {
        if (model != null) {
            return model.invoke(List.of(new BaseMessage("user", content)));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return baseModelClient.invoke(List.of(Map.of("role", "user", "content", content)),
                        null, null, null, null, null, null, null, null, null);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, IO_EXECUTOR);
    }

    private String contentAsString(AssistantMessage response) {
        if (response == null) {
            return "";
        }
        Object content = response.getContent();
        return content == null ? String.valueOf(response) : String.valueOf(content);
    }

    private void warnIfLlmUnsupported() {
        Object llmClient = model != null ? model : baseModelClient;
        if (llmClient == null) {
            LOGGER.warn("Image captioning is disabled for empty llmClient. Please ensure an appropriate VLM is used.");
            return;
        }
        String modelName = resolveModelName(llmClient);
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        boolean supported = SUPPORTED_LLM_CLIENT.stream().anyMatch(modelName::startsWith);
        if (!supported) {
            LOGGER.warn("modelName={} may not be supported for imaging captioning. "
                    + "Please ensure an appropriate VLM is used.", modelName);
        }
    }

    private static String resolveModelName(Object llmClient) {
        Object modelConfig = invokeNoArg(llmClient, "getModelConfig");
        if (modelConfig == null) {
            modelConfig = readField(llmClient, "modelConfig");
        }
        if (modelConfig instanceof ModelRequestConfig config) {
            return config.getModelName();
        }
        Object modelName = modelConfig == null ? null : invokeNoArg(modelConfig, "getModelName");
        return modelName == null ? null : String.valueOf(modelName);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String mimeType(Path imagePath) throws IOException {
        String mimeType = Files.probeContentType(imagePath);
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType;
        }
        String fileName = imagePath.getFileName() == null
                ? ""
                : imagePath.getFileName().toString().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : MIME_FALLBACKS.entrySet()) {
            if (fileName.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        LOGGER.warn("Could not determine MIME type for imageLoc={}, using image/png", imagePath);
        return "image/png";
    }
}
