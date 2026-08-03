/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Memory SQL row for persisted user messages.
 *
 * <p>Mirrors Python's {@code UserMessage} in
 * {@code openjiuwen/core/memory/manage/mem_model/db_model.py}.</p>
 */
public class UserMessage extends MessageMixin {

    public static final String TABLE_NAME = "user_message";
}
