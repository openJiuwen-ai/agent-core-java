/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Pregel graph execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.config.PregelConfig}
 * and {@code InnerPregelConfig}.
 * 
 * @since 0.1.7
 */
public class PregelConfig {
    private String sessionId;
    private int recursionLimit;
    private String ns;
    private String parentNs;

    /**
     * PregelConfig.
     * 
     * @since 0.1.7
     */
    public PregelConfig() {
        this.recursionLimit = PregelConstants.MAX_RECURSIVE_LIMIT;
    }

    /**
     * PregelConfig.
     * 
     * @param sessionId sessionId
     * @param ns ns
     * @param recursionLimit recursionLimit
     * @since 0.1.7
     */
    public PregelConfig(String sessionId, String ns, int recursionLimit) {
        this.sessionId = sessionId;
        this.ns = ns;
        this.recursionLimit = recursionLimit;
    }

    /**
     * getSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * setSessionId.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * getRecursionLimit.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRecursionLimit() {
        return recursionLimit;
    }

    /**
     * setRecursionLimit.
     * 
     * @param recursionLimit recursionLimit
     * @since 0.1.7
     */
    public void setRecursionLimit(int recursionLimit) {
        this.recursionLimit = recursionLimit;
    }

    /**
     * getNs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNs() {
        return ns;
    }

    /**
     * setNs.
     * 
     * @param ns ns
     * @since 0.1.7
     */
    public void setNs(String ns) {
        this.ns = ns;
    }

    /**
     * getParentNs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getParentNs() {
        return parentNs;
    }

    /**
     * setParentNs.
     * 
     * @param parentNs parentNs
     * @since 0.1.7
     */
    public void setParentNs(String parentNs) {
        this.parentNs = parentNs;
    }

    /**
     * Get a config value by key name (for compatibility with dict-style access).
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
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
     * 
     * @return the result
     * @since 0.1.7
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
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static PregelConfig createInnerConfig(PregelConfig config) {
        PregelConfig inner = new PregelConfig();
        if (config != null) {
            inner.sessionId = config.sessionId;
            inner.ns = config.ns;
            inner.parentNs = config.parentNs;
            inner.recursionLimit =
                config.recursionLimit > 0 ? config.recursionLimit : PregelConstants.MAX_RECURSIVE_LIMIT;
        }
        return inner;
    }

    /**
     * DEFAULT.
     * 
     * @since 0.1.7
     */
    public static final PregelConfig DEFAULT = new PregelConfig(null, null, PregelConstants.MAX_RECURSIVE_LIMIT);
}
