// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result;

/**
 * Enum for supported programming languages.
 * 
 * <p>对应 Python: Literal['python', 'javascript']
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public enum Language {
    /**
     * Python language.
     */
    PYTHON("python"),
    
    /**
     * JavaScript language.
     */
    JAVASCRIPT("javascript");

    private final String value;

    Language(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Language fromValue(String value) {
        for (Language language : values()) {
            if (language.value.equalsIgnoreCase(value)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Invalid language: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}

