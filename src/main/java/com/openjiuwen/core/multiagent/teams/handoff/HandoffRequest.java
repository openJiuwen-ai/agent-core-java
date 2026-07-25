/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.session.AgentGroupSessionApi;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class HandoffRequest used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class HandoffRequest {
    private Object inputMessage;

    @Builder.Default
    private List<Map<String, Object>> history = new ArrayList<>();

    private AgentGroupSessionApi session;

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionId() {
        return session != null ? session.getSessionId() : "";
    }
}
