// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.utils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可调用对象Schema提取器
 * 
 * <p>从Java Method对象提取JSON Schema，包括参数类型、描述等信息。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class CallableSchemaExtractor {

    private static final TypeSchemaExtractorRegistry REGISTRY = TypeSchemaExtractorRegistry.getInstance();

    // 常见缩写列表
    private static final List<String> ABBREVIATIONS = List.of(
        "id", "url", "uri", "api", "sql", "html", "xml", "json", "csv"
    );

    /**
     * 生成方法的完整JSON Schema
     * 
     * @param method 要分析的方法
     * @return JSON Schema（Map形式）
     */
    public static Map<String, Object> generateSchema(Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            String paramName = param.getName();
            Type paramType = param.getParameterizedType();
            
            Map<String, Object> paramSchema = getTypeSchema(paramType);
            
            // 添加描述
            String description = humanizeName(paramName);
            if (!paramSchema.containsKey("description")) {
                paramSchema.put("description", description);
            }
            
            // 所有参数默认为必填（Java方法参数没有默认值概念）
            required.add(paramName);
            
            properties.put(paramName, paramSchema);
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        schema.put("title", humanizeName(method.getName()));
        
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    /**
     * 将Java类型转换为JSON Schema
     * 
     * @param type 要转换的类型
     * @return JSON Schema
     */
    public static Map<String, Object> getTypeSchema(Type type) {
        for (TypeSchemaExtractor extractor : REGISTRY.getExtractors()) {
            if (extractor.canExtract(type)) {
                return extractor.extract(type, CallableSchemaExtractor::getTypeSchema);
            }
        }
        
        // 默认处理未知类型
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("description", "Object");
        return schema;
    }

    /**
     * 将变量名转换为人类可读的文本
     * 
     * @param name 变量名
     * @return 人类可读的文本
     */
    public static String humanizeName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String result;
        
        // 处理snake_case
        if (name.contains("_")) {
            String[] words = name.split("_");
            result = String.join(" ", Arrays.stream(words)
                .filter(w -> !w.isEmpty())
                .map(String::toLowerCase)
                .toArray(String[]::new));
        } else {
            // 处理camelCase和PascalCase
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (i == 0) {
                    sb.append(Character.toLowerCase(c));
                } else if (Character.isUpperCase(c)) {
                    // 检查是否需要添加空格
                    boolean prevLower = i > 0 && Character.isLowerCase(name.charAt(i - 1));
                    boolean nextLower = i < name.length() - 1 && Character.isLowerCase(name.charAt(i + 1));
                    if (prevLower || nextLower) {
                        sb.append(' ');
                    }
                    sb.append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
            result = sb.toString();
        }

        // 处理常见缩写（保持大写）
        for (String abbr : ABBREVIATIONS) {
            Pattern pattern = Pattern.compile("\\b" + abbr + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(result);
            result = matcher.replaceAll(abbr.toUpperCase());
        }

        return result.toLowerCase();
    }

    /**
     * 从方法提取函数描述
     * 
     * <p>尝试从JavaDoc注释中提取描述，如果没有则返回人性化的方法名。
     * 
     * @param method 要分析的方法
     * @return 函数描述
     */
    public static String extractFunctionDescription(Method method) {
        // Java运行时无法直接获取JavaDoc，返回人性化的方法名
        return humanizeName(method.getName());
    }

    /**
     * 提取参数描述（从JavaDoc）
     * 
     * <p>Java运行时无法直接获取JavaDoc注释，此方法返回空Map。
     * 如需获取参数描述，可以考虑使用注解或外部文档。
     * 
     * @param method 要分析的方法
     * @return 参数名到描述的映射
     */
    public static Map<String, String> extractDocstringDescriptions(Method method) {
        // Java运行时无法直接获取JavaDoc
        return Collections.emptyMap();
    }

    /**
     * 获取枚举类型的Schema
     * 
     * @param enumClass 枚举类
     * @return JSON Schema
     */
    public static Map<String, Object> getEnumSchema(Class<? extends Enum<?>> enumClass) {
        Enum<?>[] constants = enumClass.getEnumConstants();
        List<String> enumValues = new ArrayList<>();
        for (Enum<?> e : constants) {
            enumValues.add(e.name());
        }
        
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "string");
        schema.put("enum", enumValues);
        schema.put("description", "Enum values: " + String.join(", ", enumValues));
        
        return schema;
    }
}

