/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code SharedExperience} in
 * {@code openjiuwen/agent_evolving/sharing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SharedExperience {

    private EvolutionRecord record;
    private List<String> keywords = new ArrayList<>();
    private String summary = "";
    private SharingMeta sharingMeta;

    public SharedExperience() {
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("record", record == null ? Map.of() : record.toDict());
        payload.put("keywords", new ArrayList<>(keywords));
        payload.put("summary", summary);
        payload.put("sharing_meta", sharingMeta == null ? null : sharingMeta.toDict());
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static SharedExperience fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        SharedExperience experience = new SharedExperience();
        Object recordData = resolved.get("record");
        Object sharingMetaData = resolved.get("sharing_meta");
        experience.record = EvolutionRecord.fromDict(recordData instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
        experience.keywords = stringList(resolved.get("keywords"));
        experience.summary = stringValue(resolved.get("summary"), "");
        experience.sharingMeta = sharingMetaData instanceof Map<?, ?> map ? SharingMeta.fromDict((Map<String, Object>) map) : null;
        return experience;
    }

    static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String resolved = String.valueOf(value);
        return resolved.isEmpty() ? fallback : resolved;
    }

    public EvolutionRecord getRecord() {
        return record;
    }

    public void setRecord(EvolutionRecord record) {
        this.record = record;
    }

    public List<String> getKeywords() {
        return new ArrayList<>(keywords);
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary != null ? summary : "";
    }

    public SharingMeta getSharingMeta() {
        return sharingMeta;
    }

    public void setSharingMeta(SharingMeta sharingMeta) {
        this.sharingMeta = sharingMeta;
    }
}
