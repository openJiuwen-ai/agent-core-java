/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter utilities for DL transformer.
 * <p>
 * Mirrors Python's {@code ConverterUtils} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converter_utils}.
 */
public final class ConverterUtils {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static long counter = 0;

    private ConverterUtils() {
    }

    public static synchronized String generateNodeId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String[] extractVariable(String input) {
        Matcher matcher = VAR_PATTERN.matcher(input);
        if (!matcher.matches()) return null;
        String inner = matcher.group(1);
        int dotIdx = inner.indexOf('.');
        if (dotIdx < 0) return null;
        return new String[]{inner.substring(0, dotIdx), inner.substring(dotIdx + 1)};
    }

    public static Map<String, Object> convertRefVariable(String input) {
        Matcher matcher = VAR_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid reference variable: " + input);
        }
        String inner = matcher.group(1);
        String[] parts = inner.split("\\.|_of_");
        List<String> content = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) content.add(part);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "ref");
        result.put("content", content);
        return result;
    }
}
