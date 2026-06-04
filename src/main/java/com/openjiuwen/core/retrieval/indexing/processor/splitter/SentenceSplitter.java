/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentence-aware splitter with lightweight language detection and tokenizer-aware windows.
 */
public class SentenceSplitter extends Splitter {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^.!?。！？]+[.!?。！？]*", Pattern.MULTILINE);
    private static final Pattern CHINESE_CHAR = Pattern.compile("[\\p{IsHan}]");

    private final Function<String, List<String>> tokenizer;
    private final String defaultLanguage;

    public SentenceSplitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, null, "auto");
    }

    public SentenceSplitter(int chunkSize,
                            int chunkOverlap,
                            Function<String, List<String>> tokenizer,
                            String language) {
        super(chunkSize, chunkOverlap);
        this.tokenizer = tokenizer;
        this.defaultLanguage = language == null ? "auto" : language;
    }

    @Override
    public List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalizedText = text.trim();
        String language = "auto".equalsIgnoreCase(defaultLanguage) ? detectLanguage(normalizedText) : defaultLanguage;
        String separator = "zh".equalsIgnoreCase(language) ? "" : " ";
        List<String> sentences = splitSentences(normalizedText, language);
        List<String> segments = new ArrayList<>();
        for (String sentence : sentences) {
            if (tokenCount(sentence) > chunkSize) {
                segments.addAll(splitOversizedSentence(sentence, language));
            } else {
                segments.add(sentence);
            }
        }
        List<String> result = new ArrayList<>();
        List<String> window = new ArrayList<>();
        int tokenCount = 0;
        for (String sentence : segments) {
            int sentenceTokens = tokenCount(sentence);
            if (!window.isEmpty() && tokenCount + sentenceTokens > chunkSize) {
                result.add(String.join(separator, window).trim());
                List<String> overlapWindow = new ArrayList<>();
                int overlapTokens = 0;
                for (int i = window.size() - 1; i >= 0; i--) {
                    String item = window.get(i);
                    int itemTokens = tokenCount(item);
                    if (overlapTokens + itemTokens > chunkOverlap) {
                        break;
                    }
                    overlapWindow.add(0, item);
                    overlapTokens += itemTokens;
                }
                window = overlapWindow;
                tokenCount = overlapTokens;
            }
            window.add(sentence.trim());
            tokenCount += sentenceTokens;
        }
        if (!window.isEmpty()) {
            result.add(String.join(separator, window).trim());
        }
        return result;
    }

    private List<String> splitOversizedSentence(String sentence, String language) {
        if (sentence == null || sentence.isBlank()) {
            return List.of();
        }
        List<String> tokens;
        String separator;
        if (tokenizer != null) {
            tokens = tokenizer.apply(sentence.trim());
            separator = "zh".equalsIgnoreCase(language) ? "" : " ";
        } else if ("zh".equalsIgnoreCase(language)) {
            tokens = new ArrayList<>(sentence.trim().length());
            for (int i = 0; i < sentence.trim().length(); i++) {
                tokens.add(String.valueOf(sentence.trim().charAt(i)));
            }
            separator = "";
        } else {
            tokens = List.of(sentence.trim().split("\\s+"));
            separator = " ";
        }
        if (tokens == null || tokens.isEmpty()) {
            return List.of(sentence.trim());
        }
        int step = Math.max(1, chunkSize - chunkOverlap);
        List<String> result = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(tokens.size(), start + chunkSize);
            result.add(String.join(separator, tokens.subList(start, end)).trim());
            if (end >= tokens.size()) {
                break;
            }
        }
        return result;
    }

    private int tokenCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        if (tokenizer != null) {
            List<String> tokens = tokenizer.apply(trimmed);
            return tokens == null || tokens.isEmpty() ? 0 : tokens.size();
        }
        if ("zh".equalsIgnoreCase(detectLanguage(trimmed))) {
            int count = 0;
            Matcher matcher = CHINESE_CHAR.matcher(trimmed);
            while (matcher.find()) {
                count++;
            }
            return count > 0 ? count : trimmed.length();
        }
        String[] parts = trimmed.split("\\s+");
        return parts.length;
    }

    private static String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }
        int chinese = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                chinese++;
            }
        }
        return chinese >= Math.max(1, (int) Math.ceil(text.length() * 0.1)) ? "zh" : "en";
    }

    private static List<String> splitSentences(String text, String language) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        if (sentences.isEmpty()) {
            return Collections.singletonList(text);
        }
        if ("zh".equalsIgnoreCase(language)) {
            return sentences;
        }
        List<String> normalized = new ArrayList<>(sentences.size());
        for (String sentence : sentences) {
            normalized.add(sentence.replaceAll("\\s+", " ").trim());
        }
        return normalized;
    }
}
