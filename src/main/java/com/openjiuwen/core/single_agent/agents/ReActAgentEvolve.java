/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.agents;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.tool_call.ToolCallOperator;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct agent variant for self-evolution training.
 *
 * <p>Mirrors Python's {@code ReActAgentEvolve} in
 * {@code openjiuwen/core/single_agent/agents/react_agent_evolve.py}.</p>
 */
public class ReActAgentEvolve extends ReActAgent {
    private LLMCallOperator llmOp;
    private ToolCallOperator toolOp;

    public ReActAgentEvolve(AgentCard card) {
        super(card);
        initOperators();
    }

    public final void initOperators() {
        llmOp = new LLMCallOperator(
                getConfig().getPromptTemplate(),
                "{{query}}",
                false,
                true,
                "react_llm",
                this::onLlmParameterUpdated
        );
        toolOp = new ToolCallOperator(
                "react_tool",
                extractToolDescriptions(),
                this::onToolParameterUpdated
        );
    }

    public void _init_operators() {
        initOperators();
    }

    public Map<String, Operator> getOperators() {
        Map<String, Operator> operators = new LinkedHashMap<>();
        if (toolOp != null) {
            operators.put(toolOp.getOperatorId(), toolOp);
        }
        if (llmOp != null) {
            operators.put(llmOp.getOperatorId(), llmOp);
        }
        return operators;
    }

    public Map<String, Operator> get_operators() {
        return getOperators();
    }

    public LLMCallOperator getLlmOperator() {
        return llmOp;
    }

    public ToolCallOperator getToolOperator() {
        return toolOp;
    }

    void setLlmOperatorForTest(LLMCallOperator llmOp) {
        this.llmOp = llmOp;
    }

    void setToolOperatorForTest(ToolCallOperator toolOp) {
        this.toolOp = toolOp;
    }

    public void onLlmParameterUpdated(String target, Object value) {
        if (!"system_prompt".equals(target)) {
            return;
        }
        if (value instanceof List<?> list) {
            getConfig().setPromptTemplate(toPromptTemplate(list));
        } else if (value instanceof String text) {
            getConfig().setPromptTemplate(List.of(Map.of("role", "system", "content", text)));
        }
    }

    public void _on_llm_parameter_updated(String target, Object value) {
        onLlmParameterUpdated(target, value);
    }

    public Map<String, String> extractToolDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (ToolInfo toolInfo : getAbilityManager().listToolInfo()) {
            if (toolInfo.getName() != null && !toolInfo.getName().isEmpty()) {
                descriptions.put(toolInfo.getName(), toolInfo.getDescription());
            }
        }
        return descriptions;
    }

    public Map<String, String> _extract_tool_descriptions() {
        return extractToolDescriptions();
    }

    public void onToolParameterUpdated(String target, Object value) {
        if (!"tool_description".equals(target) || !(value instanceof Map<?, ?> descriptions)) {
            return;
        }
        for (Map.Entry<?, ?> entry : descriptions.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Object ability = getAbilityManager().get(String.valueOf(entry.getKey())).orElse(null);
            if (ability instanceof BaseCard card) {
                card.setDescription(entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
        }
    }

    public void _on_tool_parameter_updated(String target, Object value) {
        onToolParameterUpdated(target, value);
    }

    private static List<Map<String, Object>> toPromptTemplate(List<?> value) {
        return value.stream()
                .map(ReActAgentEvolve::toPromptMessage)
                .toList();
    }

    private static Map<String, Object> toPromptMessage(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            return Map.of("role", "system", "content", String.valueOf(item));
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
        return normalized;
    }
}
