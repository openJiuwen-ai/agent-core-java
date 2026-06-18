/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

/**
 * Deprecated compatibility facade for the root session module.
 *
 * <p>Mirrors Python's deprecated {@code Session} in
 * {@code openjiuwen/core/session/session.py}.</p>
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public final class Session {

    public static final String DEPRECATION_MESSAGE =
            "`openjiuwen.core.session.Session` is deprecated and will be removed in a future release. "
                    + "Use `openjiuwen.core.[module].Session` instead.";

    public Session() {
    }

    public String deprecationMessage() {
        return DEPRECATION_MESSAGE;
    }
}
