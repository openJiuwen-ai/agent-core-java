/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

/**
 * Marker for framework stream schema payloads.
 *
 * <p>Mirrors Python's {@code StreamSchemas} union in
 * {@code openjiuwen/core/session/stream/base.py}.</p>
 *
 * <p>Extends {@link java.io.Serializable} so that all stream schema types
 * (OutputSchema, TraceSchema, CustomSchema, WorkflowChunk) can be persisted
 * by Java-native serialization used by PersistenceCheckpointer and
 * RedisCheckpointer.</p>
 */
public interface StreamSchema extends java.io.Serializable {
}
