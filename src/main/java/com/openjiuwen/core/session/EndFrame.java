/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

/**
 * Represents an end frame marker in session stream processing.
 * 
 * <p>Used to signal the end of a stream from a specific source.
 * 
 * @param source the source identifier of the end frame
 * @author OpenJiuwen
 * @since 1.0.0
 */
public record EndFrame(String source) {
}

