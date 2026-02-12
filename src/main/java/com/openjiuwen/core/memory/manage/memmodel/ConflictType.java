/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

/**
 * Conflict type enumeration.
 * Corresponds to Python: manage/mem_model/memory_unit.py - ConflictType
 */
public enum ConflictType {
    ADD("ADD"),
    DELETE("DELETE"),
    UPDATE("UPDATE"),
    NONE("NONE");

    private final String value;

    ConflictType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

