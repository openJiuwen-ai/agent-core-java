/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

/**
 * Base marker interface for message entities.
 * Corresponds to Python: manage/mem_model/message.py - Base = declarative_base()
 *
 * <p>In Python, this is created via SQLAlchemy's declarative_base().
 * In Java, we use this as a marker interface to identify message entities.
 */
public interface MessageBase {
    // Marker interface for message entities
    // Corresponds to SQLAlchemy's declarative_base()
}

