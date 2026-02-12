// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.prompt.assemble.variables;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.LogEventType;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variable class for processing string-type placeholders.
 * <p>
 * TextableVariable parses a text template containing placeholders (e.g., "{{name}}") and supports
 * nested placeholder access (e.g., "{{user.name}}"). It can replace placeholders with actual values
 * during the update process.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/prompt/assemble/variables/textable.py</p>
 */
public class TextableVariable extends Variable {
    private static final LoggerProtocol promptLogger = LogManager.getLogger("prompt");

    private final String text;
    private final String prefix;
    private final String suffix;
    private final List<String> placeholders;

    /**
     * Constructs a TextableVariable with the specified text and placeholder delimiters.
     *
     * @param text   the template text containing placeholders
     * @param name   the name of this variable
     * @param prefix the left delimiter for placeholders (e.g., "{{")
     * @param suffix the right delimiter for placeholders (e.g., "}}")
     * @throws BaseError if a placeholder is empty or malformed
     */
    public TextableVariable(String text, String name, String prefix, String suffix) {
        super(name, null); // Will be set after parsing placeholders
        this.text = text;
        this.prefix = prefix;
        this.suffix = suffix;

        // Parse placeholders from text
        String regexPattern = Pattern.quote(prefix) + "([^{}]*?)" + Pattern.quote(suffix);
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(text);

        this.placeholders = new ArrayList<>();
        while (matcher.find()) {
            String placeholder = matcher.group(1).strip();
            if (placeholder.isEmpty()) {
                throw new BaseError(
                    StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                    "placeholders cannot be empty string",
                    null, null, null
                );
            }
            if (!placeholders.contains(placeholder)) {
                placeholders.add(placeholder);
            }
        }

        // Extract input keys from placeholders (get the first part before '.')
        List<String> inputKeys = new ArrayList<>();
        for (String placeholder : placeholders) {
            String inputKey = placeholder.split("\\.")[0];
            if (!inputKeys.contains(inputKey)) {
                inputKeys.add(inputKey);
            }
        }

        // Set input keys via reflection workaround (since we need to set it after construction)
        try {
            java.lang.reflect.Field field = Variable.class.getDeclaredField("inputKeys");
            field.setAccessible(true);
            field.set(this, inputKeys);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set inputKeys", e);
        }
    }

    /**
     * Gets the original text template.
     *
     * @return the text template
     */
    public String getText() {
        return text;
    }

    /**
     * Gets the list of placeholders found in the text.
     *
     * @return the list of placeholders
     */
    public List<String> getPlaceholders() {
        return Collections.unmodifiableList(placeholders);
    }

    /**
     * Replaces placeholders in the text with passed-in key-values and updates the value.
     * <p>
     * Supports nested placeholder access like "user.name" where it will navigate through
     * the object hierarchy to retrieve the value.
     * </p>
     *
     * @param kwargs the arguments passed in as key-value pairs for updating the variable
     * @throws BaseError if placeholder parsing fails
     */
    @Override
    public void update(Map<String, Object> kwargs) {
        String formattedText = text;
        
        for (String placeholder : placeholders) {
            Object value = kwargs;
            
            try {
                // Navigate through nested path (e.g., "user.name" -> kwargs["user"]["name"])
                for (String node : placeholder.split("\\.")) {
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) value;
                        value = map.get(node);
                    } else {
                        // Try to get field value via reflection
                        try {
                            java.lang.reflect.Field field = value.getClass().getDeclaredField(node);
                            field.setAccessible(true);
                            value = field.get(value);
                        } catch (NoSuchFieldException e) {
                            // Try getter method
                            String getterName = "get" + Character.toUpperCase(node.charAt(0)) + node.substring(1);
                            java.lang.reflect.Method method = value.getClass().getMethod(getterName);
                            value = method.invoke(value);
                        }
                    }
                }
            } catch (Exception e) {
                throw new BaseError(
                    StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                    "error parsing the placeholder `" + placeholder + "`",
                    null, e, null
                );
            }

            // Convert non-string values to string
            if (!(value instanceof String || value instanceof Integer || 
                  value instanceof Float || value instanceof Double || 
                  value instanceof Boolean)) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("placeholder", placeholder);
                promptLogger.info(
                    "Converting non-string value using str(). Please check if the style is describe.",
                    LogEventType.AGENT_START,
                    kwargs,
                    getValue(),
                    metadata
                );
            }

            // Replace placeholder in text
            String placeholderStr = prefix + placeholder + suffix;
            formattedText = formattedText.replace(placeholderStr, String.valueOf(value));
        }

        setValue(formattedText);
    }
}

