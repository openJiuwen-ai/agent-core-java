/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Event emitted by a {@link ContextProcessor} describing what was modified.
 * <p>
 * Mirrors Python's {@code ContextEvent} from {@code processor/base.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEvent {

    private String eventType;

    @Builder.Default
    private List<Integer> messagesToModify = new ArrayList<>();

    public static ContextEventBuilder builder() {
        return new ContextEventBuilder();
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public List<Integer> getMessagesToModify() {
        return messagesToModify;
    }

    public void setMessagesToModify(List<Integer> messagesToModify) {
        this.messagesToModify = messagesToModify;
    }

    public static final class ContextEventBuilder {
        private String eventType;
        private List<Integer> messagesToModify = new ArrayList<>();

        public ContextEventBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public ContextEventBuilder messagesToModify(List<Integer> messagesToModify) {
            this.messagesToModify = messagesToModify;
            return this;
        }

        public ContextEvent build() {
            return new ContextEvent(eventType, messagesToModify);
        }
    }
}
