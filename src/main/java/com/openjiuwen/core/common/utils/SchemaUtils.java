package com.openjiuwen.core.common.utils;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.ValidationError;

import java.util.*;

/**
 * Schema验证和转换工具类（简化实现）
 * 
 * 从 Python schema_utils.py 转换
 * 
 * ⚠️ 简化实现：暂不实现完整的Pydantic功能
 * - 使用Map<String, Object>表示schema
 * - 提供基本的类型检查和必填字段验证
 * - 支持默认值填充
 * 
 * TODO: 后续可考虑引入hibernate-validator或json-schema-validator
 */
public final class SchemaUtils {

    private SchemaUtils() {
        // 防止实例化
    }

    /**
     * 根据提供的schema格式化数据，填充默认值
     *
     * @param data            要格式化的数据
     * @param schema          JSON Schema字典（Map类型）
     * @param skipNoneValue   是否跳过null值
     * @param skipValidate    是否跳过验证
     * @return 格式化后的数据，带默认值
     * @throws ValidationError 如果数据无法按schema格式化
     */
    public static Object formatWithSchema(
            Object data,
            Object schema,
            boolean skipNoneValue,
            boolean skipValidate
    ) {
        try {
            // 处理null数据
            if (data == null) {
                throw new ValidationError(
                        StatusCode.SCHEMA_FORMAT_INVALID,
                        "数据不能为null",
                        null,
                        null,
                        null
                );
            }
            
            // 处理null值
            Object newData = skipNoneValue ? removeNoneValues(data) : data;

            // 确保schema是Map类型
            if (!(schema instanceof Map)) {
                throw new IllegalArgumentException("Schema must be a Map");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> schemaMap = (Map<String, Object>) schema;

            // 先格式化数据（填充默认值）
            Object formattedData = formatData(newData, schemaMap);
            
            // 然后验证格式化后的数据
            if (!skipValidate) {
                validateWithSchema(formattedData, schema);
            }

            return formattedData;

        } catch (ValidationError e) {
            throw e;
        } catch (Exception e) {
            throw ErrorBuilder.build(
                    StatusCode.SCHEMA_FORMAT_INVALID,
                    "格式化失败: " + e.getMessage(),
                    Map.of("data", data),
                    e,
                    null
            );
        }
    }

    /**
     * 递归移除null值
     *
     * @param data 输入数据
     * @return 移除null值后的数据
     */
    public static Object removeNoneValues(Object data) {
        if (data == null) {
            return null;
        }

        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            Map<String, Object> cleanedMap = new HashMap<>();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object cleanedValue = removeNoneValues(entry.getValue());
                if (cleanedValue != null) {
                    cleanedMap.put(entry.getKey(), cleanedValue);
                }
            }

            return cleanedMap.isEmpty() ? null : cleanedMap;

        } else if (data instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) data;
            List<Object> cleanedList = new ArrayList<>();

            for (Object item : list) {
                Object cleanedItem = removeNoneValues(item);
                if (cleanedItem != null) {
                    cleanedList.add(cleanedItem);
                }
            }

