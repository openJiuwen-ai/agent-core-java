// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription;

/**
 * Unique key for identifying a ResponseCollector.
 * Composed of remoteId, messageId, and optional requestId.
 * 
 * 对应Python: drunner/dmessage_queue/dsubscription/reply_topic_subscription.py - CollectorKey
 */
public record CollectorKey(String remoteId, String messageId, String requestId) {
}

