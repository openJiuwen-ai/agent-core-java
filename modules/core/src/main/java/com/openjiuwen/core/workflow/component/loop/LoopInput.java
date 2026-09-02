/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import java.util.HashMap;
import java.util.Map;

/**
 * Input model for loop component configuration.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopInput}.
 * 
 * @since 0.1.7
 */
public class LoopInput {
    private String loopType = "";
    private Integer loopNumber = 0;

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> loopArray = new HashMap<>();
    private Object boolExpression = "";

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> intermediateVar = new HashMap<>();

    /**
     * LoopInput.
     * 
     * @since 0.1.7
     */
    public LoopInput() {
    }

    /**
     * getLoopType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLoopType() {
        return loopType;
    }

    /**
     * setLoopType.
     * 
     * @param loopType loopType
     * @since 0.1.7
     */
    public void setLoopType(String loopType) {
        this.loopType = loopType;
    }

    /**
     * getLoopNumber.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getLoopNumber() {
        return loopNumber;
    }

    /**
     * setLoopNumber.
     * 
     * @param loopNumber loopNumber
     * @since 0.1.7
     */
    public void setLoopNumber(Integer loopNumber) {
        this.loopNumber = loopNumber;
    }

    /**
     * getLoopArray.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getLoopArray() {
        return loopArray;
    }

    /**
     * setLoopArray.
     * 
     * @param loopArray loopArray
     * @since 0.1.7
     */
    public void setLoopArray(Map<String, Object> loopArray) {
        this.loopArray = loopArray;
    }

    /**
     * getBoolExpression.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getBoolExpression() {
        return boolExpression;
    }

    /**
     * setBoolExpression.
     * 
     * @param boolExpression boolExpression
     * @since 0.1.7
     */
    public void setBoolExpression(Object boolExpression) {
        this.boolExpression = boolExpression;
    }

    /**
     * getIntermediateVar.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getIntermediateVar() {
        return intermediateVar;
    }

    /**
     * setIntermediateVar.
     * 
     * @param intermediateVar intermediateVar
     * @since 0.1.7
     */
    public void setIntermediateVar(Map<String, Object> intermediateVar) {
        this.intermediateVar = intermediateVar;
    }

    /**
     * fromMap.
     * 
     * @param map map
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static LoopInput fromMap(Map<String, Object> map) {
        LoopInput input = new LoopInput();
        if (map == null) {
            return input;
        }
        if (map.containsKey("loop_type")) {
            input.loopType = String.valueOf(map.get("loop_type"));
        }
        if (map.containsKey("loop_number")) {
            input.loopNumber = parseLoopNumber(map.get("loop_number"));
        }
        if (map.containsKey("loop_array") && map.get("loop_array") instanceof Map) {
            input.loopArray = (Map<String, Object>) map.get("loop_array");
        }
        if (map.containsKey("bool_expression")) {
            input.boolExpression = map.get("bool_expression");
        }
        if (map.containsKey("intermediate_var") && map.get("intermediate_var") instanceof Map) {
            input.intermediateVar = (Map<String, Object>) map.get("intermediate_var");
        }
        return input;
    }

    /**
     * parseLoopNumber.
     * 
     * @param rawValue rawValue
     * @return the result
     * @since 0.1.7
     */
    private static Integer parseLoopNumber(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer
                || rawValue instanceof Long) {
            return ((Number) rawValue).intValue();
        }
        if (rawValue instanceof Float || rawValue instanceof Double) {
            double value = ((Number) rawValue).doubleValue();
            if (Double.isFinite(value) && Math.rint(value) == value) {
                return (int) value;
            }
            throw new IllegalArgumentException("1 validation error for LoopInput\nloop_number");
        }
        if (rawValue instanceof String strValue) {
            try {
                return Integer.valueOf(strValue);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("1 validation error for LoopInput\nloop_number", ex);
            }
        }
        throw new IllegalArgumentException("1 validation error for LoopInput\nloop_number");
    }
}
