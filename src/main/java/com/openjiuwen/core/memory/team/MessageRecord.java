/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

/**
 * Lightweight message record for team memory extraction.
 *
 * <p>Mirrors the subset of {@code TeamMessage} fields needed by
 * {@link TeamMemoryExtractor} for building extraction context.</p>
 */
public record MessageRecord(
        long timestamp,
        String fromMemberName,
        String toMemberName,
        String content,
        boolean broadcast
) {
}
