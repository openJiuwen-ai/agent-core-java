package com.openjiuwen.core.common.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code ParamType} in
 * {@code openjiuwen/core/common/schema/param.py}.
 */
public enum ParamType {
    STRING("string"),
    BOOLEAN("boolean"),
    INTEGER("integer"),
    NUMBER("number"),
    ARRAY("array"),
    OBJECT("object");

    private final String value;

    ParamType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ParamType fromValue(String value) {
        for (ParamType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ParamType: " + value);
    }
}
