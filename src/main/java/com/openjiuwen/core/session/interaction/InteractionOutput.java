/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

/**
 * Output model for interaction responses.
 * 
 * @param id the interaction node ID
 * @param value the interaction value
 * @author OpenJiuwen
 * @since 1.0.0
 */
public record InteractionOutput(String id, Object value) {
}

