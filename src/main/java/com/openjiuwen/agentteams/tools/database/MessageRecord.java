/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class MessageRecord used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class MessageRecord {
    private String messageId;
    private String teamName;
    private String fromMemberName;
    private String toMemberName;
    private String content;
    private long timestamp;
    private boolean isBroadcast;
    private boolean isRead;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class MessageRecordBuilder {
        /**
         * Auto-generated for codecheck compliance.
         */
        public MessageRecordBuilder broadcast(boolean value) {
            this.isBroadcast = value;
            return this;
        }
    }
}
