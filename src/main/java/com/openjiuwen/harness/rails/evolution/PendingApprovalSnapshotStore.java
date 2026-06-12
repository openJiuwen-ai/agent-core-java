/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agent_evolving.experience.PendingChange;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code PendingApprovalSnapshotStore} in
 * {@code openjiuwen/harness/rails/evolution/contracts.py}.
 */
public class PendingApprovalSnapshotStore extends LinkedHashMap<String, PendingChange> {

    public PendingApprovalSnapshotStore() {
        super();
    }

    public PendingApprovalSnapshotStore(Map<String, PendingChange> values) {
        super(values == null ? Map.of() : values);
    }
}
