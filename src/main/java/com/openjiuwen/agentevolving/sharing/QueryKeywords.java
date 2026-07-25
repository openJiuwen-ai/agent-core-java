/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code QueryKeywords} in
 * {@code openjiuwen/agent_evolving/sharing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class QueryKeywords {

    private List<String> keywords = List.of();
    private String intent = "";
    private String rawExcerpt = "";

    public QueryKeywords() {
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keywords", keywords);
        payload.put("intent", intent);
        payload.put("raw_excerpt", rawExcerpt);
        return payload;
    }

    public static QueryKeywords fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        QueryKeywords queryKeywords = new QueryKeywords();
        queryKeywords.keywords = SharedExperience.stringList(resolved.get("keywords"));
        queryKeywords.intent = SharedExperience.stringValue(resolved.get("intent"), "");
        queryKeywords.rawExcerpt = SharedExperience.stringValue(resolved.get("raw_excerpt"), "");
        return queryKeywords;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent != null ? intent : "";
    }

    public String getRawExcerpt() {
        return rawExcerpt;
    }

    public void setRawExcerpt(String rawExcerpt) {
        this.rawExcerpt = rawExcerpt != null ? rawExcerpt : "";
    }
}
