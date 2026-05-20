/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Message record projection for monitor APIs, including sender, target and read/broadcast flags.
 *
 * @since 1.0
 */
public class MessageInfo {
    private String messageId;
    private String teamId;
    private String fromMember;
    private String toMember;
    private String content;
    private long timestamp;
    private boolean broadcast;
    private boolean read;
}
