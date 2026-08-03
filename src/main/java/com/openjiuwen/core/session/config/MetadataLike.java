/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.config;

/**
 * Callback metadata shape.
 *
 * <p>Mirrors Python's {@code MetadataLike} in
 * {@code openjiuwen/core/session/config/base.py}.</p>
 */
public record MetadataLike(String name, String event) {
}
