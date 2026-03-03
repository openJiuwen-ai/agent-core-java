// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 参数定义模型
 *
 * <p>用于描述Agent和工作流的输入参数。支持基本类型和复杂嵌套类型。</p>
 *
 * <p>设计原则：
 * <ul>
 *     <li>{@code items} 字段仅用于 Array 类型</li>
 *     <li>{@code properties} 字段仅用于 Object 类型</li>
 *     <li>对于其他类型，这些字段必须为 null</li>
 *     <li>验证确保数据一致性</li>
 * </ul>
 * </p>
 *
 * <p>Attributes:
 *     name: 参数名称
 *     description: 参数描述
 *     type: 参数类型
 *     required: 参数是否必填
 *     default: 默认值（可选）
 *     items: 数组元素类型定义（仅 Array 类型）
 *     properties: 对象属性列表（仅 Object 类型）
 * </p>
 */
public class Param {

    private String name;
    private String description;
    private ParamType type;
    private boolean required;
    private Object defaultValue;
    private Param items;
    private List<Param> properties;

    /**
     * 默认构造函数
     */
    public Param() {
    }

    /**
     * 全参数构造函数
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param type 参数类型
     * @param required 是否必填
     * @param defaultValue 默认值
     * @param items 数组元素类型定义（仅 Array）
     * @param properties 对象属性列表（仅 Object）
     */
    public Param(String name, String description, ParamType type, boolean required,
                 Object defaultValue, Param items, List<Param> properties) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.items = items;
        this.properties = properties;
        validateTypeSpecificFields();
    }

    /**
     * 创建字符串类型参数
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @return Param实例
     */
    public static Param string(String name, String description, boolean required, String defaultValue) {
        return new Param(name, description, ParamType.STRING, required, defaultValue, null, null);
    }

    /**
     * 创建字符串类型参数（无默认值）
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @return Param实例
     */
    public static Param string(String name, String description, boolean required) {
        return string(name, description, required, null);
    }

    /**
     * 创建布尔类型参数
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @return Param实例
     */
    public static Param booleanType(String name, String description, boolean required, Boolean defaultValue) {
        return new Param(name, description, ParamType.BOOLEAN, required, defaultValue, null, null);
    }

    /**
     * 创建布尔类型参数（无默认值）
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @return Param实例
     */
    public static Param booleanType(String name, String description, boolean required) {
        return booleanType(name, description, required, null);
    }

    /**
     * 创建整数类型参数
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @return Param实例
     */
    public static Param integer(String name, String description, boolean required, Integer defaultValue) {
        return new Param(name, description, ParamType.INTEGER, required, defaultValue, null, null);
    }

    /**
     * 创建整数类型参数（无默认值）
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @return Param实例
     */
    public static Param integer(String name, String description, boolean required) {
        return integer(name, description, required, null);
    }

    /**
     * 创建数字类型参数
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @return Param实例
     */
    public static Param number(String name, String description, boolean required, Double defaultValue) {
        return new Param(name, description, ParamType.NUMBER, required, defaultValue, null, null);
    }

    /**
     * 创建数字类型参数（无默认值）
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @return Param实例
     */
    public static Param number(String name, String description, boolean required) {
        return number(name, description, required, null);
    }

    /**
     * 创建数组类型参数
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param items 数组元素类型定义（必需）
     * @param defaultValue 默认值
     * @return Param实例
     */
    public static Param array(String name, String description, boolean required, Param items, List<?> defaultValue) {
        return new Param(name, description, ParamType.ARRAY, required, defaultValue, items, null);
    }

    /**
     * 创建数组类型参数（无默认值）
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param items 数组元素类型定义（必需）
     * @return Param实例
     */
    public static Param array(String name, String description, boolean required, Param items) {
        return array(name, description, required, items, null);
    }

    /**
     * 创建对象类型参数
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param properties 对象属性列表（必需）
     * @param defaultValue 默认值
     * @return Param实例
     */
    public static Param object(String name, String description, boolean required, List<Param> properties, Map<String, Object> defaultValue) {
        return new Param(name, description, ParamType.OBJECT, required, defaultValue, null, properties);
    }

    /**
     * 创建对象类型参数（无默认值）
     *
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param properties 对象属性列表（必需）
     * @return Param实例
     */
    public static Param object(String name, String description, boolean required, List<Param> properties) {
        return object(name, description, required, properties, null);
    }

    /**
     * 验证类型特定字段
     *
     * <p>规则：
     * <ul>
     *     <li>Array 类型必须有 items，不能有 properties</li>
     *     <li>Object 类型必须有 properties，不能有 items</li>
     *     <li>其他类型不能有 items 或 properties</li>
     * </ul>
     * </p>
     */
    private void validateTypeSpecificFields() {
        if (type == ParamType.ARRAY) {
            if (items == null) {
                throw new IllegalArgumentException(
                    String.format("Param '%s': Array type requires 'items' field", name)
                );
            }
            if (properties != null) {
                throw new IllegalArgumentException(
                    String.format("Param '%s': Array type should not have 'properties' field", name)
                );
            }
        } else if (type == ParamType.OBJECT) {
            if (properties == null || properties.isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("Param '%s': Object type requires 'properties' field", name)
                );
            }
            if (items != null) {
                throw new IllegalArgumentException(
                    String.format("Param '%s': Object type should not have 'items' field", name)
                );
            }
        } else {
            // 简单类型: String, Boolean, Integer, Number
            if (items != null) {
                throw new IllegalArgumentException(
                    String.format("Param '%s': %s type should not have 'items' field", name, type.getValue())
                );
            }
            if (properties != null) {
                throw new IllegalArgumentException(
                    String.format("Param '%s': %s type should not have 'properties' field", name, type.getValue())
                );
            }
        }
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        validateTypeSpecificFields();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ParamType getType() {
        return type;
    }

    public void setType(ParamType type) {
        this.type = type;
        validateTypeSpecificFields();
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Param getItems() {
        return items;
    }

    public void setItems(Param items) {
        this.items = items;
        validateTypeSpecificFields();
    }

    public List<Param> getProperties() {
        return properties;
    }

    public void setProperties(List<Param> properties) {
        this.properties = properties != null ? new ArrayList<>(properties) : null;
        validateTypeSpecificFields();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Param param = (Param) o;
        return required == param.required
            && Objects.equals(name, param.name)
            && Objects.equals(description, param.description)
            && type == param.type
            && Objects.equals(defaultValue, param.defaultValue)
            && Objects.equals(items, param.items)
            && Objects.equals(properties, param.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, type, required, defaultValue, items, properties);
    }

    @Override
    public String toString() {
        return "Param{"
            + "name='" + name + '\''
            + ", description='" + description + '\''
            + ", type=" + type
            + ", required=" + required
            + ", defaultValue=" + defaultValue
            + ", items=" + items
            + ", properties=" + properties
            + '}';
    }
}