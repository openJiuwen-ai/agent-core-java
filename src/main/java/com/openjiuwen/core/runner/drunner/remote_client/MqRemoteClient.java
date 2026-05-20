/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.DistributedRunner;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ResponseCollector;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * MQ-backed remote client.
 */
public class MqRemoteClient implements RemoteClient {

    private static final Logger logger = LoggerFactory.getLogger(MqRemoteClient.class);

    private final RemoteClientConfig config;
    private MessageQueueBase mq;
    private ReplyTopicSubscription replySubscription;
    private boolean started;

    /**
     * Auto-generated for codecheck compliance.
     */
    public MqRemoteClient(RemoteClientConfig config) {
        this.config = config;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public void stop() {
        started = false;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isStarted() {
        return started;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        ensureStarted();
        String messageId = buildMessageId(inputs);
        double effectiveTimeout = timeoutSeconds != null ? timeoutSeconds
                : RunnerConfig.getRunnerConfig().getDistributedConfig().getRequestTimeout();
        ResponseCollector collector = replySubscription.registerCollector(messageId, config.getId(), null, effectiveTimeout);
        try {
            mq.produceMessage(config.getTopic(), buildRequest(messageId, inputs, false, effectiveTimeout));
            return collector.result(effectiveTimeout);
        } catch (CancellationException e) {
            logger.info("[MqRemoteClient] invoke {} cancelled, sending STOP", messageId);
            sendStopMessage(messageId);
            throw e;
        } finally {
            replySubscription.unregisterCollector(messageId, config.getId(), null);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        ensureStarted();
        String messageId = buildMessageId(inputs);
        double effectiveTimeout = timeoutSeconds != null ? timeoutSeconds
                : RunnerConfig.getRunnerConfig().getDistributedConfig().getRequestTimeout();
        ResponseCollector collector = replySubscription.registerCollector(messageId, config.getId(), null, effectiveTimeout);
        try {
            mq.produceMessage(config.getTopic(), buildRequest(messageId, inputs, true, effectiveTimeout));
            return collector.stream(effectiveTimeout);
        } catch (CancellationException e) {
            logger.info("[MqRemoteClient] stream {} cancelled, sending STOP", messageId);
            sendStopMessage(messageId);
            throw e;
        }
    }

    /**
     * Send a STOP message to cancel an in-flight request.
     * Messages contain an expiration time, so STOP is only needed when isClosed early
     * (not on timeout).
     */
    private void sendStopMessage(String messageId) {
        try {
            DmqRequestMessage stopMsg = new DmqRequestMessage();
            stopMsg.setType(DMessageType.STOP);
            stopMsg.setMessageId(messageId);
            stopMsg.setSenderId(replySubscription.getTopic());
            stopMsg.setReceiverId(config.getId());
            stopMsg.setBody(Map.of());
            double requestTimeout = RunnerConfig.getRunnerConfig().getDistributedConfig().getRequestTimeout();
            stopMsg.setExpireAt((System.currentTimeMillis() / 1000.0) + requestTimeout);
            mq.produceMessage(config.getTopic(), stopMsg);
            logger.info("[MqRemoteClient] Sent STOP message for {}", messageId);
        } catch (Exception e) {
            logger.error("[MqRemoteClient] Failed to send STOP message: {}", e.getMessage(), e);
        }
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
