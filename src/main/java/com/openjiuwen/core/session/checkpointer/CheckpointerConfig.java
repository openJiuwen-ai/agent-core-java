/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.security.UrlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Configuration for creating a checkpointer via {@link CheckpointerFactory}.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.checkpointer.CheckpointerConfig}.
 */
public class CheckpointerConfig {

    private static final Pattern URL_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");

    private String type;
    private Map<String, Object> conf;

    public CheckpointerConfig() {
        this.type = "in_memory";
        this.conf = new HashMap<>();
    }

    public CheckpointerConfig(String type, Map<String, Object> conf) {
        this.type = type != null ? type : "in_memory";
        this.conf = conf != null ? conf : new HashMap<>();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getConf() {
        return conf;
    }

    public void setConf(Map<String, Object> conf) {
        this.conf = conf;
    }

    @Override
    public String toString() {
        Map<String, Object> redactedConf = redactUrlsInMap(conf);
        return "CheckpointerConfig(type=" + type + ", conf=" + redactedConf + ")";
    }

    public String toSimpleString() {
        Map<String, Object> redactedConf = redactUrlsInMap(conf);
        return "type=" + type + " conf=" + redactedConf;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> redactUrlsInMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), redactUrlsInValue(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object redactUrlsInValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String str = (String) value;
            if (URL_PATTERN.matcher(str).find()) {
                return UrlUtils.redactUrlPassword(str);
            }
            return str;
        }
        if (value instanceof Map) {
            return redactUrlsInMap((Map<String, Object>) value);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            java.util.List<Object> result = new java.util.ArrayList<>();
            for (Object item : list) {
                result.add(redactUrlsInValue(item));
            }
            return result;
        }
        return value;
    }
}
