/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Mirrors Python's {@code CommitStateLike} in
 * {@code openjiuwen/core/session/state/base.py}.
 */
public interface CommitStateLike extends StateLike {

    void updateById(String nodeId, Map<String, Object> data);

    void commit(String nodeId);

    default void commit() {
        commit(null);
    }

    void rollback(String nodeId);

    Map<String, Object> getUpdates();

    void setUpdates(Map<String, Object> updates);
}
