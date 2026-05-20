/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class Experience used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class Experience {
    @Builder.Default
    private ExperienceType type = ExperienceType.OPTIMIZATION;
    @Builder.Default
    private String topic = "";
    @Builder.Default
    private String summary = "";
    @Builder.Default
    private String outcome = "";
    @Builder.Default
    private String details = "";
    @Builder.Default
    private String prUrl = "";
    @Builder.Default
    private java.util.List<String> filesChanged = new java.util.ArrayList<>();
    @Builder.Default
    private String id = randomId();
    @Builder.Default
    private long timestamp = System.currentTimeMillis() / 1000;

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
