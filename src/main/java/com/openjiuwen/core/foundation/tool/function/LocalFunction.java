package com.openjiuwen.core.foundation.tool.function;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 本地函数工具
 * 
 * <p>将Java函数包装为Tool接口的实现。
 * 
 * <p>支持两种函数类型：
 * <ul>
 *   <li>普通函数: 返回单个结果，用于invoke()</li>
 *   <li>流式函数: 返回Iterator/Stream/Iterable，用于stream()</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class LocalFunction extends Tool<Map<String, Object>, Object> {
    private final Function<Map<String, Object>, Object> func;
    private Function<Map<String, Object>, Iterator<?>> streamFunc;
    
    /**
     * 构造本地函数工具
     * 
     * @param card 工具卡片
     * @param func 要包装的函数
     */
    public LocalFunction(ToolCard card, Function<Map<String, Object>, Object> func) {
        super(card);
        if (func == null) {
            Map<String, Object> params = Map.of("card", card.toString());
            throw ErrorBuilder.build(StatusCode.TOOL_LOCAL_FUNCTION_FUNC_NOT_SUPPORTED, null, null, null, params);
        }
        this.func = func;
    }
    
    /**
     * 构造本地函数工具（支持流式函数）
     * 
     * @param card 工具卡片
     * @param func 普通函数
     * @param streamFunc 流式函数（返回Iterator）
     */
    public LocalFunction(
            ToolCard card,
            Function<Map<String, Object>, Object> func,
            Function<Map<String, Object>, Iterator<?>> streamFunc) {
        super(card);
        if (func == null) {
            Map<String, Object> params = Map.of("card", card.toString());
            throw ErrorBuilder.build(StatusCode.TOOL_LOCAL_FUNCTION_FUNC_NOT_SUPPORTED, null, null, null, params);
        }
        this.func = func;
        this.streamFunc = streamFunc;
    }
    
    /**
     * 设置流式函数
     * 
     * @param streamFunc 流式函数（返回Iterator）
     * @return this
     */
    public LocalFunction withStreamFunction(Function<Map<String, Object>, Iterator<?>> streamFunc) {
        this.streamFunc = streamFunc;
        return this;
    }
    
    @Override
    public CompletableFuture<Object> invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 格式化输入参数
                Map<String, Object> formattedInputs = formatInputs(inputs, kwargs);
                
                // 检查函数返回类型 - 如果返回Iterator/Stream则报错
                Object result = func.apply(formattedInputs);
                if (result instanceof Iterator || result instanceof Stream || result instanceof Iterable) {
                    Map<String, Object> params = Map.of(
                        "interface", "invoke",
                        "reason", "Function returns iterator/stream, use stream() instead",
                        "card", card.toString()
                    );
                    throw ErrorBuilder.build(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR, null, null, null, params);
                }
                
                return result;
            } catch (Exception e) {
                if (e instanceof RuntimeException && e.getCause() != null) {
                    // 检查是否是我们自己抛出的异常
                    if (e.getMessage() != null && e.getMessage().contains("TOOL_LOCAL_FUNCTION")) {
                        throw (RuntimeException) e;
                    }
                }
                Map<String, Object> params = Map.of(
                    "interface", "invoke",
                    "reason", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "card", card.toString()
                );
                throw ErrorBuilder.build(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR, null, null, e, params);
            }
        });
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Stream<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        try {
            // 格式化输入参数
            Map<String, Object> formattedInputs = formatInputs(inputs, kwargs);
            
            // 优先使用专门的streamFunc
            if (streamFunc != null) {
                Iterator<?> iterator = streamFunc.apply(formattedInputs);
                return (Stream<Object>) StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                    false
                );
            }
            
            // 尝试使用普通func，检查返回类型
            Object result = func.apply(formattedInputs);
            
            if (result instanceof Stream) {
                return (Stream<Object>) result;
            }
            
            if (result instanceof Iterator) {
                Iterator<?> iterator = (Iterator<?>) result;
                return (Stream<Object>) StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                    false
                );
            }
            
            if (result instanceof Iterable) {
                return (Stream<Object>) StreamSupport.stream(
                    ((Iterable<?>) result).spliterator(),
                    false
                );
            }
            
            // 如果不是流式类型，抛出异常
            Map<String, Object> params = Map.of(
                "interface", "stream",
                "reason", "Function does not return iterator/stream/iterable",
                "card", card.toString()
            );
            throw ErrorBuilder.build(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR, null, null, null, params);
            
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            Map<String, Object> params = Map.of(
                "interface", "stream",
                "reason", e.getMessage() != null ? e.getMessage() : "Unknown error",
                "card", card.toString()
            );
            throw ErrorBuilder.build(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR, null, null, e, params);
        }
    }
    
    /**
     * 格式化输入参数
     */
    private Map<String, Object> formatInputs(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> formattedInputs = inputs;
        
        if (card.getInputParams() != null) {
            if (card.getInputParams() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> paramsMap = (Map<String, Object>) card.getInputParams();
                if (!paramsMap.isEmpty()) {
                    boolean skipNoneValue = kwargs != null && 
                        Boolean.TRUE.equals(kwargs.get("skip_none_value"));
                    boolean skipValidate = kwargs != null && 
                        Boolean.TRUE.equals(kwargs.get("skip_inputs_validate"));
                    
                    Object formatted = SchemaUtils.formatWithSchema(
                        inputs,
                        card.getInputParams(),
                        skipNoneValue,
                        skipValidate
                    );
                    if (formatted instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> formattedMap = (Map<String, Object>) formatted;
                        formattedInputs = formattedMap;
                    }
                }
            }
        }
        
        return formattedInputs;
    }
    
    /**
     * 检查是否支持流式调用
     * 
     * @return 如果配置了streamFunc或func返回流式类型则返回true
     */
    public boolean supportsStream() {
        return streamFunc != null;
    }
}
