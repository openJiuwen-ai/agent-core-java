/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.prompt.assemble;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.assemble.variables.DictableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.Variable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembler that substitutes placeholders in a prompt template.
 * <p>
 * Supports both String and {@code List<BaseMessage>} content.
 * <p>
 * Mirrors Python's {@code PromptAssembler}.
 */
public class PromptAssembler {

    private Object templateContent;  // String or List<BaseMessage>
    private final String placeholderPrefix;
    private final String placeholderSuffix;
    private final List<Variable> templateFormatters;
    private final Map<String, Variable> variables;

    /**
     * Construct a PromptAssembler.
     *
     * @param promptTemplateContent the template content (String or List of BaseMessage)
     * @param placeholderPrefix     prefix delimiter for placeholders
     * @param placeholderSuffix     suffix delimiter for placeholders
     * @param initialVariables      optional pre-configured variables
     */
    public PromptAssembler(Object promptTemplateContent,
                           String placeholderPrefix,
                           String placeholderSuffix,
                           Map<String, Variable> initialVariables) {
        this.templateContent = promptTemplateContent;
        this.placeholderPrefix = placeholderPrefix;
        this.placeholderSuffix = placeholderSuffix;
        this.templateFormatters = buildFormatterList();
        this.variables = buildVariablesWithVerify(
                initialVariables != null ? initialVariables : Map.of());
    }

    public PromptAssembler(Object promptTemplateContent,
                           String placeholderPrefix,
                           String placeholderSuffix) {
        this(promptTemplateContent, placeholderPrefix, placeholderSuffix, null);
    }

    /**
     * Get all input keys needed for the template.
     */
    public List<String> getInputKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Variable v : variables.values()) {
            keys.addAll(v.getInputKeys());
        }
        return new ArrayList<>(keys);
    }

    /**
     * Assemble the prompt by substituting placeholders with the given keyword arguments.
     *
     * @param kwargs key-value pairs for substitution
     * @return assembled content (String or List of BaseMessage)
     */
    @SuppressWarnings("unchecked")
    public Object promptAssemble(Map<String, Object> kwargs) {
        List<String> inputKeys = getInputKeys();
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (var entry : kwargs.entrySet()) {
            if (entry.getValue() != null && inputKeys.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        // Fill missing keys with the placeholder string
        Map<String, Object> allKwargs = new LinkedHashMap<>();
        for (String k : inputKeys) {
            if (!filtered.containsKey(k)) {
                allKwargs.put(k, placeholderPrefix + k + placeholderSuffix);
            }
        }
        allKwargs.putAll(filtered);

        doUpdate(allKwargs);
        return doFormat();
    }

    // ==================== Internal ====================

    @SuppressWarnings("unchecked")
    private List<Variable> buildFormatterList() {
        List<Variable> list = new ArrayList<>();
        if (templateContent instanceof String text) {
            list.add(new TextableVariable(text, "__inner__", placeholderPrefix, placeholderSuffix));
        } else if (templateContent instanceof List<?> messages) {
            for (Object msg : messages) {
                if (msg instanceof BaseMessage bm) {
                    Object content = bm.getContent();
                    if (content instanceof String text) {
                        list.add(new TextableVariable(text, "__inner__", placeholderPrefix, placeholderSuffix));
                    } else if (content instanceof List<?> contentList && !contentList.isEmpty()
                            && contentList.getFirst() instanceof Map) {
                        list.add(new DictableVariable(content, "__inner__", placeholderPrefix, placeholderSuffix));
                    } else {
                        list.add(null);
                    }
                }
            }
        }
        return list;
    }

    private Map<String, Variable> buildVariablesWithVerify(Map<String, Variable> inputVariables) {
        // Collect all input keys from formatters
        Set<String> allKeys = new LinkedHashSet<>();
        for (Variable formatter : templateFormatters) {
            if (formatter != null) {
                allKeys.addAll(formatter.getInputKeys());
            }
        }

        Map<String, Variable> result = new LinkedHashMap<>(inputVariables);

        // Verify provided variables
        for (var entry : inputVariables.entrySet()) {
            if (!allKeys.contains(entry.getKey())) {
                throw ErrorHelper.buildError(StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                        "error_msg", "variable " + entry.getKey() + " is not defined in the promptTemplate");
            }
            if (!(entry.getValue() instanceof Variable)) {
                throw ErrorHelper.buildError(StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                        "error_msg", "variable " + entry.getKey() + " must be instantiated as a Variable object");
            }
        }

        // Create default TextableVariable for placeholders not yet provided
        for (String placeholder : allKeys) {
            if (result.containsKey(placeholder)) {
                result.get(placeholder).setName(placeholder);
            } else {
                String placeholderStr = placeholderPrefix + placeholder + placeholderSuffix;
                result.put(placeholder, new TextableVariable(
                        placeholderStr, placeholder, placeholderPrefix, placeholderSuffix));
            }
        }
        return result;
    }

    private void doUpdate(Map<String, Object> kwargs) {
        List<String> inputKeys = getInputKeys();
        Set<String> missing = new LinkedHashSet<>(inputKeys);
        missing.removeAll(kwargs.keySet());
        if (!missing.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.PROMPT_ASSEMBLER_TEMPLATE_PARAM_ERROR,
                    "error_msg", "missing keys for updating the prompt assembler: " + missing);
        }
        Set<String> unexpected = new LinkedHashSet<>(kwargs.keySet());
        unexpected.removeAll(new LinkedHashSet<>(inputKeys));
        if (!unexpected.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.PROMPT_ASSEMBLER_TEMPLATE_PARAM_ERROR,
                    "error_msg", "unexpected keys for updating the prompt assembler: " + unexpected);
        }
        for (Variable variable : variables.values()) {
            Map<String, Object> inputKwargs = new LinkedHashMap<>();
            for (var entry : kwargs.entrySet()) {
                if (variable.getInputKeys().contains(entry.getKey())) {
                    inputKwargs.put(entry.getKey(), entry.getValue());
                }
            }
            variable.eval(inputKwargs);
        }
    }

    @SuppressWarnings("unchecked")
    private Object doFormat() {
        Map<String, Object> formatKwargs = new LinkedHashMap<>();
        for (Variable v : variables.values()) {
            formatKwargs.put(v.getName(), v.getValue());
        }

        for (int idx = 0; idx < templateFormatters.size(); idx++) {
            Variable formatter = templateFormatters.get(idx);
            if (formatter == null) {
                continue;
            }
            Object formattedPrompt = formatter.eval(formatKwargs);
            if (templateContent instanceof String) {
                templateContent = formattedPrompt;
                break;
            } else if (templateContent instanceof List<?> messages) {
                ((BaseMessage) messages.get(idx)).setContent(formattedPrompt);
            }
        }
        return templateContent;
    }
}
