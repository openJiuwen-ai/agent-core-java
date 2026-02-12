// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool error
 * 
 * @since 0.1.4
 */
public class ToolError extends ExecutionError {
    
    private Object card;
    
    public ToolError(StatusCode status) {
        super(status);
    }
    
    public ToolError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(status, msg, details, cause, params);
    }
    
    /**
     * Constructor with card
     * 
     * @param status the status code
     * @param msg custom message
     * @param details additional details
     * @param cause the cause exception
     * @param card the tool card
     * @param params template parameters
     */
    public ToolError(StatusCode status, String msg, Object details, Throwable cause, Object card, Map<String, Object> params) {
        super(status, msg, enrichDetailsWithCard(details, card), cause, enrichParamsWithCard(params, card));
        this.card = card;
    }
    
    private static Object enrichDetailsWithCard(Object details, Object card) {
        if (card == null) {
            return details;
        }
        
        if (details == null) {
            Map<String, Object> newDetails = new HashMap<>();
            newDetails.put("card", card);
            return newDetails;
        }
        
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> detailsMap = new HashMap<>((Map<String, Object>) details);
            detailsMap.put("card", card);
            return detailsMap;
        }
        
        return details;
    }
    
    private static Map<String, Object> enrichParamsWithCard(Map<String, Object> params, Object card) {
        Map<String, Object> result = params != null ? new HashMap<>(params) : new HashMap<>();
        if (card != null) {
            result.put("card", card);
        }
        return result;
    }
    
    public Object getCard() {
        return card;
    }
}

