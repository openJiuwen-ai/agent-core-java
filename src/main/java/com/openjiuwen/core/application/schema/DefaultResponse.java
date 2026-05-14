/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Default response configuration for agents.
 * <p>
 * Used when intent detection returns no matching workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultResponse {

    @Builder.Default
    @JsonProperty("type")
    @JsonAlias("type")
    private String type = "text";

    private String text;

    public static DefaultResponseBuilder builder() {
        return new DefaultResponseBuilder();
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public static final class DefaultResponseBuilder {
        private String type = "text";
        private String text;

        public DefaultResponseBuilder type(String type) { this.type = type; return this; }
        public DefaultResponseBuilder text(String text) { this.text = text; return this; }

        public DefaultResponse build() {
            DefaultResponse response = new DefaultResponse();
            response.setType(type);
            response.setText(text);
            return response;
        }
    }
}
