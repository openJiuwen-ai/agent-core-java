/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for creating a checkpointer via {@link CheckpointerFactory}.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.checkpointer.CheckpointerConfig}.
 * 
 * @since 0.1.7
 */
public class CheckpointerConfig {
    private String type;
    private Map<String, Object> conf;

    /**
     * CheckpointerConfig.
     * 
     * @since 0.1.7
     */
    public CheckpointerConfig() {
        this.type = "in_memory";
        this.conf = new HashMap<>();
    }

    /**
     * CheckpointerConfig.
     * 
     * @param type type
     * @param conf conf
     * @since 0.1.7
     */
    public CheckpointerConfig(String type, Map<String, Object> conf) {
        this.type = type != null ? type : "in_memory";
        this.conf = conf != null ? conf : new HashMap<>();
    }

    /**
     * getType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getType() {
        return type;
    }

    /**
     * setType.
     * 
     * @param type type
     * @since 0.1.7
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * getConf.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getConf() {
        return conf;
    }

    /**
     * setConf.
     * 
     * @param conf conf
     * @since 0.1.7
     */
    public void setConf(Map<String, Object> conf) {
        this.conf = conf;
    }
}
