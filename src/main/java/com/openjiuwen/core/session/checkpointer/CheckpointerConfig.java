/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Checkpointer configuration value object.
 *
 * <p>Mirrors Python's {@code CheckpointerConfig} in
 * {@code openjiuwen/core/session/checkpointer/checkpointer.py}.</p>
 */
public class CheckpointerConfig {

    private String type = "in_memory";
    private Map<String, Object> conf = new LinkedHashMap<>();

    public CheckpointerConfig() {
    }

    public CheckpointerConfig(String type, Map<String, Object> conf) {
        if (type != null) {
            this.type = type;
        }
        if (conf != null) {
            this.conf = new LinkedHashMap<>(conf);
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type == null ? "in_memory" : type;
    }

    public Map<String, Object> getConf() {
        return new LinkedHashMap<>(conf);
    }

    public void setConf(Map<String, Object> conf) {
        this.conf = conf == null ? new LinkedHashMap<>() : new LinkedHashMap<>(conf);
    }

    public String repr() {
        return "CheckpointerConfig(type='" + type + "', conf=" + redact(conf) + ")";
    }

    @Override
    public String toString() {
        return "type='" + type + "' conf=" + redact(conf);
    }

    public String toSimpleString() {
        return toString();
    }

    public Object redactUrlsInValue(Object value) {
        return redact(value);
    }

    @SuppressWarnings("unchecked")
    private Object redact(Object value) {
        return redact(null, value);
    }

    private Object redact(String key, Object value) {
        if (isSensitiveKey(key) && value != null) {
            return "***";
        }
        if (value instanceof String text && isUri(text)) {
            try {
                URI uri = URI.create(text);
                if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
                    String username = uri.getUserInfo().substring(0, uri.getUserInfo().indexOf(':'));
                    return new URI(uri.getScheme(), username + ":***", uri.getHost(), uri.getPort(),
                            uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
                }
            } catch (Exception ignored) {
                return value;
            }
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String entryKey = String.valueOf(entry.getKey());
                copy.put(entryKey, redact(entryKey, entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> redact(null, item)).toList();
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
        return normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("api_key")
                || normalized.contains("apikey");
    }

    private boolean isUri(String text) {
        return text.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    }
}
