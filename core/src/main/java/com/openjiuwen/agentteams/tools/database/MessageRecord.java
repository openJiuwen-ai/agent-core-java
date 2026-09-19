/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class MessageRecord used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
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
     * MessageRecordBuilder.
     * 
     * @since 0.1.7
     */
    public static class MessageRecordBuilder {
        /**
         * broadcast.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public MessageRecordBuilder broadcast(boolean value) {
            this.isBroadcast = value;
            return this;
        }
    }
}
