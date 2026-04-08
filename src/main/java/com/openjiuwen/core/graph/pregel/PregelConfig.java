/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Pregel graph execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.config.PregelConfig}
 * and {@code InnerPregelConfig}.
 */
public class PregelConfig {

    private String sessionId;
    private int recursionLimit;
    private String ns;
    private String parentNs;

    public PregelConfig() {
        this.recursionLimit = PregelConstants.MAX_RECURSIVE_LIMIT;
    }

    public PregelConfig(String sessionId, String ns, int recursionLimit) {
        this.sessionId = sessionId;
        this.ns = ns;
        this.recursionLimit = recursionLimit;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getRecursionLimit() {
        return recursionLimit;
    }

    public void setRecursionLimit(int recursionLimit) {
        this.recursionLimit = recursionLimit;
    }

    public String getNs() {
        return ns;
    }

    public void setNs(String ns) {
        this.ns = ns;
    }

    public String getParentNs() {
        return parentNs;
    }

    public void setParentNs(String parentNs) {
        this.parentNs = parentNs;
    }

    /**
     * Get a config value by key name (for compatibility with dict-style access).
     */
    public Object get(String key) {
        return switch (key) {
            case PregelConstants.SESSION_ID -> sessionId;
            case PregelConstants.NS -> ns;
            case PregelConstants.PARENT_NS -> parentNs;
            case PregelConstants.RECURSION_LIMIT -> recursionLimit;
            default -> null;
        };
    }

    /**
     * Convert to a map representation.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(PregelConstants.SESSION_ID, sessionId);
        map.put(PregelConstants.NS, ns);
        map.put(PregelConstants.PARENT_NS, parentNs);
        map.put(PregelConstants.RECURSION_LIMIT, recursionLimit);
        return map;
    }

    /**
     * Create an inner config copy with defaults applied.
     */
    public static PregelConfig createInnerConfig(PregelConfig config) {
        PregelConfig inner = new PregelConfig();
        if (config != null) {
            inner.sessionId = config.sessionId;
            inner.ns = config.ns;
            inner.parentNs = config.parentNs;
            inner.recursionLimit = config.recursionLimit > 0 ? config.recursionLimit
                    : PregelConstants.MAX_RECURSIVE_LIMIT;
        }
        return inner;
    }

    /**
     * Default Pregel configuration.
     */
    public static final PregelConfig DEFAULT = new PregelConfig(null, null, PregelConstants.MAX_RECURSIVE_LIMIT);
}
