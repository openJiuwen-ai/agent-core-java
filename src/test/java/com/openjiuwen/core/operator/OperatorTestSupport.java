/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.operator;

import com.openjiuwen.core.session.AgentSessionApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared test helpers for operator module tests.
 */
public final class OperatorTestSupport {

    private OperatorTestSupport() {
    }

    public static final class TrackingSession implements AgentSessionApi {

        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<String> operatorHistory = new ArrayList<>();
        private String currentOperatorId;

        @Override
        public String getSessionId() {
            return "test-session";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            if (state != null) {
                this.state.putAll(state);
            }
        }

        public void setCurrentOperatorId(String operatorId) {
            this.currentOperatorId = operatorId;
            this.operatorHistory.add(operatorId);
        }

        public String getCurrentOperatorId() {
            return currentOperatorId;
        }

        public List<String> getOperatorHistory() {
            return operatorHistory;
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public java.util.Iterator<Object> streamIterator() {
            return java.util.List.of().iterator();
        }
    }
}
