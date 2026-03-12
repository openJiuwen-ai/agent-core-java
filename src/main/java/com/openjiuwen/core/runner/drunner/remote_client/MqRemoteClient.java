/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.DistributedRunner;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ResponseCollector;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * MQ-backed remote client.
 */
public class MqRemoteClient implements RemoteClient {

    private final RemoteClientConfig config;
    private MessageQueueBase mq;
    private ReplyTopicSubscription replySubscription;
    private boolean started;

    public MqRemoteClient(RemoteClientConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        DistributedRunner.ensureStarted();
        this.mq = DistributedRunner.messageQueue();
        this.replySubscription = DistributedRunner.replySubscription();
        this.started = true;
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        ensureStarted();
        String messageId = buildMessageId(inputs);
        double effectiveTimeout = timeoutSeconds != null ? timeoutSeconds
                : RunnerConfig.getRunnerConfig().getDistributedConfig().getRequestTimeout();
        ResponseCollector collector = replySubscription.registerCollector(messageId, config.getId(), null, effectiveTimeout);
        try {
            mq.produceMessage(config.getTopic(), buildRequest(messageId, inputs, false, effectiveTimeout));
            return collector.result(effectiveTimeout);
        } finally {
            replySubscription.unregisterCollector(messageId, config.getId(), null);
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        ensureStarted();
        String messageId = buildMessageId(inputs);
        double effectiveTimeout = timeoutSeconds != null ? timeoutSeconds
                : RunnerConfig.getRunnerConfig().getDistributedConfig().getRequestTimeout();
        ResponseCollector collector = replySubscription.registerCollector(messageId, config.getId(), null, effectiveTimeout);
        mq.produceMessage(config.getTopic(), buildRequest(messageId, inputs, true, effectiveTimeout));
        return collector.stream(effectiveTimeout);
    }

    private void ensureStarted() {
        if (!started) {
            start();
        }
    }

    private String buildMessageId(Map<String, Object> inputs) {
        String sessionId = inputs != null && inputs.get("conversation_id") != null
                ? String.valueOf(inputs.get("conversation_id"))
                : "default_session";
        return sessionId + "_" + UUID.randomUUID();
    }

    private DmqRequestMessage buildRequest(String messageId,
                                           Map<String, Object> inputs,
                                           boolean enableStream,
                                           double timeoutSeconds) {
        DmqRequestMessage request = new DmqRequestMessage();
        request.setType(DMessageType.INPUT);
        request.setMessageId(messageId);
        request.setReplyTopic(replySubscription.getTopic());
        request.setSenderId(replySubscription.getTopic());
        request.setReceiverId(config.getId());
        request.setEnableStream(enableStream);
        request.setExpireAt((System.currentTimeMillis() / 1000.0) + timeoutSeconds);
        request.setBody(inputs);
        return request;
    }
}
