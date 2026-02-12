// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for message subscriptions.
 */
public abstract class SubscriptionBase {
    
    /**
     * Sets the message handler for this subscription.
     *
     * @param handler the handler to process messages
     */
    public void setMessageHandler(AsyncMessageHandler handler) {
        // Default no-op, subclasses should override
    }
    
    /**
     * Activates this subscription to start receiving messages.
     */
    public void activate() {
        // Default no-op, subclasses should override
    }
    
    /**
     * Deactivates this subscription to stop receiving messages.
     *
     * @return a future that completes when deactivation is done
     */
    public CompletableFuture<Void> deactivate() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Checks if this subscription is currently active.
     *
     * @return true if active, false otherwise
     */
    public boolean isActive() {
        return false;
    }
}

