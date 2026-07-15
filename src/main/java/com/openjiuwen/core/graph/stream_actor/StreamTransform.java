/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for transforming stream data using schemas or transformers.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.manager.StreamTransform}.
 * 
 * @since 0.1.7
 */
public class StreamTransform {
    /**
     * getByDefinedTransformer.
     * 
     * @param originMessage originMessage
     * @param transformer transformer
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Object getByDefinedTransformer(Object originMessage, Object transformer) {
        if (transformer instanceof Function) {
            return ((Function<Object, Object>) transformer).apply(originMessage);
        }
        return originMessage;
    }

    /**
     * getByDefaultTransformer.
     * 
     * @param originMessage originMessage
     * @param streamInputsSchema streamInputsSchema
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Object getByDefaultTransformer(Object originMessage, Object streamInputsSchema) {
        if (originMessage instanceof Map && streamInputsSchema != null) {
            return SessionUtils.getBySchema(streamInputsSchema, (Map<String, Object>) originMessage);
        }
        return originMessage;
    }
}
