/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.locales;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight i18n facade for agent team tool descriptions.
 *
 * <p>Mirrors Python's {@code make_translator} in
 * {@code openjiuwen/agent_teams/tools/locales/__init__.py}.</p>
 */
public final class TeamToolLocales {

    private static final String DEFAULT_LANGUAGE = "cn";
    private static final String DEFAULT_DESCRIPTION_KEY = "_desc";
    private static final String DESCRIPTION_RESOURCE_ROOT = "openjiuwen/agent_teams/tools/locales/descs";
    private static final Pattern FORMAT_TOKEN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}");
    private static final ConcurrentMap<DescriptionKey, Optional<PromptTemplate>> DESCRIPTION_CACHE =
            new ConcurrentHashMap<>();

    private TeamToolLocales() {
    }

    public static Translator makeTranslator() {
        return makeTranslator(DEFAULT_LANGUAGE);
    }

    public static Translator makeTranslator(String language) {
        String effectiveLanguage = language == null ? DEFAULT_LANGUAGE : language;
        Map<String, String> strings = "en".equals(effectiveLanguage)
                ? EnLocaleStrings.getAll()
                : CnLocaleStrings.getAll();
        return new LocaleTranslator(effectiveLanguage, strings);
    }

    public interface Translator {
        default String translate(String tool) {
            return translate(tool, DEFAULT_DESCRIPTION_KEY, Map.of());
        }

        default String translate(String tool, String key) {
            return translate(tool, key, Map.of());
        }

        String translate(String tool, String key, Map<String, ?> keywords);
    }

    private record LocaleTranslator(String language, Map<String, String> strings) implements Translator {
        @Override
        public String translate(String tool, String key, Map<String, ?> keywords) {
            String effectiveKey = key == null ? DEFAULT_DESCRIPTION_KEY : key;
            Map<String, ?> effectiveKeywords = keywords == null ? Map.of() : keywords;
            if (DEFAULT_DESCRIPTION_KEY.equals(effectiveKey)) {
                Optional<PromptTemplate> template = loadDescription(tool, language);
                if (template.isPresent()) {
                    Object content = effectiveKeywords.isEmpty()
                            ? template.get().getContent()
                            : template.get().format(toObjectMap(effectiveKeywords)).getContent();
                    return String.valueOf(content);
                }
                String dictionaryKey = tool + "." + DEFAULT_DESCRIPTION_KEY;
                if (!strings.containsKey(dictionaryKey)) {
                    throw missingDescription(tool, language, dictionaryKey);
                }
            }
            String dictionaryKey = tool + "." + effectiveKey;
            String raw = strings.get(dictionaryKey);
            if (raw == null) {
                throw new NoSuchElementException(dictionaryKey);
            }
            return effectiveKeywords.isEmpty() ? raw : formatMap(raw, effectiveKeywords);
        }
    }

    private static Optional<PromptTemplate> loadDescription(String tool, String language) {
        DescriptionKey key = new DescriptionKey(tool, language);
        return DESCRIPTION_CACHE.computeIfAbsent(key, ignored -> readDescription(tool, language));
    }

    private static Optional<PromptTemplate> readDescription(String tool, String language) {
        String resourceName = DESCRIPTION_RESOURCE_ROOT + "/" + language + "/" + tool + ".md";
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = classLoader == null
                ? TeamToolLocales.class.getClassLoader().getResourceAsStream(resourceName)
                : classLoader.getResourceAsStream(resourceName);
        if (stream == null) {
            return Optional.empty();
        }
        try (InputStream input = stream) {
            String content = readUtf8(input).strip();
            return Optional.of(PromptTemplate.builder()
                    .name(tool + "." + DEFAULT_DESCRIPTION_KEY)
                    .content(content)
                    .build());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load locale description: " + resourceName, exception);
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> toObjectMap(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach(result::put);
        return result;
    }

    private static String formatMap(String raw, Map<String, ?> keywords) {
        Matcher matcher = FORMAT_TOKEN.matcher(raw);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!keywords.containsKey(token)) {
                throw new NoSuchElementException(token);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(keywords.get(token))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static UncheckedIOException missingDescription(String tool, String language, String dictionaryKey) {
        String expectedPath = DESCRIPTION_RESOURCE_ROOT + "/" + language + "/" + tool + ".md";
        FileNotFoundException exception = new FileNotFoundException(
                "Missing description for tool '" + tool + "' in language '" + language + "': expected Markdown at "
                        + expectedPath + " or STRINGS['" + dictionaryKey + "']"
        );
        return new UncheckedIOException(exception);
    }

    private record DescriptionKey(String tool, String language) {
    }
}
