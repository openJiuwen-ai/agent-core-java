/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.schema;

import java.util.List;
import java.util.Objects;

/**
 * Parameter definition model with nested structure support.
 * <p>
 * Supports basic types and complex nested types (Array, Object).
 * Validation rules:
 * <ul>
 * <li>Array type MUST have items, MUST NOT have properties</li>
 * <li>Object type MUST have properties, MUST NOT have items</li>
 * <li>Other types MUST NOT have items or properties</li>
 * </ul>
 * 
 * @since 0.1.7
 */
public class Param {
    private final String name;
    private final String description;
    private final ParamType type;
    private final boolean required;
    private final Object defaultValue;
    private final Param items; // ONLY for Array
    private final List<Param> properties; // ONLY for Object

    /**
     * Param.
     * 
     * @param name name
     * @param description description
     * @param type type
     * @param required required
     * @param defaultValue defaultValue
     * @param items items
     * @param properties properties
     * @since 0.1.7
     */
    private Param(String name, String description, ParamType type, boolean required, Object defaultValue, Param items,
            List<Param> properties) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.required = required;
        this.defaultValue = defaultValue;
        this.items = items;
        this.properties = properties;
        validate();
    }

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    private void validate() {
        switch (type) {
            case ARRAY -> {
                if (items == null) {
                    throw new IllegalArgumentException("Param '" + name + "': Array type requires 'items' field");
                }
                if (properties != null) {
                    throw new IllegalArgumentException(
                            "Param '" + name + "': Array type should not have 'properties' field");
                }
            }
            case OBJECT -> {
                if (properties == null) {
                    throw new IllegalArgumentException("Param '" + name + "': Object type requires 'properties' field");
                }
                if (items != null) {
                    throw new IllegalArgumentException(
                            "Param '" + name + "': Object type should not have 'items' field");
                }
            }
            default -> {
                if (items != null) {
                    throw new IllegalArgumentException(
                            "Param '" + name + "': " + type.getValue() + " type should not have 'items' field");
                }
                if (properties != null) {
                    throw new IllegalArgumentException(
                            "Param '" + name + "': " + type.getValue() + " type should not have 'properties' field");
                }
            }
        }
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        return description;
    }

    /**
     * getType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ParamType getType() {
        return type;
    }

    /**
     * isRequired.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * getDefaultValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * getItems.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Param getItems() {
        return items;
    }

    /**
     * getProperties.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Param> getProperties() {
        return properties;
    }

    // ==================== Factory Methods ====================

    /**
     * Create a string type parameter.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @return the result
     * @since 0.1.7
     */
    public static Param string(String name, String description, boolean required) {
        return new Param(name, description, ParamType.STRING, required, null, null, null);
    }

    /**
     * string.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static Param string(String name, String description, boolean required, String defaultValue) {
        return new Param(name, description, ParamType.STRING, required, defaultValue, null, null);
    }

    /**
     * Create a boolean type parameter.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @return the result
     * @since 0.1.7
     */
    public static Param bool(String name, String description, boolean required) {
        return new Param(name, description, ParamType.BOOLEAN, required, null, null, null);
    }

    /**
     * bool.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static Param bool(String name, String description, boolean required, Boolean defaultValue) {
        return new Param(name, description, ParamType.BOOLEAN, required, defaultValue, null, null);
    }

    /**
     * Create an integer type parameter.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @return the result
     * @since 0.1.7
     */
    public static Param integer(String name, String description, boolean required) {
        return new Param(name, description, ParamType.INTEGER, required, null, null, null);
    }

    /**
     * integer.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static Param integer(String name, String description, boolean required, Integer defaultValue) {
        return new Param(name, description, ParamType.INTEGER, required, defaultValue, null, null);
    }

    /**
     * Create a number (float/double) type parameter.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @return the result
     * @since 0.1.7
     */
    public static Param number(String name, String description, boolean required) {
        return new Param(name, description, ParamType.NUMBER, required, null, null, null);
    }

    /**
     * number.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static Param number(String name, String description, boolean required, Double defaultValue) {
        return new Param(name, description, ParamType.NUMBER, required, defaultValue, null, null);
    }

    /**
     * Create an array type parameter.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @param items items
     * @return the result
     * @since 0.1.7
     */
    public static Param array(String name, String description, boolean required, Param items) {
        return new Param(name, description, ParamType.ARRAY, required, null, items, null);
    }

    /**
     * array.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @param items items
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static Param array(String name, String description, boolean required, Param items, Object defaultValue) {
        return new Param(name, description, ParamType.ARRAY, required, defaultValue, items, null);
    }

    /**
     * Create an object type parameter.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @param properties properties
     * @return the result
     * @since 0.1.7
     */
    public static Param object(String name, String description, boolean required, List<Param> properties) {
        return new Param(name, description, ParamType.OBJECT, required, null, null, properties);
    }

    /**
     * object.
     * 
     * @param name name
     * @param description description
     * @param required required
     * @param properties properties
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static Param object(String name, String description, boolean required, List<Param> properties,
            Object defaultValue) {
        return new Param(name, description, ParamType.OBJECT, required, defaultValue, null, properties);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "Param{name='" + name + "', type=" + type + ", required=" + required + '}';
    }
}
