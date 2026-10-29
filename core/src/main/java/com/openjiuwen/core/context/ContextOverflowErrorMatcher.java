/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.common.exception.BaseError;

import java.lang.reflect.InaccessibleObjectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Detects provider errors that describe an input/context length overflow.
 *
 * <p>Mirrors Python's {@code ContextEngine.is_context_overflow_error} /
 * {@code _iter_exception_texts} in
 * {@code openjiuwen/core/context_engine/context_engine.py}.</p>
 */
final class ContextOverflowErrorMatcher {
    private static final int MAX_DEPTH = 6;

    /**
     * Phrases that identify context/input overflow. Includes Bailian-style
     * wording from issue #202 ({@code range of input length} / 输入长度超过).
     */
    private static final List<String> CONTEXT_OVERFLOW_PHRASES = List.of(
            "context length",
            "context window",
            "maximum context",
            "context limit",
            "prompt is too long",
            "prompt too long",
            "input is too long",
            "input too long",
            "range of input length",
            "input length",
            "输入长度超过"
    );

    private static final List<String> CONTEXT_OVERFLOW_FIELDS = List.of(
            "message",
            "body",
            "details",
            "error",
            "errors",
            "response",
            "code",
            "type",
            "param",
            "status_code",
            "status",
            "text",
            "content",
            "cause"
    );

    private ContextOverflowErrorMatcher() {
    }

    /**
     * Returns whether {@code exception} describes an input context overflow.
     *
     * @param exception provider or wrapped model error; {@code null} is not overflow
     * @param extraKeywords optional additional phrases (already lower-cased preferred)
     * @return {@code true} when a recoverable context-overflow signal is found
     */
    static boolean matches(Throwable exception, Collection<String> extraKeywords) {
        if (exception == null) {
            return false;
        }
        List<String> phrases = mergePhrases(extraKeywords);
        for (String value : iterExceptionTexts(exception, 0, new IdentityHashMap<>())) {
            String message = normalize(value);
            if (containsAny(message, phrases)) {
                return true;
            }
            if (message.contains("token limit")
                    && !containsAny(message, List.of("output", "completion", "response"))) {
                return true;
            }
            if (message.contains("too long")
                    && containsAny(message, List.of("context", "prompt", "input"))) {
                return true;
            }
            if ((message.contains("exceed") || message.contains("over limit"))
                    && containsAny(message, List.of("context", "prompt", "input"))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> mergePhrases(Collection<String> extraKeywords) {
        if (extraKeywords == null || extraKeywords.isEmpty()) {
            return CONTEXT_OVERFLOW_PHRASES;
        }
        List<String> merged = new ArrayList<>(CONTEXT_OVERFLOW_PHRASES);
        for (String keyword : extraKeywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            merged.add(normalize(keyword));
        }
        return merged;
    }

    private static List<String> iterExceptionTexts(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        List<String> texts = new ArrayList<>();
        collectExceptionTexts(value, depth, seen, texts);
        return texts;
    }

    private static void collectExceptionTexts(Object value, int depth, IdentityHashMap<Object, Boolean> seen,
                                              List<String> texts) {
        if (value == null || depth > MAX_DEPTH) {
            return;
        }
        if (value instanceof String text) {
            texts.add(text);
            return;
        }
        if (value instanceof byte[] bytes) {
            texts.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            return;
        }
        if (seen.containsKey(value)) {
            return;
        }
        seen.put(value, Boolean.TRUE);

        if (value instanceof Throwable throwable) {
            String throwableText = String.valueOf(throwable);
            if (!throwableText.isBlank()) {
                texts.add(throwableText);
            }
            String message = throwable.getMessage();
            if (message != null && !message.isBlank()) {
                texts.add(message);
            }
            if (throwable instanceof BaseError baseError && baseError.getDetails() != null) {
                collectExceptionTexts(baseError.getDetails(), depth + 1, seen, texts);
            }
            collectExceptionTexts(throwable.getCause(), depth + 1, seen, texts);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                collectExceptionTexts(entry.getKey(), depth + 1, seen, texts);
                collectExceptionTexts(entry.getValue(), depth + 1, seen, texts);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                collectExceptionTexts(nested, depth + 1, seen, texts);
            }
            return;
        }
        if (value instanceof Iterator<?> iterator) {
            while (iterator.hasNext()) {
                collectExceptionTexts(iterator.next(), depth + 1, seen, texts);
            }
            return;
        }
        for (String field : CONTEXT_OVERFLOW_FIELDS) {
            Object nested = readField(value, field);
            if (nested == null || nested == value) {
                continue;
            }
            collectExceptionTexts(nested, depth + 1, seen, texts);
        }
    }

    private static Object readField(Object value, String fieldName) {
        try {
            var field = value.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(value);
        } catch (ReflectiveOperationException | SecurityException | InaccessibleObjectException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
    }

    private static boolean containsAny(String message, Collection<String> phrases) {
        for (String phrase : phrases) {
            if (message.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
