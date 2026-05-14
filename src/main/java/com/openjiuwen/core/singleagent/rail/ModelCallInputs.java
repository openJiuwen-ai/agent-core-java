/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Input data for BEFORE/AFTER_MODEL_CALL events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallInputs implements EventInputs {
    @Builder.Default
    private List<Object> messages = new ArrayList<>();
    private List<ToolInfo> tools;
    private Object response;

    public static ModelCallInputsBuilder builder() {
        return new ModelCallInputsBuilder();
    }

    public List<Object> getMessages() {
        return messages;
    }

    public void setMessages(List<Object> messages) {
        this.messages = messages;
    }

    public List<ToolInfo> getTools() {
        return tools;
    }

    public void setTools(List<ToolInfo> tools) {
        this.tools = tools;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    public static final class ModelCallInputsBuilder {
        private List<Object> messages = new ArrayList<>();
        private List<ToolInfo> tools;
        private Object response;

        public ModelCallInputsBuilder messages(List<Object> messages) {
            this.messages = messages;
            return this;
        }

        public ModelCallInputsBuilder tools(List<ToolInfo> tools) {
            this.tools = tools;
            return this;
        }

        public ModelCallInputsBuilder response(Object response) {
            this.response = response;
            return this;
        }

        public ModelCallInputs build() {
            return new ModelCallInputs(messages, tools, response);
        }
    }
}
