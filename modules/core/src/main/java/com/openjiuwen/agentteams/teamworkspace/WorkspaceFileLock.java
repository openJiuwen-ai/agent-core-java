/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Public class WorkspaceFileLock used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceFileLock {
    private String filePath;
    private String holderId;
    private String holderName;
    private String acquiredAt;
    @Builder.Default
    private int timeoutSeconds = 300;

    /**
     * isExpired.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isExpired() {
        OffsetDateTime acquired = OffsetDateTime.parse(acquiredAt);
        return OffsetDateTime.now(ZoneOffset.UTC).isAfter(acquired.plusSeconds(timeoutSeconds));
    }
}
