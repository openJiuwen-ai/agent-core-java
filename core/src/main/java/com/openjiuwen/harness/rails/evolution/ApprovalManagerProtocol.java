/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Mirrors Python's {@code ApprovalManagerProtocol} in
 * {@code openjiuwen/harness/rails/evolution/contracts.py}.
 */
public interface ApprovalManagerProtocol {

    CompletionStage<Object> approveRequest(String requestId, List<String> approvedRecordIds);

    default CompletionStage<Object> approveRequest(String requestId) {
        return approveRequest(requestId, null);
    }

    CompletionStage<Object> rejectRequest(String requestId);
}
