/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code StreamTransform} in
 * {@code openjiuwen/core/graph/stream_actor/manager.py}.
 */
public class StreamTransform {

    /**
     * Applies a caller-defined transformer to the original stream message.
     *
     * @param originMessage original stream message
     * @param transformer caller-defined transformer
     * @return transformed message
     */
    public Map<String, Object> getByDefinedTransformer(
            Map<String, Object> originMessage,
            Function<Map<String, Object>, Map<String, Object>> transformer) {
        return transformer.apply(originMessage);
    }

    /**
     * Resolves the stream input schema against the original stream message.
     *
     * @param originMessage original stream message
     * @param streamInputsSchema stream input schema
     * @return resolved stream message
     */
    public Map<String, Object> getByDefaultTransformer(
            Map<String, Object> originMessage,
            Map<String, Object> streamInputsSchema) {
        return castMap(SessionUtils.getBySchema(streamInputsSchema, originMessage));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("stream transform result is invalid");
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }
}
