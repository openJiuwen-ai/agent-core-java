/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

/**
 * Steering message sent from a client or parent agent to a running task.
 *
 * @since 0.1.13
 */
public class SteeringEvent extends Event {
    private String message;
    private String targetTaskId;

    /**
     * SteeringEvent.
     *
     * @since 0.1.13
     */
    public SteeringEvent() {
        super(EventType.STEERING);
    }

    /**
     * SteeringEvent.
     *
     * @param message steering message
     * @since 0.1.13
     */
    public SteeringEvent(String message) {
        this(message, null);
    }

    /**
     * SteeringEvent.
     *
     * @param message steering message
     * @param targetTaskId optional target task ID
     * @since 0.1.13
     */
    public SteeringEvent(String message, String targetTaskId) {
        super(EventType.STEERING);
        this.message = message;
        this.targetTaskId = targetTaskId;
    }

    /**
     * getMessage.
     *
     * @return steering message
     * @since 0.1.13
     */
    public String getMessage() {
        return message;
    }

    /**
     * setMessage.
     *
     * @param message steering message
     * @since 0.1.13
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * getTargetTaskId.
     *
     * @return optional target task ID
     * @since 0.1.13
     */
    public String getTargetTaskId() {
        return targetTaskId;
    }

    /**
     * setTargetTaskId.
     *
     * @param targetTaskId optional target task ID
     * @since 0.1.13
     */
    public void setTargetTaskId(String targetTaskId) {
        this.targetTaskId = targetTaskId;
    }
}