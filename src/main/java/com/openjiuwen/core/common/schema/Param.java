package com.openjiuwen.core.common.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数定义模型（支持嵌套结构）
 * 
 * <p>用于描述Agent和工作流的输入参数。
 * 支持基本类型和复杂嵌套类型。
 * 
 * <p><strong>设计原则</strong>：
 * <ul>
 *   <li>{@code items}字段<strong>仅</strong>用于Array类型</li>
 *   <li>{@code properties}字段<strong>仅</strong>用于Object类型</li>
 *   <li>其他类型，这些字段必须为null</li>
 *   <li>验证确保数据一致性</li>
 * </ul>
 * 
 * <p><strong>使用示例</strong>：
 * <pre>{@code
 * // 简单字符串参数
 * Param username = Param.string("username", "用户名", true);
 * 
 * // 数组参数（字符串数组）
 * Param tags = Param.array("tags", "标签列表", false,
 *     Param.string("tag", "标签", true));
 * 
 * // 对象参数
 * Param user = Param.object("user", "用户信息", true,
 *     List.of(
 *         Param.string("name", "姓名", true),
 *         Param.integer("age", "年龄", false)
 *     ));
 * }</pre>
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class Param {
    
    private final String name;
    private final String description;
    private final ParamType type;
    private final boolean required;
    private final Object defaultValue;
    
    /** [仅用于Array] 数组元素类型定义 */
    private final Param items;
    
    /** [仅用于Object] 对象属性列表 */
    private final List<Param> properties;
    
    /**
     * 私有构造器，通过Builder创建
     */
    private Param(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.type = builder.type;
        this.required = builder.required;
        this.defaultValue = builder.defaultValue;
        this.items = builder.items;
        this.properties = builder.properties;
        
        validate();
    }
    
    /**
     * 验证类型特定字段的正确性
     * 
     * <p><strong>验证规则</strong>：
     * <ul>
     *   <li>Array类型<strong>必须</strong>有items，<strong>不能</strong>有properties</li>
     *   <li>Object类型<strong>必须</strong>有properties，<strong>不能</strong>有items</li>
     *   <li>其他类型<strong>不能</strong>有items或properties</li>
     * </ul>
     * 
     * @throws IllegalArgumentException 如果验证失败
     */
    private void validate() {
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
            if (properties == null) {
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
                    String.format("Param '%s': %s type should not have 'items' field", 
                        name, type.getValue())
                );
            }
            if (properties != null) {
                throw new IllegalArgumentException(
                    String.format("Param '%s': %s type should not have 'properties' field", 
                        name, type.getValue())
                );
            }
        }
    }
    
    // Getters
    
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
    
    public Object getDefaultValue() {
        return defaultValue;
    }
    
    public Param getItems() {
        return items;
    }
    
    public List<Param> getProperties() {
        return properties != null ? new ArrayList<>(properties) : null;
    }
    
    // 工厂方法：提供更好的可读性和易用性
    
    /**
     * 创建字符串类型参数
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @return 字符串参数
     */
    public static Param string(String name, String description, boolean required) {
        return string(name, description, required, null);
    }
    
    /**
     * 创建字符串类型参数（带默认值）
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @return 字符串参数
     */
    public static Param string(String name, String description, boolean required, String defaultValue) {
        return new Builder()
            .name(name)
            .description(description)
            .type(ParamType.STRING)
            .required(required)
            .defaultValue(defaultValue)
            .build();
    }
    
    /**
     * 创建布尔类型参数
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @return 布尔参数
     */
    public static Param booleanParam(String name, String description, boolean required) {
        return booleanParam(name, description, required, null);
    }
    
    /**
     * 创建布尔类型参数（带默认值）
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @return 布尔参数
     */
    public static Param booleanParam(String name, String description, boolean required, Boolean defaultValue) {
        return new Builder()
            .name(name)
            .description(description)
            .type(ParamType.BOOLEAN)
            .required(required)
            .defaultValue(defaultValue)
            .build();
    }
    
    /**
     * 创建整数类型参数
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @return 整数参数
     */
    public static Param integer(String name, String description, boolean required) {
        return integer(name, description, required, null);
    }
    
    /**
     * 创建整数类型参数（带默认值）
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @return 整数参数
     */
    public static Param integer(String name, String description, boolean required, Integer defaultValue) {
        return new Builder()
            .name(name)
            .description(description)
            .type(ParamType.INTEGER)
            .required(required)
            .defaultValue(defaultValue)
            .build();
    }
    
    /**
     * 创建数字类型参数（浮点数）
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @return 数字参数
     */
    public static Param number(String name, String description, boolean required) {
        return number(name, description, required, null);
    }
    
    /**
     * 创建数字类型参数（浮点数，带默认值）
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @return 数字参数
     */
    public static Param number(String name, String description, boolean required, Double defaultValue) {
        return new Builder()
            .name(name)
            .description(description)
            .type(ParamType.NUMBER)
            .required(required)
            .defaultValue(defaultValue)
            .build();
    }
    
    /**
     * 创建数组类型参数
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param items 数组元素类型定义（必填）
     * @return 数组参数
     */
    public static Param array(String name, String description, boolean required, Param items) {
        return array(name, description, required, items, null);
    }
    
    /**
     * 创建数组类型参数（带默认值）
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param items 数组元素类型定义（必填）
     * @param defaultValue 默认值
     * @return 数组参数
     */
    public static Param array(String name, String description, boolean required, Param items, List<?> defaultValue) {
        return new Builder()
            .name(name)
            .description(description)
            .type(ParamType.ARRAY)
            .required(required)
            .items(items)
            .defaultValue(defaultValue)
            .build();
    }
    
    /**
     * 创建对象类型参数
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param properties 对象属性列表（必填）
     * @return 对象参数
     */
    public static Param object(String name, String description, boolean required, List<Param> properties) {
        return object(name, description, required, properties, null);
    }
    
    /**
     * 创建对象类型参数（带默认值）
     * 
     * @param name 参数名称
     * @param description 参数描述
     * @param required 是否必填
     * @param properties 对象属性列表（必填）
     * @param defaultValue 默认值
     * @return 对象参数
     */
    public static Param object(String name, String description, boolean required, 
                               List<Param> properties, Object defaultValue) {
        return new Builder()
            .name(name)
            .description(description)
            .type(ParamType.OBJECT)
            .required(required)
            .properties(properties)
            .defaultValue(defaultValue)
            .build();
    }
    
    /**
     * Builder模式构建器
     */
    public static class Builder {
        private String name;
        private String description;
        private ParamType type;
        private boolean required;
        private Object defaultValue;
        private Param items;
        private List<Param> properties;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder type(ParamType type) {
            this.type = type;
            return this;
        }
        
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }
        
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }
        
        public Builder items(Param items) {
            this.items = items;
            return this;
        }
        
        public Builder properties(List<Param> properties) {
            this.properties = properties != null ? new ArrayList<>(properties) : null;
            return this;
        }
        
        /**
         * 构建Param实例
         * 
         * @return Param实例
         * @throws IllegalArgumentException 如果参数验证失败
         */
        public Param build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Parameter name is required");
            }
            if (description == null) {
                description = "";
            }
            if (type == null) {
                throw new IllegalArgumentException("Parameter type is required");
            }
            
            return new Param(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("Param{name='%s', type=%s, required=%s}", 
            name, type, required);
    }
}

