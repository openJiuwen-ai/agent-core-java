/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.session.checkpointer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for creating a checkpointer via {@link CheckpointerFactory}.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.checkpointer.CheckpointerConfig}.
 */
public class CheckpointerConfig {

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
}
