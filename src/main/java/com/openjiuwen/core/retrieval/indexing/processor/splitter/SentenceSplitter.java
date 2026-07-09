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
 * 
 * @since 0.1.7
 */
public class SentenceSplitter extends Splitter {
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^.!?。！？]+[.!?。！？]*", Pattern.MULTILINE);

    private final Function<String, List<String>> tokenizer;
    private final Function<List<String>, String> tokenizerDecoder;
    private final String defaultLanguage;

    /**
     * SentenceSplitter.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @since 0.1.7
     */
    public SentenceSplitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, null, "auto");
    }

    /**
     * SentenceSplitter.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @param tokenizer tokenizer
     * @param language language
     * @since 0.1.7
     */
    public SentenceSplitter(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer,
            String language) {
        this(chunkSize, chunkOverlap, tokenizer, language, null);
    }

    /**
     * SentenceSplitter.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @param tokenizer tokenizer
     * @param language language
     * @param tokenizerDecoder tokenizerDecoder
     * @since 0.1.7
     */
    public SentenceSplitter(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language,
            Function<List<String>, String> tokenizerDecoder) {
        super(chunkSize, chunkOverlap);
        this.tokenizer = tokenizer;
        this.tokenizerDecoder = tokenizerDecoder;
        this.defaultLanguage = language == null ? "auto" : language;
    }

    /**
     * splitText.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> splitText(String text) {
        return splitSpans(text).stream().map(SplitSpan::text).toList();
    }

    /**
     * splitSpans.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<SplitSpan> splitSpans(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String language = "auto".equalsIgnoreCase(defaultLanguage) ? detectLanguage(text) : defaultLanguage;
        String separator = "zh".equalsIgnoreCase(language) ? "" : " ";
        List<SentenceSpan> sentences = sentenceSpans(text, language);
        List<SplitSpan> result = new ArrayList<>();
        List<SentenceSpan> window = new ArrayList<>();
        int tokenCount = 0;
        for (SentenceSpan sentence : sentences) {
            int sentenceTokens = tokenCount(sentence.text());
            if (sentenceTokens > chunkSize) {
                flushWindow(result, window, separator);
                window = new ArrayList<>();
                tokenCount = 0;
                if (tokenizerDecoder != null) {
                    result.addAll(splitLongSegment(sentence));
                } else {
                    result.add(new SplitSpan(sentence.text(), sentence.start(), sentence.end()));
                }
                continue;
            }
            if (!window.isEmpty() && tokenCount + sentenceTokens > chunkSize) {
                flushWindow(result, window, separator);
                List<SentenceSpan> overlapWindow = new ArrayList<>();
                int overlapTokens = 0;
                for (int i = window.size() - 1; i >= 0; i--) {
                    SentenceSpan item = window.get(i);
                    int itemTokens = tokenCount(item.text());
                    if (overlapTokens + itemTokens > chunkOverlap) {
                        break;
                    }
                    overlapWindow.add(0, item);
                    overlapTokens += itemTokens;
                }
                window = overlapWindow;
                tokenCount = overlapTokens;
            }
            window.add(sentence);
            tokenCount += sentenceTokens;
        }
        if (!window.isEmpty()) {
            flushWindow(result, window, separator);
        }
        return result;
    }

    /**
     * tokenCount.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    private int tokenCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        if (tokenizer != null) {
            List<String> tokens = tokenizer.apply(trimmed);
            return tokens == null || tokens.isEmpty() ? 0 : tokens.size();
        }
        return trimmed.length();
    }

    /**
     * detectLanguage.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    public static String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }
        int chinese = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                chinese++;
            }
        }
        int threshold = (int) (text.length() * 0.1);
        if (chinese >= threshold) {
            return "zh";
        }
        boolean hasChinesePunctuation = count(text, '？') > count(text, '?') && count(text, '！') > count(text, '!');
        return hasChinesePunctuation ? "zh" : "en";
    }

    /**
     * sentenceSpans.
     * 
     * @param text text
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static List<SentenceSpan> sentenceSpans(String text, String language) {
        List<SentenceSpan> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                int leading = matcher.group().indexOf(sentence);
                int start = matcher.start() + Math.max(leading, 0);
                sentences.add(new SentenceSpan(sentence, start, start + sentence.length()));
            }
        }
        if (sentences.isEmpty()) {
            return Collections.singletonList(new SentenceSpan(text, 0, text.length()));
        }
        if ("zh".equalsIgnoreCase(language)) {
            return sentences;
        }
        return sentences.stream().map(sentence -> new SentenceSpan(sentence.text().replaceAll("\\s+", " ").trim(),
                sentence.start(), sentence.end())).toList();
    }

    /**
     * splitLongSegment.
     * 
     * @param sentence sentence
     * @return the result
     * @since 0.1.7
     */
    private List<SplitSpan> splitLongSegment(SentenceSpan sentence) {
        List<String> tokens = tokens(sentence.text());
        if (tokens.isEmpty()) {
            return List.of(new SplitSpan(sentence.text(), sentence.start(), sentence.end()));
        }
        int step = Math.max(1, chunkSize - chunkOverlap);
        List<SplitSpan> out = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(tokens.size(), start + chunkSize);
            List<String> window = tokens.subList(start, end);
            String chunk = tokenizerDecoder.apply(window);
            if (chunk != null && !chunk.isBlank()) {
                out.add(new SplitSpan(chunk, sentence.start(), sentence.end()));
            }
            if (end >= tokens.size()) {
                break;
            }
        }
        return out.isEmpty() ? List.of(new SplitSpan(sentence.text(), sentence.start(), sentence.end())) : out;
    }

    /**
     * tokens.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    private List<String> tokens(String text) {
        if (tokenizer != null) {
            List<String> tokens = tokenizer.apply(text);
            return tokens == null ? List.of() : tokens;
        }
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
    }

    /**
     * flushWindow.
     * 
     * @param result result
     * @param window window
     * @param separator separator
     * @since 0.1.7
     */
    private static void flushWindow(List<SplitSpan> result, List<SentenceSpan> window, String separator) {
        if (window.isEmpty()) {
            return;
        }
        String text =
            window.stream().map(SentenceSpan::text).collect(java.util.stream.Collectors.joining(separator)).trim();
        result.add(new SplitSpan(text, window.get(0).start(), window.get(window.size() - 1).end()));
    }

    /**
     * count.
     * 
     * @param text text
     * @param needle needle
     * @return the result
     * @since 0.1.7
     */
    private static int count(String text, char needle) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == needle) {
                total++;
            }
        }
        return total;
    }

    /**
     * SentenceSpan.
     * 
     * @param text text
     * @param start start
     * @param end end
     * @since 0.1.7
     */
    private record SentenceSpan(String text, int start, int end) {
    }
}
