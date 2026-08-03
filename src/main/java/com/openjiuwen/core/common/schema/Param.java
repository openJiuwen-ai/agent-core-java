package com.openjiuwen.core.common.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Mirrors Python's {@code Param} in
 * {@code openjiuwen/core/common/schema/param.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Param {
    private final String name;
    private final String description;
    private final ParamType type;
    private final boolean required;

    @JsonProperty("default")
    private final Object defaultValue;

    private final Param items;
    private final List<Param> properties;

    private Param(
            String name,
            String description,
            ParamType type,
            boolean required,
            Object defaultValue,
            Param items,
            List<Param> properties
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.required = required;
        this.defaultValue = defaultValue;
        this.items = items;
        this.properties = properties;
        validateTypeSpecificFields();
    }

    private void validateTypeSpecificFields() {
        if (type == ParamType.ARRAY) {
            if (items == null) {
                throw new IllegalArgumentException(
                        "Param '" + name + "': Array type requires 'items' field");
            }
            if (properties != null) {
                throw new IllegalArgumentException(
                        "Param '" + name + "': Array type should not have 'properties' field");
            }
            return;
        }

        if (type == ParamType.OBJECT) {
            if (properties == null) {
                throw new IllegalArgumentException(
                        "Param '" + name + "': Object type requires 'properties' field");
            }
            if (items != null) {
                throw new IllegalArgumentException(
                        "Param '" + name + "': Object type should not have 'items' field");
            }
            return;
        }

        if (items != null) {
            throw new IllegalArgumentException(
                    "Param '" + name + "': " + type.getValue() + " type should not have 'items' field");
        }
        if (properties != null) {
            throw new IllegalArgumentException(
                    "Param '" + name + "': " + type.getValue() + " type should not have 'properties' field");
        }
    }

    public static Param string(String name, String description, boolean required) {
        return new Param(name, description, ParamType.STRING, required, null, null, null);
    }

    public static Param string(String name, String description, boolean required, String defaultValue) {
        return new Param(name, description, ParamType.STRING, required, defaultValue, null, null);
    }

    public static Param booleanParam(String name, String description, boolean required, java.lang.Boolean defaultValue) {
        return new Param(name, description, ParamType.BOOLEAN, required, defaultValue, null, null);
    }

    public static Param booleanParam(String name, String description, boolean required) {
        return booleanParam(name, description, required, null);
    }

    public static Param booleanValue(String name, String description, boolean required, java.lang.Boolean defaultValue) {
        return booleanParam(name, description, required, defaultValue);
    }

    public static Param booleanValue(String name, String description, boolean required) {
        return booleanParam(name, description, required, null);
    }

    public static Param integer(String name, String description, boolean required) {
        return new Param(name, description, ParamType.INTEGER, required, null, null, null);
    }

    public static Param integer(String name, String description, boolean required, Integer defaultValue) {
        return new Param(name, description, ParamType.INTEGER, required, defaultValue, null, null);
    }

    public static Param number(String name, String description, boolean required) {
        return new Param(name, description, ParamType.NUMBER, required, null, null, null);
    }

    public static Param number(String name, String description, boolean required, Double defaultValue) {
        return new Param(name, description, ParamType.NUMBER, required, defaultValue, null, null);
    }

    public static Param array(String name, String description, boolean required, Param items) {
        return new Param(name, description, ParamType.ARRAY, required, null, items, null);
    }

    public static Param array(String name, String description, boolean required, Param items, List<?> defaultValue) {
        return new Param(name, description, ParamType.ARRAY, required, defaultValue, items, null);
    }

    public static Param object(String name, String description, boolean required, List<Param> properties) {
        return new Param(name, description, ParamType.OBJECT, required, null, null, properties);
    }

    public static Param object(String name, String description, boolean required, List<Param> properties, Object defaultValue) {
        return new Param(name, description, ParamType.OBJECT, required, defaultValue, null, properties);
    }

    public static Param ofBoolean(String name, String description, boolean required) {
        return booleanParam(name, description, required);
    }

    public static Param ofBoolean(String name, String description, boolean required, java.lang.Boolean defaultValue) {
        return booleanParam(name, description, required, defaultValue);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ParamType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    @JsonProperty("default")
    public Object getDefaultValue() {
        return defaultValue;
    }

    public Param getItems() {
        return items;
    }

    public List<Param> getProperties() {
        return properties;
    }
}
