/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Replaces control characters with spaces.
 * 
 * @since 0.1.7
 */
public class SpecialCharacterNormalizer implements TextPreprocessor {
    private final String charsToRemove;
    private final java.util.Map<String, String> charsToReplace;

    /**
     * SpecialCharacterNormalizer.
     * 
     * @since 0.1.7
     */
    public SpecialCharacterNormalizer() {
        this("", java.util.Map.of());
    }

    /**
     * SpecialCharacterNormalizer.
     * 
     * @param charsToRemove charsToRemove
     * @param charsToReplace charsToReplace
     * @since 0.1.7
     */
    public SpecialCharacterNormalizer(String charsToRemove, java.util.Map<String, String> charsToReplace) {
        this.charsToRemove = charsToRemove != null ? charsToRemove : "";
        this.charsToReplace = charsToReplace != null ? java.util.Map.copyOf(charsToReplace) : java.util.Map.of();
    }

    /**
     * process.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String process(String text) {
        if (text == null) {
            return null;
        }
        String result = text.replaceAll("[\\x00-\\x1F\\x7F]", "");
        result = result.replaceAll("(?U)(?![#*_\\-|]{2,})([^\\w\\s#*_\\-|\\u4e00-\\u9fa5，。！？；：“”‘’（）【】《》、…·]){2,}", "");
        for (var entry : charsToReplace.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        if (!charsToRemove.isEmpty()) {
            for (int i = 0; i < charsToRemove.length(); i++) {
                result = result.replace(String.valueOf(charsToRemove.charAt(i)), "");
            }
        }
        return result;
    }

    /**
     * getCharsToRemove.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCharsToRemove() {
        return charsToRemove;
    }

    /**
     * getCharsToReplace.
     * 
     * @return the result
     * @since 0.1.7
     */
    public java.util.Map<String, String> getCharsToReplace() {
        return charsToReplace;
    }
}
