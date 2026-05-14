/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single memory check result item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemCheckItem {
    private String infoId;
    private String infoText;
    private CheckResult result;
    @Builder.Default
    private Map<String, String> relatedInfos = new LinkedHashMap<>();

    public static MemCheckItemBuilder builder() {
        return new MemCheckItemBuilder();
    }

    public String getInfoId() { return infoId; }
    public void setInfoId(String infoId) { this.infoId = infoId; }
    public String getInfoText() { return infoText; }
    public void setInfoText(String infoText) { this.infoText = infoText; }
    public CheckResult getResult() { return result; }
    public void setResult(CheckResult result) { this.result = result; }
    public Map<String, String> getRelatedInfos() { return relatedInfos; }
    public void setRelatedInfos(Map<String, String> relatedInfos) { this.relatedInfos = relatedInfos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(relatedInfos); }

    public static final class MemCheckItemBuilder {
        private String infoId;
        private String infoText;
        private CheckResult result;
        private Map<String, String> relatedInfos = new LinkedHashMap<>();

        public MemCheckItemBuilder infoId(String infoId) { this.infoId = infoId; return this; }
        public MemCheckItemBuilder infoText(String infoText) { this.infoText = infoText; return this; }
        public MemCheckItemBuilder result(CheckResult result) { this.result = result; return this; }
        public MemCheckItemBuilder relatedInfos(Map<String, String> relatedInfos) { this.relatedInfos = relatedInfos; return this; }

        public MemCheckItem build() {
            MemCheckItem item = new MemCheckItem();
            item.setInfoId(infoId);
            item.setInfoText(infoText);
            item.setResult(result);
            item.setRelatedInfos(relatedInfos);
            return item;
        }
    }
}
