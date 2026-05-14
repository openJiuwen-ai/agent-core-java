/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal RL online sample DTO extracted from one LLM trajectory step.
 * <p>
 * Mirrors the sample shape produced by Python's online rail converter.
 */
public class OnlineRlSample {

    private final List<Map<String, Object>> messages = new ArrayList<>();
    private final List<Integer> promptIds = new ArrayList<>();
    private final List<Integer> responseTokens = new ArrayList<>();
    private final List<Double> responseLogprobs = new ArrayList<>();
    private String responseText = "";

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public List<Integer> getPromptIds() {
        return promptIds;
    }

    public List<Integer> getResponseTokens() {
        return responseTokens;
    }

    public List<Double> getResponseLogprobs() {
        return responseLogprobs;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText != null ? responseText : "";
    }

    public Map<String, Object> toDict() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messages", new ArrayList<>(messages));
        out.put("prompt_ids", new ArrayList<>(promptIds));
        out.put("response_tokens", new ArrayList<>(responseTokens));
        out.put("response_logprobs", new ArrayList<>(responseLogprobs));
        out.put("response_text", responseText);
        return out;
    }
}
