// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.prompt.assemble;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.Variable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for creating prompt based on a given prompt template.
 * <p>
 * PromptAssembler manages variables and their replacement in either string or message list templates.
 * It supports custom placeholder delimiters and validates that all required variables are provided.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/prompt/assemble/assembler.py</p>
 */
public class PromptAssembler {
    private Object templateContent; // Union[List[BaseMessage], str]
    private final String placeholderPrefix;
    private final String placeholderSuffix;
    private final List<Variable> templateFormatter;
    private final Map<String, Variable> variables;

    /**
     * Constructs a PromptAssembler with the specified template content and variables.
     *
     * @param promptTemplateContent the template content (String or List&lt;BaseMessage&gt;)
     * @param placeholderPrefix     the left delimiter for placeholders (default "{{")
     * @param placeholderSuffix     the right delimiter for placeholders (default "}}")
     * @param variables             map of variable name to Variable instance
     */
    public PromptAssembler(Object promptTemplateContent,
                          String placeholderPrefix,
                          String placeholderSuffix,
                          Map<String, Variable> variables) {
        this.templateContent = promptTemplateContent;
        this.placeholderPrefix = placeholderPrefix != null ? placeholderPrefix : "{{";
        this.placeholderSuffix = placeholderSuffix != null ? placeholderSuffix : "}}";
        this.templateFormatter = getFormatterList();
        this.variables = getVariablesWithVerify(variables != null ? variables : new HashMap<>());
    }

    /**
     * Gets the list of argument names for updating all the variables.
     *
     * @return the list of input keys required by all variables
     */
    public List<String> getInputKeys() {
        Set<String> keys = new HashSet<>();
        for (Variable variable : variables.values()) {
            if (variable.getInputKeys() != null) {
                keys.addAll(variable.getInputKeys());
            }
        }
        return new ArrayList<>(keys);
    }

    /**
     * Gets the prompt template content formatter.
     *
     * @return the list of Variable formatters for the template
     */
    private List<Variable> getFormatterList() {
        List<Variable> templateFormatterList = new ArrayList<>();
        
        if (templateContent instanceof String) {
            templateFormatterList.add(
                new TextableVariable(
                    (String) templateContent,
                    "__inner__",
                    placeholderPrefix,
                    placeholderSuffix
                )
            );
            return templateFormatterList;
        } else if (templateContent instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> contentList = (List<Object>) templateContent;
            
            for (Object msg : contentList) {
                // Process BaseMessage type
                if (msg instanceof BaseMessage) {
                    BaseMessage baseMsg = (BaseMessage) msg;
                    if (!(baseMsg.getContent() instanceof String)) {
                        templateFormatterList.add(null);
                        continue;
                    }
                    templateFormatterList.add(
                        new TextableVariable(
                            (String) baseMsg.getContent(),
                            "__inner__",
                            placeholderPrefix,
                            placeholderSuffix
                        )
                    );
                }
            }
        }
        
        return templateFormatterList;
    }

    /**
     * Verifies input variables and summarizes with prompt template content variables.
     *
     * @param variables the map of variable name to Variable instance
     * @return the verified and complete map of variables
     * @throws BaseError if a variable is not defined in the template or is not a Variable instance
     */
    private Map<String, Variable> getVariablesWithVerify(Map<String, Variable> variables) {
        // Collect all input keys from template formatters
        List<String> inputKeys = new ArrayList<>();
        for (Variable formatter : templateFormatter) {
            if (formatter != null && formatter.getInputKeys() != null) {
                inputKeys.addAll(formatter.getInputKeys());
            }
        }
        inputKeys = inputKeys.stream().distinct().collect(Collectors.toList());

        // Verify provided variables
        for (Map.Entry<String, Variable> entry : variables.entrySet()) {
            String name = entry.getKey();
            Variable variable = entry.getValue();
            
            if (!inputKeys.contains(name)) {
                throw new BaseError(
                    StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                    "variable " + name + " is not defined in the promptTemplate",
                    null, null, null
                );
            }
            if (!(variable instanceof Variable)) {
                throw new BaseError(
                    StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                    "variable " + name + " must be instantiated as a `variable` object",
                    null, null, null
                );
            }
        }

        // Create default TextableVariable for missing placeholders
        Map<String, Variable> completeVariables = new HashMap<>(variables);
        for (String placeholder : inputKeys) {
            if (completeVariables.containsKey(placeholder)) {
                completeVariables.get(placeholder).setName(placeholder);
            } else {
                String placeholderStr = placeholderPrefix + placeholder + placeholderSuffix;
                completeVariables.put(placeholder, 
                    new TextableVariable(
                        placeholderStr,
                        placeholder,
                        placeholderPrefix,
                        placeholderSuffix
                    )
                );
            }
        }

        return completeVariables;
    }