            return cleanedList.isEmpty() ? null : cleanedList;

        } else if (data instanceof String || data instanceof Number || data instanceof Boolean) {
            return data;

        } else {
            try {
                return data.toString();
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * 验证数据是否符合schema
     *
     * @param data   要验证的数据
     * @param schema JSON Schema（Map类型）
     * @throws ValidationError 如果数据验证失败
     */
    public static void validateWithSchema(Object data, Object schema) {
        try {
            if (!(schema instanceof Map)) {
                throw new IllegalArgumentException("Schema must be a Map");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> schemaMap = (Map<String, Object>) schema;

            validateDataAgainstSchema(data, schemaMap, "");

        } catch (Exception e) {
            if (e instanceof ValidationError) {
                throw (ValidationError) e;
            }
            throw ErrorBuilder.build(
                    StatusCode.SCHEMA_VALIDATE_INVALID,
                    "验证失败: " + e.getMessage(),
                    data,
                    e,
                    null
            );
        }
    }

    /**
     * 格式化数据，使用schema的默认值
     *
     * @param data      原始数据
     * @param schemaMap schema映射
     * @return 格式化后的数据
     */
    private static Object formatData(Object data, Map<String, Object> schemaMap) {
        String schemaType = (String) schemaMap.getOrDefault("type", "object");

        if (!"object".equals(schemaType)) {
            // 非对象类型，简单返回或使用默认值
            return data != null ? data : schemaMap.get("default");
        }

        // 处理对象类型
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
        
        if (properties == null) {
            return data != null ? data : new HashMap<String, Object>();
        }

        Map<String, Object> result = new HashMap<>();

        // 如果data是Map，复制所有值
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            result.putAll(dataMap);
        }

        // 为所有属性填充默认值（如果缺失）
        for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
            String propName = propEntry.getKey();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> propSchema = (Map<String, Object>) propEntry.getValue();

            if (!result.containsKey(propName)) {
                // 使用默认值
                Object defaultValue = propSchema.get("default");
                if (defaultValue != null) {
                    result.put(propName, defaultValue);
                }
            }
        }

        return result;
    }

    /**
     * 递归验证数据
     *
     * @param data      数据
     * @param schemaMap schema
     * @param path      当前路径（用于错误消息）
     * @throws ValidationError 验证失败
     */
    private static void validateDataAgainstSchema(
            Object data,
            Map<String, Object> schemaMap,
            String path
    ) {
        String schemaType = (String) schemaMap.getOrDefault("type", "object");

        // 检查必填字段
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schemaMap.get("required");
        if (required != null && data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            
            for (String requiredField : required) {
                if (!dataMap.containsKey(requiredField)) {
                    throw ErrorBuilder.build(
                            StatusCode.SCHEMA_VALIDATE_INVALID,
                            "必填字段缺失: " + path + "." + requiredField,
                            data,
                            null,
                            null
                    );
                }
            }
        }

        // 类型检查
        if ("object".equals(schemaType)) {
            if (data != null && !(data instanceof Map)) {
                throw ErrorBuilder.build(
                        StatusCode.SCHEMA_VALIDATE_INVALID,
                        "类型错误: 期望object，实际" + data.getClass().getSimpleName() + " at " + path,
                        data,
                        null,
                        null
                );
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
            
            if (properties != null && data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;

                for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                    String propName = propEntry.getKey();
                    Object propValue = dataMap.get(propName);

                    if (propValue != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> propSchema = (Map<String, Object>) propEntry.getValue();
                        
                        String newPath = path.isEmpty() ? propName : path + "." + propName;
                        validateDataAgainstSchema(propValue, propSchema, newPath);
                    }
                }
            }

        } else if ("string".equals(schemaType)) {
            if (data != null && !(data instanceof String)) {
                throw ErrorBuilder.build(
                        StatusCode.SCHEMA_VALIDATE_INVALID,
                        "类型错误: 期望string at " + path,
                        data,
                        null,
                        null
                );
            }

            if (data instanceof String) {
                String strValue = (String) data;
                
                // minLength检查
                Integer minLength = (Integer) schemaMap.get("minLength");
                if (minLength != null && strValue.length() < minLength) {
                    throw ErrorBuilder.build(
                            StatusCode.SCHEMA_VALIDATE_INVALID,
                            "字符串长度不足: 最小" + minLength + " at " + path,
                            data,
                            null,
                            null
                    );
                }

                // maxLength检查
                Integer maxLength = (Integer) schemaMap.get("maxLength");
                if (maxLength != null && strValue.length() > maxLength) {
                    throw ErrorBuilder.build(
                            StatusCode.SCHEMA_VALIDATE_INVALID,
                            "字符串长度超限: 最大" + maxLength + " at " + path,
                            data,
                            null,
                            null
                    );
                }
            }

        } else if ("integer".equals(schemaType)) {
            if (data != null && !(data instanceof Integer) && !(data instanceof Long)) {
                throw ErrorBuilder.build(
                        StatusCode.SCHEMA_VALIDATE_INVALID,
                        "类型错误: 期望integer at " + path,
                        data,
                        null,
                        null
                );
            }

            if (data instanceof Number) {
                int intValue = ((Number) data).intValue();

                // minimum检查
                Integer minimum = (Integer) schemaMap.get("minimum");
                if (minimum != null && intValue < minimum) {
                    throw ErrorBuilder.build(
                            StatusCode.SCHEMA_VALIDATE_INVALID,
                            "数值过小: 最小" + minimum + " at " + path,
                            data,
                            null,
                            null
                    );
                }

                // maximum检查
                Integer maximum = (Integer) schemaMap.get("maximum");
                if (maximum != null && intValue > maximum) {
                    throw ErrorBuilder.build(
                            StatusCode.SCHEMA_VALIDATE_INVALID,
                            "数值过大: 最大" + maximum + " at " + path,
                            data,
                            null,
                            null
                    );
                }
            }

        } else if ("boolean".equals(schemaType)) {
            if (data != null && !(data instanceof Boolean)) {
                throw ErrorBuilder.build(
                        StatusCode.SCHEMA_VALIDATE_INVALID,
                        "类型错误: 期望boolean at " + path,
                        data,
                        null,
                        null
                );
            }

        } else if ("array".equals(schemaType)) {
            if (data != null && !(data instanceof List)) {
                throw ErrorBuilder.build(
                        StatusCode.SCHEMA_VALIDATE_INVALID,
                        "类型错误: 期望array at " + path,
                        data,
                        null,
                        null
                );
            }

            if (data instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> listValue = (List<Object>) data;

                // minItems检查
                Integer minItems = (Integer) schemaMap.get("minItems");
                if (minItems != null && listValue.size() < minItems) {
                    throw ErrorBuilder.build(
                            StatusCode.SCHEMA_VALIDATE_INVALID,
                            "数组元素不足: 最小" + minItems + " at " + path,
                            data,
                            null,
                            null
                    );
                }

                // maxItems检查
                Integer maxItems = (Integer) schemaMap.get("maxItems");
                if (maxItems != null && listValue.size() > maxItems) {
                    throw ErrorBuilder.build(
                            StatusCode.SCHEMA_VALIDATE_INVALID,
                            "数组元素过多: 最大" + maxItems + " at " + path,
                            data,
                            null,
                            null
                    );
                }
            }
        }
    }
}


