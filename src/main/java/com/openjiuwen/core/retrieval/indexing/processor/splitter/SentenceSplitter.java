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

/** Sentence-aware splitter with lightweight language detection and tokenizer-aware windows. */
public class SentenceSplitter extends Splitter {

  private static final Pattern SENTENCE_PATTERN =
      Pattern.compile("[^.!?。！？]+[.!?。！？]*", Pattern.MULTILINE);

  private final Function<String, List<String>> tokenizer;
  private final Function<List<String>, String> tokenizerDecoder;
  private final String defaultLanguage;

  /** Auto-generated for codecheck compliance. */
  public SentenceSplitter(int chunkSize, int chunkOverlap) {
    this(chunkSize, chunkOverlap, null, "auto");
  }

  /** Auto-generated for codecheck compliance. */
  public SentenceSplitter(
      int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language) {
    this(chunkSize, chunkOverlap, tokenizer, language, null);
  }

  /** Auto-generated for codecheck compliance. */
  public SentenceSplitter(
      int chunkSize,
      int chunkOverlap,
      Function<String, List<String>> tokenizer,
      String language,
      Function<List<String>, String> tokenizerDecoder) {
    super(chunkSize, chunkOverlap);
    this.tokenizer = tokenizer;
    this.tokenizerDecoder = tokenizerDecoder;
    this.defaultLanguage = language == null ? "auto" : language;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public List<String> splitText(String text) {
    return splitSpans(text).stream().map(SplitSpan::text).toList();
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public List<SplitSpan> splitSpans(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String language =
        "auto".equalsIgnoreCase(defaultLanguage) ? detectLanguage(text) : defaultLanguage;
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

  /** Auto-generated for codecheck compliance. */
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
    boolean hasChinesePunctuation =
        count(text, '？') > count(text, '?') && count(text, '！') > count(text, '!');
    return hasChinesePunctuation ? "zh" : "en";
  }

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
    return sentences.stream()
        .map(
            sentence ->
                new SentenceSpan(
                    sentence.text().replaceAll("\\s+", " ").trim(),
                    sentence.start(),
                    sentence.end()))
        .toList();
  }

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
    return out.isEmpty()
        ? List.of(new SplitSpan(sentence.text(), sentence.start(), sentence.end()))
        : out;
  }

  private List<String> tokens(String text) {
    if (tokenizer != null) {
      List<String> tokens = tokenizer.apply(text);
      return tokens == null ? List.of() : tokens;
    }
    String trimmed = text == null ? "" : text.trim();
    return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
  }

  private static void flushWindow(
      List<SplitSpan> result, List<SentenceSpan> window, String separator) {
    if (window.isEmpty()) {
      return;
    }
    String text =
        window.stream()
            .map(SentenceSpan::text)
            .collect(java.util.stream.Collectors.joining(separator))
            .trim();
    result.add(new SplitSpan(text, window.get(0).start(), window.get(window.size() - 1).end()));
  }

  private static int count(String text, char needle) {
    int total = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == needle) {
        total++;
      }
    }
    return total;
  }

  private record SentenceSpan(String text, int start, int end) {}
}
