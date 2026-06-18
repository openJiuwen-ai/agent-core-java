/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single LLM memory check result item.
 *
 * <p>Mirrors Python's {@code MemCheckItem} in
 * {@code openjiuwen/core/memory/manage/update/mem_update_checker.py}.</p>
 */
public record MemCheckItem(
        @JsonProperty("info_id") String infoId,
        @JsonProperty("info_text") String infoText,
        CheckResult result,
        @JsonProperty("related_infos") Map<String, String> relatedInfos
) {

    public MemCheckItem {
        Objects.requireNonNull(infoId, "info_id");
        Objects.requireNonNull(infoText, "info_text");
        Objects.requireNonNull(result, "result");
        relatedInfos = relatedInfos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(relatedInfos);
    }
}
