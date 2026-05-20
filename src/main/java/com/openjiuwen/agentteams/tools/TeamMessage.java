/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class TeamMessage used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TeamMessage {
    private String messageId;
    private String teamName;
    private String fromMemberName;
    private String toMemberName;
    private String content;
    private long timestamp;
    @Builder.Default
    private boolean isBroadcast = false;
    @Builder.Default
    private boolean isRead = false;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class TeamMessageBuilder {
        /**
         * Auto-generated for codecheck compliance.
         */
        public TeamMessageBuilder broadcast(boolean value) {
            return this.isBroadcast(value);
        }
    }
}
