// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.exception.StatusCode;

/**
 * Base message for queue communication.
 */
public class QueueMessage {
    
    private String messageId = "";
    private Object payload;
    private int errorCode = StatusCode.SUCCESS.getCode();
    private String errorMsg = "";
    
    public QueueMessage() {
    }
    
    public QueueMessage(String messageId, Object payload) {
        this.messageId = messageId != null ? messageId : "";
        this.payload = payload;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId != null ? messageId : "";
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public void setPayload(Object payload) {
        this.payload = payload;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMsg() {
        return errorMsg;
    }
    
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg != null ? errorMsg : "";
    }
}

