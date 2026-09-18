/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public class SessionToolkit used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class SessionToolkit {
    private final Map<String, SessionTaskRow> rows = new LinkedHashMap<>();

    /**
     * upsertRunning.
     * 
     * @param taskId taskId
     * @param subSessionId subSessionId
     * @param description description
     * @since 0.1.7
     */
    public void upsertRunning(String taskId, String subSessionId, String description) {
        rows.put(taskId, SessionTaskRow.builder().taskId(taskId).subSessionId(subSessionId).description(description)
                .status("running").build());
    }

    /**
     * markCompleted.
     * 
     * @param taskId taskId
     * @param result result
     * @since 0.1.7
     */
    public void markCompleted(String taskId, String result) {
        SessionTaskRow row = rows.get(taskId);
        if (row != null) {
            row.setStatus("completed");
            row.setResult(result);
        }
    }

    /**
     * markFailed.
     * 
     * @param taskId taskId
     * @param error error
     * @since 0.1.7
     */
    public void markFailed(String taskId, String error) {
        SessionTaskRow row = rows.get(taskId);
        if (row != null) {
            row.setStatus("error");
            row.setError(error);
        }
    }

    /**
     * markCanceled.
     * 
     * @param taskId taskId
     * @since 0.1.7
     */
    public void markCanceled(String taskId) {
        SessionTaskRow row = rows.get(taskId);
        if (row != null) {
            row.setStatus("canceled");
        }
    }

    /**
     * listAll.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<SessionTaskRow> listAll() {
        return new ArrayList<>(rows.values());
    }

    /**
     * get.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public SessionTaskRow get(String taskId) {
        return rows.get(taskId);
    }

    /**
     * clear.
     * 
     * @since 0.1.7
     */
    public void clear() {
        rows.clear();
    }
}
