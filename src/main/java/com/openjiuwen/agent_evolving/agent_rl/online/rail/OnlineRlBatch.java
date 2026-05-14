/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal RL online upload batch.
 * <p>
 * Mirrors the highest-value tenant/samples envelope used by Python's uploader.
 */
public class OnlineRlBatch {

    private String tenantId = "";
    private final List<OnlineRlSample> samples = new ArrayList<>();

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId != null ? tenantId : "";
    }

    public List<OnlineRlSample> getSamples() {
        return samples;
    }

    public Map<String, Object> toDict() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenant_id", tenantId);
        List<Map<String, Object>> sampleDicts = new ArrayList<>();
        for (OnlineRlSample sample : samples) {
            sampleDicts.add(sample.toDict());
        }
        out.put("samples", sampleDicts);
        return out;
    }
}
