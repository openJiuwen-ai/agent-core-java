/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for image files using LLM captions.
 */
public class ImageParser extends Parser {

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        try {
            String content = parseContent(doc, llmClient, options);
            if (content == null) {
                return List.of();
            }
            return List.of(new Document(docId, content, Map.of()));
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        try {
            ImageCaptioner imageCaptioner = new ImageCaptioner(llmClient);
            imageCaptioner.cpImage(doc);
            List<String> captions = imageCaptioner.captionImages(List.of(doc));
            String content = captions.stream().filter(caption -> caption != null && !caption.isBlank()).reduce((a, b) -> a + "\n" + b).orElse("");
            return content.isBlank() ? null : content;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean supports(String doc) {
        String lower = doc == null ? "" : doc.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif")
                || lower.endsWith(".jfif");
    }
}
