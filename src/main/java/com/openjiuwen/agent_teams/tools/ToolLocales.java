/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.tools.locales.CnLocaleStrings;
import com.openjiuwen.agent_teams.tools.locales.EnLocaleStrings;

import java.io.FileNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Locale helper for team tool descriptions and parameter labels.
 *
 * <p>Mirrors Python's {@code make_translator} in
 * {@code openjiuwen.agent_teams.tools.locales}.</p>
 */
public final class ToolLocales {

    private ToolLocales() {
    }

    public static Translator makeTranslator(String language) {
        String locale = "en".equals(language) ? "en" : "cn";
        Map<String, String> strings = "en".equals(locale) ? EnLocaleStrings.getAll() : CnLocaleStrings.getAll();
        return new MapTranslator(locale, strings);
    }

    public static String translate(String language, String toolName) throws FileNotFoundException {
        return ((MapTranslator) makeTranslator(language)).translate(toolName);
    }

    public static String translate(String language, String toolName, String key) throws FileNotFoundException {
        return ((MapTranslator) makeTranslator(language)).translate(toolName, key);
    }

    private static final class MapTranslator implements Translator {

        private final String locale;
        private final Map<String, String> strings;

        private MapTranslator(String locale, Map<String, String> strings) {
            this.locale = locale;
            this.strings = new LinkedHashMap<>(strings);
        }

        @Override
        public String get(String key) {
            return strings.getOrDefault(key, key);
        }

        @Override
        public String get(String key, String defaultValue) {
            return strings.getOrDefault(key, defaultValue);
        }

        @Override
        public Map<String, String> getAll() {
            return new LinkedHashMap<>(strings);
        }

        @Override
        public String getLocale() {
            return locale;
        }

        private String translate(String toolName) throws FileNotFoundException {
            String desc = strings.get(toolName + "._desc");
            if (desc != null) {
                return desc;
            }
            if (strings.keySet().stream().anyMatch(key -> key.startsWith(toolName + "."))) {
                return toolName + " tool description";
            }
            throw new FileNotFoundException("Missing description for tool '" + toolName + "' in locale '" + locale + "'");
        }

        private String translate(String toolName, String key) throws FileNotFoundException {
            String value = strings.get(toolName + "." + key);
            if (value != null) {
                return value;
            }
            throw new FileNotFoundException("Missing translation for tool '" + toolName
                    + "' key '" + key + "' in locale '" + locale + "'");
        }
    }
}
