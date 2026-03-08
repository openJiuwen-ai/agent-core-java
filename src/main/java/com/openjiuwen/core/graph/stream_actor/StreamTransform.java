/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for transforming stream data using schemas or transformers.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.manager.StreamTransform}.
 */
public class StreamTransform {

    /**
     * Transform a message using a user-defined transformer function.
     *
     * @param originMessage the original message
     * @param transformer   the transformer function
     * @return the transformed message
     */
    @SuppressWarnings("unchecked")
    public Object getByDefinedTransformer(Object originMessage, Object transformer) {
        if (transformer instanceof Function) {
            return ((Function<Object, Object>) transformer).apply(originMessage);
        }
        return originMessage;
    }

    /**
     * Transform a message using a default schema-based approach.
     *
     * @param originMessage     the original message (Map)
     * @param streamInputsSchema the schema used to extract fields
     * @return the transformed message
     */
    @SuppressWarnings("unchecked")
    public Object getByDefaultTransformer(Object originMessage, Object streamInputsSchema) {
        if (originMessage instanceof Map && streamInputsSchema != null) {
            return SessionUtils.getBySchema(streamInputsSchema, (Map<String, Object>) originMessage);
        }
        return originMessage;
    }
}
