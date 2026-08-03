/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code SpecialCharacterNormalizer} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/text_preprocessor.py}.
 */
public class SpecialCharacterNormalizer implements TextPreprocessor {

    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern REDUNDANT_SYMBOLS_PATTERN = Pattern.compile(
            "(?![\\u4e00-\\u9fa5，。！？；：“”‘’（）【】《》、…])"
                    + "(?![#*_\\-]{2,})"
                    + "([^\\w\\s#*_\\-|]){2,}");

    private final String charsToRemove;
    private final Map<String, String> charsToReplace;

    public SpecialCharacterNormalizer() {
        this("", null);
    }

    public SpecialCharacterNormalizer(String charsToRemove) {
        this(charsToRemove, null);
    }

    public SpecialCharacterNormalizer(Map<String, String> charsToReplace) {
        this("", charsToReplace);
    }

    public SpecialCharacterNormalizer(String charsToRemove, Map<String, String> charsToReplace) {
        this.charsToRemove = charsToRemove == null ? "" : charsToRemove;
        this.charsToReplace = charsToReplace == null ? new LinkedHashMap<>() : new LinkedHashMap<>(charsToReplace);
    }

    public String getCharsToRemove() {
        return charsToRemove;
    }

    public Map<String, String> getCharsToReplace() {
        return new LinkedHashMap<>(charsToReplace);
    }

    @Override
    public String process(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String processed = CONTROL_CHAR_PATTERN.matcher(text).replaceAll("");
        processed = REDUNDANT_SYMBOLS_PATTERN.matcher(processed).replaceAll("");

        for (Map.Entry<String, String> entry : charsToReplace.entrySet()) {
            processed = processed.replace(entry.getKey(), entry.getValue());
        }

        if (!charsToRemove.isEmpty()) {
            StringBuilder builder = new StringBuilder(processed.length());
            for (int i = 0; i < processed.length(); i++) {
                char current = processed.charAt(i);
                if (charsToRemove.indexOf(current) < 0) {
                    builder.append(current);
                }
            }
            processed = builder.toString();
        }

        return processed;
    }
}