    /**
     * Updates the variables and formats the prompt template into a string-type or message-type prompt.
     *
     * @param kwargs the key-value pairs for updating variables
     * @return the formatted prompt (String or List&lt;BaseMessage&gt;)
     */
    public Object promptAssemble(Map<String, Object> kwargs) {
        List<String> inputKeys = getInputKeys();
        
        // Filter kwargs to only include valid input keys with non-null values
        Map<String, Object> filteredKwargs = kwargs.entrySet().stream()
            .filter(e -> e.getValue() != null && inputKeys.contains(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Add placeholders for missing keys
        Map<String, Object> allKwargs = new HashMap<>();
        for (String k : inputKeys) {
            if (!filteredKwargs.containsKey(k)) {
                allKwargs.put(k, placeholderPrefix + k + placeholderSuffix);
            }
        }
        allKwargs.putAll(filteredKwargs);

        update(allKwargs);
        return format();
    }

    /**
     * Updates the variables based on the arguments passed in as key-value pairs.
     *
     * @param kwargs the key-value pairs for updating variables
     * @throws BaseError if there are missing or unexpected keys
     */
    private void update(Map<String, Object> kwargs) {
        List<String> inputKeys = getInputKeys();
        Set<String> kwargKeys = kwargs.keySet();

        // Check for missing keys
        Set<String> missingKeys = new HashSet<>(inputKeys);
        missingKeys.removeAll(kwargKeys);
        if (!missingKeys.isEmpty()) {
            throw new BaseError(
                StatusCode.PROMPT_ASSEMBLER_TEMPLATE_PARAM_ERROR,
                "missing keys for updating the prompt assembler: " + new ArrayList<>(missingKeys),
                null, null, null
            );
        }

        // Check for unexpected keys
        Set<String> unexpectedKeys = new HashSet<>(kwargKeys);
        unexpectedKeys.removeAll(inputKeys);
        if (!unexpectedKeys.isEmpty()) {
            throw new BaseError(
                StatusCode.PROMPT_ASSEMBLER_TEMPLATE_PARAM_ERROR,
                "unexpected keys for updating the prompt assembler: " + new ArrayList<>(unexpectedKeys),
                null, null, null
            );
        }

        // Update each variable
        for (Variable variable : variables.values()) {
            if (variable.getInputKeys() == null) {
                continue;
            }
            Map<String, Object> inputKwargs = kwargs.entrySet().stream()
                .filter(e -> variable.getInputKeys().contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            variable.eval(inputKwargs);
        }
    }

    /**
     * Substitutes placeholders in the prompt template with variables values and gets formatted prompt.
     *
     * @return the formatted prompt (String or List&lt;BaseMessage&gt;)
     */
    private Object format() {
        // Create format kwargs from variables
        Map<String, Object> formatKwargs = new HashMap<>();
        for (Variable var : variables.values()) {
            formatKwargs.put(var.getName(), var.getValue());
        }

        // Apply formatting to each formatter
        for (int idx = 0; idx < templateFormatter.size(); idx++) {
            Variable formatter = templateFormatter.get(idx);
            if (formatter == null) {
                continue;
            }

            String formattedPrompt = formatter.eval(formatKwargs);

            if (templateContent instanceof String) {
                templateContent = formattedPrompt;
                break;
            } else if (templateContent instanceof List) {
                @SuppressWarnings("unchecked")
                List<BaseMessage> messageList = (List<BaseMessage>) templateContent;
                messageList.get(idx).setContent(formattedPrompt);
            }
        }

        return templateContent;
    }
}

