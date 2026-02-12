package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.utils.CallableSchemaExtractor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

/**
 * 工具装饰器工厂类
 * 
 * <p>提供创建LocalFunction工具的静态方法，模拟Python的@tool装饰器。
 * 
 * <p>支持多种用法模式：
 * <ol>
 *   <li>使用预定义ToolCard: {@link #create(ToolCard, Function)}</li>
 *   <li>自动生成ToolCard: {@link #create(String, String, Object, Function)}</li>
 *   <li>从Method反射自动创建: {@link #fromMethod(Method, Object)}</li>
 *   <li>使用card并覆盖属性: {@link #createWithOverride(ToolCard, Function, String, String, Object)}</li>
 *   <li>Builder模式: {@link #builder()}</li>
 * </ol>
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class ToolDecorator {
    
    private static final LoggerProtocol logger = LogManager.getLogger("ToolDecorator");
    
    private ToolDecorator() {
        // Utility class
    }
    
    /**
     * 创建LocalFunction工具（使用预定义ToolCard）
     * 
     * @param card 工具卡片
     * @param func 要包装的函数
     * @return LocalFunction实例
     */
    public static LocalFunction create(ToolCard card, Function<Map<String, Object>, Object> func) {
        return new LocalFunction(card, func);
    }
    
    /**
     * 创建LocalFunction工具（自动生成ToolCard）
     * 
     * @param name 工具名称
     * @param description 工具描述
     * @param inputParams 输入参数schema
     * @param func 要包装的函数
     * @return LocalFunction实例
     */
    public static LocalFunction create(
        String name,
        String description,
        Object inputParams,
        Function<Map<String, Object>, Object> func
    ) {
        ToolCard card = ToolCard.builder()
            .name(name)
            .description(description)
            .inputParams(inputParams)
            .build();
        return new LocalFunction(card, func);
    }
    
    /**
     * 使用预构建card创建，支持覆盖属性
     * 
     * <p>对应Python: @tool(card=existing_card, name="override", description="new")
     * 
     * @param card 预构建的工具卡片
     * @param func 要包装的函数
     * @param nameOverride 覆盖的名称（null表示不覆盖）
     * @param descriptionOverride 覆盖的描述（null表示不覆盖）
     * @param inputParamsOverride 覆盖的输入参数schema（null表示不覆盖）
     * @return LocalFunction实例
     */
    public static LocalFunction createWithOverride(
            ToolCard card,
            Function<Map<String, Object>, Object> func,
            String nameOverride,
            String descriptionOverride,
            Object inputParamsOverride) {
        
        boolean hasOverrides = nameOverride != null || descriptionOverride != null || inputParamsOverride != null;
        
        if (!hasOverrides) {
            return new LocalFunction(card, func);
        }
        
        // 输出覆盖警告日志
        if (nameOverride != null && !nameOverride.equals(card.getName())) {
            logger.warning("Overriding card name '" + card.getName() + "' with '" + nameOverride + "'");
        }
        
        ToolCard finalCard = ToolCard.builder()
            .name(nameOverride != null ? nameOverride : card.getName())
            .description(descriptionOverride != null ? descriptionOverride : card.getDescription())
            .inputParams(inputParamsOverride != null ? inputParamsOverride : card.getInputParams())
            .build();
        
        return new LocalFunction(finalCard, func);
    }
    
    /**
     * 从Method自动创建LocalFunction（类似Python @tool的自动模式）
     * 
     * <p>自动从方法签名提取schema和描述。
     * 
     * @param method 要包装的方法
     * @param target 方法所属的对象实例（静态方法传null）
     * @return LocalFunction实例
     */
    public static LocalFunction fromMethod(Method method, Object target) {
        return fromMethod(method, target, true);
    }
    
    /**
     * 从Method自动创建LocalFunction
     * 
     * @param method 要包装的方法
     * @param target 方法所属的对象实例（静态方法传null）
     * @param autoExtract 是否自动提取schema（false则使用空schema）
     * @return LocalFunction实例
     */
    public static LocalFunction fromMethod(Method method, Object target, boolean autoExtract) {
        return fromMethod(method, target, null, null, null, autoExtract);
    }
    
    /**
     * 从Method创建LocalFunction，支持覆盖属性
     * 
     * @param method 要包装的方法
     * @param target 方法所属的对象实例（静态方法传null）
     * @param nameOverride 覆盖的名称（null表示使用方法名）
     * @param descriptionOverride 覆盖的描述（null表示自动提取）
     * @param inputParamsOverride 覆盖的输入参数schema（null表示自动提取）
     * @param autoExtract 是否自动提取schema
     * @return LocalFunction实例
     */
    public static LocalFunction fromMethod(
            Method method,
            Object target,
            String nameOverride,
            String descriptionOverride,
            Object inputParamsOverride,
            boolean autoExtract) {
        
        // 获取最终名称
        String finalName = nameOverride != null ? nameOverride : method.getName();
        
        // 获取最终描述
        String finalDescription = getFinalDescription(method, descriptionOverride, autoExtract);
        
        // 获取最终输入参数schema
        Object finalInputParams = getFinalInputParams(method, inputParamsOverride, autoExtract);
        
        ToolCard card = ToolCard.builder()
            .name(finalName)
            .description(finalDescription)
            .inputParams(finalInputParams)
            .build();
        
        // 创建函数包装器
        Function<Map<String, Object>, Object> func = inputs -> {
            try {
                Object[] args = extractMethodArgs(method, inputs);
                return method.invoke(target, args);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke method: " + method.getName(), e);
            }
        };
        
        return new LocalFunction(card, func);
    }
    
    /**
     * 获取最终描述
     */
    private static String getFinalDescription(Method method, String descriptionOverride, boolean autoExtract) {
        // 优先级1: 显式覆盖
        if (descriptionOverride != null) {
            return descriptionOverride;
        }
        
        // 优先级2: 自动提取
        if (autoExtract) {
            String extracted = CallableSchemaExtractor.extractFunctionDescription(method);
            if (extracted != null && !extracted.isEmpty()) {
                return extracted;
            }
        }
        
        // 优先级3: 回退到方法名的人性化版本
        return "Function " + method.getName();
    }
    
    /**
     * 获取最终输入参数schema
     */
    private static Object getFinalInputParams(Method method, Object inputParamsOverride, boolean autoExtract) {
        // 优先级1: 显式覆盖
        if (inputParamsOverride != null) {
            return inputParamsOverride;
        }
        
        // 优先级2: 自动提取
        if (autoExtract) {
            try {
                return CallableSchemaExtractor.generateSchema(method);
            } catch (Exception e) {
                logger.warning("Failed to auto-extract schema for " + method.getName() + ": " + e.getMessage() + ". Using empty schema.");
            }
        }
        
        // 优先级3: 空schema
        return Map.of("type", "object", "properties", Map.of());
    }
    
    /**
     * 从输入Map提取方法参数
     */
    private static Object[] extractMethodArgs(Method method, Map<String, Object> inputs) {
        var parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            args[i] = inputs.get(paramName);
        }
        
        return args;
    }
    
    /**
     * 创建Builder用于构建LocalFunction
     * 
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * LocalFunction Builder类
     * 
     * <p>支持链式配置，对应Python的@tool装饰器参数。
     */
    public static class Builder {
        private String name;
        private String description;
        private Object inputParams;
        private Function<Map<String, Object>, Object> function;
        private ToolCard card;
        private boolean autoExtract = true;
        private Method method;
        private Object methodTarget;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder inputParams(Object inputParams) {
            this.inputParams = inputParams;
            return this;
        }
        
        public Builder function(Function<Map<String, Object>, Object> function) {
            this.function = function;
            return this;
        }
        
        /**
         * 设置预构建的ToolCard
         */
        public Builder card(ToolCard card) {
            this.card = card;
            return this;
        }
        
        /**
         * 设置是否自动提取schema
         */
        public Builder autoExtract(boolean autoExtract) {
            this.autoExtract = autoExtract;
            return this;
        }
        
        /**
         * 设置要包装的方法（用于自动提取）
         */
        public Builder method(Method method, Object target) {
            this.method = method;
            this.methodTarget = target;
            return this;
        }
        
        public LocalFunction build() {
            // 如果提供了method，使用fromMethod
            if (method != null) {
                return fromMethod(method, methodTarget, name, description, inputParams, autoExtract);
            }
            
            // 如果提供了预构建card
            if (card != null) {
                return createWithOverride(card, function, name, description, inputParams);
            }
            
            // 否则创建新card
            String finalName = name != null ? name : "unnamed_tool";
            String finalDescription = description != null ? description : "";
            Object finalInputParams = inputParams != null ? inputParams : Map.of();
            
            ToolCard newCard = ToolCard.builder()
                .name(finalName)
                .description(finalDescription)
                .inputParams(finalInputParams)
                .build();
            
            return new LocalFunction(newCard, function);
        }
    }
}
