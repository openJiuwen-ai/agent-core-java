/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Data for BEFORE/AFTER_INVOKE events.
 *
 * <p>Before: query + conversationId filled.
 * After: result also filled.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeInputs implements EventInputs {
    private String query;
    private String conversationId;
    private Map<String, Object> result;

    public InvokeInputs() {
    }

    public InvokeInputs(String query, String conversationId, Map<String, Object> result) {
        this.query = query;
        this.conversationId = conversationId;
        this.result = result;
    }

    public static InvokeInputsBuilder builder() {
        return new InvokeInputsBuilder();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public static final class InvokeInputsBuilder {
        private String query;
        private String conversationId;
        private Map<String, Object> result;

        public InvokeInputsBuilder query(String query) {
            this.query = query;
            return this;
        }

        public InvokeInputsBuilder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public InvokeInputsBuilder result(Map<String, Object> result) {
            this.result = result;
            return this;
        }

        public InvokeInputs build() {
            return new InvokeInputs(query, conversationId, result);
        }
    }
}
