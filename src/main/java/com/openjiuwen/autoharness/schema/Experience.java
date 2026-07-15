/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Public class Experience used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
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
    /**
     * java.util.ArrayList<>.
     * 
     * @since 0.1.7
     */
    private java.util.List<String> filesChanged = new java.util.ArrayList<>();
    @Builder.Default
    /**
     * randomId.
     * 
     * @since 0.1.7
     */
    private String id = randomId();
    @Builder.Default
    /**
     * System.currentTimeMillis.
     * 
     * @since 0.1.7
     */
    private long timestamp = System.currentTimeMillis() / 1000;

    /**
     * randomId.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
