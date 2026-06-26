/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor;

import com.openjiuwen.core.context_engine.context.SessionModelContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event emitted by a context processor describing what was modified.
 *
 * <p>Mirrors Python's {@code ContextEvent} in
 * {@code openjiuwen/core/context_engine/processor/base.py}.</p>
 */
public class ContextEvent implements SessionModelContext.ContextProcessorEventPort {
    private String eventType;
    private List<Integer> messagesToModify = new ArrayList<>();
    private String compactSummary = "";
    private Map<String, Object> compressionUsage;

    public ContextEvent() {
    }

    public ContextEvent(String eventType) {
        this.eventType = eventType;
    }

    public ContextEvent(String eventType, List<Integer> messagesToModify, String compactSummary,
                        Map<String, Object> compressionUsage) {
        this.eventType = eventType;
        this.messagesToModify = messagesToModify == null ? new ArrayList<>() : new ArrayList<>(messagesToModify);
        this.compactSummary = compactSummary == null ? "" : compactSummary;
        this.compressionUsage = compressionUsage == null ? null : new LinkedHashMap<>(compressionUsage);
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @Override
    public List<Integer> messagesToModify() {
        return new ArrayList<>(messagesToModify);
    }

    public List<Integer> getMessagesToModify() {
        return messagesToModify();
    }

    public void setMessagesToModify(List<Integer> messagesToModify) {
        this.messagesToModify = messagesToModify == null ? new ArrayList<>() : new ArrayList<>(messagesToModify);
    }

    @Override
    public String compactSummary() {
        return compactSummary;
    }

    public String getCompactSummary() {
        return compactSummary;
    }

    public void setCompactSummary(String compactSummary) {
        this.compactSummary = compactSummary == null ? "" : compactSummary;
    }

    @Override
    public Object compressionUsage() {
        return compressionUsage == null ? null : new LinkedHashMap<>(compressionUsage);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCompressionUsage() {
        Object usage = compressionUsage();
        return usage instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    public void setCompressionUsage(Map<String, Object> compressionUsage) {
        this.compressionUsage = compressionUsage == null ? null : new LinkedHashMap<>(compressionUsage);
    }
}
