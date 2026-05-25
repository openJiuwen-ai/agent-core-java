/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.Map;

/**
 * Translator interface for localization in agent team tools.
 * <p>
 * Mirrors Python's {@code Translator} in agent_teams tools.
 * Provides locale-aware string translation for tool descriptions and parameters.
 */
public interface Translator {

    /**
     * Get a translated string by key.
     *
     * @param key The translation key
     * @return The translated string, or the key itself if not found
     */
    String get(String key);

    /**
     * Get a translated string with default fallback.
     *
     * @param key The translation key
     * @param defaultValue Default value if key not found
     * @return The translated string, or defaultValue if not found
     */
    String get(String key, String defaultValue);

    /**
     * Get all translations as a map.
     *
     * @return Map of all translation keys to values
     */
    Map<String, String> getAll();

    /**
     * Get the locale code.
     *
     * @return Locale code (e.g., "en", "zh")
     */
    String getLocale();
}