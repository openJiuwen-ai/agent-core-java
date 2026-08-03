/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt.assemble;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.assemble.variables.DictableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.Variable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles prompt template content by replacing declared placeholders.
 *
 * <p>Mirrors Python's {@code PromptAssembler} in
 * {@code openjiuwen/core/foundation/prompt/assemble/assembler.py}.</p>
 */
public class PromptAssembler {

    private Object templateContent;
    private final String placeholderPrefix;
    private final String placeholderSuffix;
    private final List<Variable> templateFormatters;
    private final Map<String, Variable> variables;

    public PromptAssembler(Object promptTemplateContent, String placeholderPrefix, String placeholderSuffix) {
        this(promptTemplateContent, placeholderPrefix, placeholderSuffix, null);
    }

    public PromptAssembler(Object promptTemplateContent, String placeholderPrefix, String placeholderSuffix,
            Map<String, Variable> initialVariables) {
        this.templateContent = promptTemplateContent;
        this.placeholderPrefix = placeholderPrefix == null ? "{{" : placeholderPrefix;
        this.placeholderSuffix = placeholderSuffix == null ? "}}" : placeholderSuffix;
        this.templateFormatters = buildFormatterList();
        this.variables = buildVariablesWithVerify(initialVariables == null ? Map.of() : initialVariables);
    }

    /**
     * Python-compatible {@code input_keys} property.
     *
     * @return unique placeholder root names in first-seen order
     */
    public List<String> getInputKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Variable variable : variables.values()) {
            keys.addAll(variable.getInputKeys());
        }
        return new ArrayList<>(keys);
    }

    /**
     * Assemble with keyword arguments, preserving unfilled placeholders.
     *
     * @param kwargs keyword substitutions
     * @return formatted String or list of BaseMessage values
     */
    public Object promptAssemble(Map<String, Object> kwargs) {
        List<String> inputKeys = getInputKeys();
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (kwargs != null) {
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                if (entry.getValue() != null && inputKeys.contains(entry.getKey())) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
        }

        Map<String, Object> allKwargs = new LinkedHashMap<>();
        for (String key : inputKeys) {
            if (!filtered.containsKey(key)) {
                allKwargs.put(key, placeholderPrefix + key + placeholderSuffix);
            }
        }
        allKwargs.putAll(filtered);

        doUpdate(allKwargs);
        return doFormat();
    }

    private List<Variable> buildFormatterList() {
        List<Variable> formatters = new ArrayList<>();
        if (templateContent instanceof String text) {
            formatters.add(new TextableVariable(text, "__inner__", placeholderPrefix, placeholderSuffix));
            return formatters;
        }
        if (!(templateContent instanceof List<?> messages)) {
            return formatters;
        }
        for (Object message : messages) {
            if (!(message instanceof BaseMessage baseMessage)) {
                continue;
            }
            Object content = baseMessage.getContent();
            if (content instanceof String text) {
                formatters.add(new TextableVariable(text, "__inner__", placeholderPrefix, placeholderSuffix));
            } else if (content instanceof List<?> contentList
                    && !contentList.isEmpty()
                    && contentList.get(0) instanceof Map) {
                formatters.add(new DictableVariable(content, "__inner__", placeholderPrefix, placeholderSuffix));
            } else {
                formatters.add(null);
            }
        }
        return formatters;
    }

    private Map<String, Variable> buildVariablesWithVerify(Map<String, Variable> inputVariables) {
        Set<String> inputKeys = new LinkedHashSet<>();
        for (Variable formatter : templateFormatters) {
            if (formatter != null) {
                inputKeys.addAll(formatter.getInputKeys());
            }
        }

        Map<String, Variable> result = new LinkedHashMap<>(inputVariables);
        for (Map.Entry<String, Variable> entry : inputVariables.entrySet()) {
            if (!inputKeys.contains(entry.getKey())) {
                throw ErrorHelper.buildError(
                        StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                        "error_msg",
                        "variable " + entry.getKey() + " is not defined in the promptTemplate"
                );
            }
            if (entry.getValue() == null) {
                throw ErrorHelper.buildError(
                        StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                        "error_msg",
                        "variable " + entry.getKey() + " must be instantiated as a `variable` object"
                );
            }
        }

        for (String placeholder : inputKeys) {
            if (result.containsKey(placeholder)) {
                renameVariable(result.get(placeholder), placeholder);
                continue;
            }
            String placeholderText = placeholderPrefix + placeholder + placeholderSuffix;
            result.put(placeholder, new TextableVariable(
                    placeholderText, placeholder, placeholderPrefix, placeholderSuffix));
        }
        return result;
    }

    private void doUpdate(Map<String, Object> kwargs) {
        List<String> inputKeys = getInputKeys();
        Set<String> missingKeys = new LinkedHashSet<>(inputKeys);
        missingKeys.removeAll(kwargs.keySet());
        if (!missingKeys.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.PROMPT_ASSEMBLER_TEMPLATE_PARAM_ERROR,
                    "error_msg",
                    "missing keys for updating the prompt assembler: " + missingKeys
            );
        }
        Set<String> unexpectedKeys = new LinkedHashSet<>(kwargs.keySet());
        unexpectedKeys.removeAll(new LinkedHashSet<>(inputKeys));
        if (!unexpectedKeys.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.PROMPT_ASSEMBLER_TEMPLATE_PARAM_ERROR,
                    "error_msg",
                    "unexpected keys for updating the prompt assembler: " + unexpectedKeys
            );
        }

        for (Variable variable : variables.values()) {
            Map<String, Object> inputKwargs = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                if (variable.getInputKeys().contains(entry.getKey())) {
                    inputKwargs.put(entry.getKey(), entry.getValue());
                }
            }
            variable.eval(inputKwargs);
        }
    }

    private Object doFormat() {
        Map<String, Object> formatKwargs = new LinkedHashMap<>();
        for (Variable variable : variables.values()) {
            formatKwargs.put(variable.getName(), variable.getValue());
        }

        for (int index = 0; index < templateFormatters.size(); index++) {
            Variable formatter = templateFormatters.get(index);
            if (formatter == null) {
                continue;
            }
            Object formattedPrompt = formatter.eval(formatKwargs);
            if (templateContent instanceof String) {
                templateContent = formattedPrompt;
                break;
            }
            if (templateContent instanceof List<?> messages && messages.get(index) instanceof BaseMessage baseMessage) {
                baseMessage.setContent(formattedPrompt);
            }
        }
        return templateContent;
    }

    private void renameVariable(Variable variable, String name) {
        try {
            Field field = Variable.class.getDeclaredField("name");
            field.setAccessible(true);
            field.set(variable, name);
        } catch (ReflectiveOperationException error) {
            throw ErrorHelper.buildError(
                    StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                    "error_msg",
                    "failed to rename prompt assembler variable `" + name + "`"
            );
        }
    }
}
