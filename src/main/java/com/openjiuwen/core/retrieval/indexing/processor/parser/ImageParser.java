/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Parser for image files to generate captions.
 *
 * <p>Mirrors Python's {@code ImageParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/image_parser.py}.</p>
 */
public class ImageParser extends Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageParser.class);
    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of(".png", ".jpg", ".jpeg", ".webp", ".gif", ".jfif");

    @Override
    public CompletableFuture<List<Document>> parse(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        try {
            ImageCaptioner imageCaptioner = createImageCaptioner(llmClient);
            String savedPath = copyImage(imageCaptioner, doc);
            return parseContent(doc, llmClient, options)
                    .handle((content, error) -> {
                        if (error != null) {
                            LOGGER.error("Failed to parse image {}: {}", doc, error.getMessage());
                        }
                        String text = content == null ? "" : content;
                        return List.of(new Document(docId, text, Map.of("image_path", savedPath)));
                    });
        } catch (Exception exception) {
            LOGGER.error("Failed to parse image {}: {}", doc, exception.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * Mirrors Python's {@code ImageParser._parse} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/image_parser.py}.
     */
    @Override
    protected CompletableFuture<String> parseContent(
            String imagePath,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        try {
            ImageCaptioner imageCaptioner = createImageCaptioner(llmClient);
            copyImage(imageCaptioner, imagePath);
            return imageCaptioner.captionImages(List.of(imagePath))
                    .thenApply(ImageParser::joinCaptions)
                    .exceptionally(error -> {
                        LOGGER.error("Failed to parse image {}: {}", imagePath, error.getMessage());
                        return null;
                    });
        } catch (Exception exception) {
            LOGGER.error("Failed to parse image {}: {}", imagePath, exception.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Mirrors Python's {@code ImageCaptioner(llm_client=llm_client)} construction in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/image_parser.py}.
     */
    protected ImageCaptioner createImageCaptioner(BaseModelClient llmClient) {
        return new ImageCaptioner(llmClient);
    }

    /**
     * Mirrors Python's {@code image_captioner.cp_image(...)} call in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/image_parser.py}.
     */
    protected String copyImage(ImageCaptioner imageCaptioner, String imagePath) {
        return ImageCaptioner.cpImage(imagePath);
    }

    @Override
    public boolean supports(String doc) {
        if (doc == null || doc.isBlank()) {
            return false;
        }
        String fileName = Path.of(doc).getFileName().toString().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private static String joinCaptions(List<String> captions) {
        if (captions == null || captions.isEmpty()) {
            return null;
        }
        return captions.stream()
                .filter(caption -> caption != null && !caption.isEmpty())
                .collect(Collectors.joining("\n"));
    }
}
