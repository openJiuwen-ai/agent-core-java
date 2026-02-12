// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.remoteclient;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.MessageQueueBase;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription.ResponseCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于消息队列的远程客户端实现
 * 
 * <p>通过消息队列发送请求，并使用 {@link ReplyTopicSubscription} 和
 * {@link ResponseCollector} 收集响应。支持非流式 (invoke) 和流式 (stream) 两种调用模式。
 * 
 * 对应Python: drunner/remote_client/mq_remote_clent.py - MqRemoteClient
 */
public class MqRemoteClient implements RemoteClient {

    private static final Logger logger = LoggerFactory.getLogger(MqRemoteClient.class);

    /** 客户端是否已启动 */
    volatile boolean started = false;

    /** 消息队列引用（从Runner获取） */
    MessageQueueBase mq;

    /** 远程Agent的请求主题 */
    final String topic;

    /** 远程Agent ID */
    final String remoteId;

    /** 客户端配置 */
    final RemoteClientConfig config;

    /** 系统回复主题订阅（从Runner获取） */
    ReplyTopicSubscription systemReplySub;

    /** 回复主题名称 */
    String replyTopic;

    /** 启动锁（双重检查锁定） */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 创建MQ远程客户端
     *
     * @param config 远程客户端配置
     */
    public MqRemoteClient(RemoteClientConfig config) {
        this.config = config;
        this.topic = config.getTopic();
        this.remoteId = config.getId();
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        lock.lock();
        try {
            if (started) {
                return;
            }
            // 从Runner获取分布式消息队列和回复订阅
            this.mq = Runner.getDistPubsub();
            this.systemReplySub = Runner.getSystemReplySub();
            if (this.systemReplySub == null) {
                throw new JiuWenBaseException(
                    StatusCode.RUNNER_DISTRIBUTED_MODE_REQUIRED.getCode(),
                    StatusCode.RUNNER_DISTRIBUTED_MODE_REQUIRED.getMessage());
            }
            this.replyTopic = this.systemReplySub.getTopic();
            this.started = true;
            logger.debug("[MqRemoteClient] init success topic: {}, reply_topic: {}", topic, replyTopic);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        started = false;
        logger.info("[MqRemoteClient] Stopped client for {}", remoteId);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Double timeout) throws TimeoutException {
        // 从conversation_id构建message_id
        Object convId = inputs.get("conversation_id");
        String sessionId = convId != null ? convId.toString() : "default_session";
        String messageId = sessionId + "_" + UUID.randomUUID();

        // 获取超时配置
        if (timeout == null) {
            timeout = RunnerConfig.getRunnerConfig().getDistributedConfig().getRequestTimeout();
        }
        if (timeout != null && timeout == 0.0) {
            timeout = null;
        }
        logger.info("[MqRemoteClient] Invoke {} with message_id: {}", remoteId, messageId);

        // 注册响应收集器
        ResponseCollector collector = systemReplySub.registerCollector(
            messageId, remoteId, null, timeout);
        logger.info("[MqRemoteClient] Register collector with message_id: {}, remote_id: {}", messageId, remoteId);

        // 构建请求消息
        DmqRequestMessage requestMsg = DmqRequestMessage.builder()
            .type(DMessageType.INPUT)
            .replyTopic(replyTopic)
            .messageId(messageId)
            .senderId(replyTopic)
            .receiverId(remoteId)
            .enableStream(false)
            .payload(inputs)
            .expireAt(timeout != null ? System.currentTimeMillis() / 1000.0 + timeout : null)
            .build();

        // 发送消息
        logger.info("[MqRemoteClient] Publishing to topic: {}, reply_topic: {}", topic, replyTopic);
        mq.produceMessage(topic, requestMsg).join();

        try {
            // 等待响应
            return collector.result(null);
        } catch (CancellationException e) {
            // 被取消时发送STOP消息
            logger.info("[MqRemoteClient] Invoke {} cancelled, sending STOP", messageId);
            sendStopMessage(messageId);
            throw e;
        } catch (TimeoutException e) {
            throw e;
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw e;
        } finally {
            systemReplySub.unregisterCollector(messageId, remoteId, null);
        }
    }

    @Override
    public List<Object> stream(Map<String, Object> inputs, Double timeout) throws TimeoutException {
        String messageId = UUID.randomUUID().toString();

        // 获取超时配置
        if (timeout == null) {
            timeout = RunnerConfig.getRunnerConfig().getDistributedConfig().getRequestTimeout();
        }
        if (timeout != null && timeout == 0.0) {
            timeout = null;
        }
        logger.info("[MqRemoteClient] Stream with message_id: {}", messageId);

        // 注册响应收集器
        ResponseCollector collector = systemReplySub.registerCollector(
            messageId, remoteId, null, timeout);

        // 构建请求消息
        DmqRequestMessage requestMsg = DmqRequestMessage.builder()
            .type(DMessageType.INPUT)
            .replyTopic(replyTopic)
            .messageId(messageId)
            .senderId(replyTopic)
            .receiverId(remoteId)
            .enableStream(true)
            .payload(inputs)
            .expireAt(timeout != null ? System.currentTimeMillis() / 1000.0 + timeout : null)
            .build();

        // 发送消息
        logger.info("[MqRemoteClient] Publishing to topic: {}", topic);
        mq.produceMessage(topic, requestMsg).join();

        try {
            return collector.stream(null);
        } catch (CancellationException e) {
            logger.info("[MqRemoteClient] Stream {} cancelled, sending STOP", messageId);
            sendStopMessage(messageId);
            throw e;
        } catch (TimeoutException e) {
            throw e;
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw e;
        } finally {
            systemReplySub.unregisterCollector(messageId, remoteId, null);
        }
    }

    /**
     * 发送STOP消息
     * 
     * <p>消息包含过期时间，超时时无需发送STOP，仅在提前关闭时发送。
     * 发送失败时捕获异常并记录日志，不向上抛出。
     *
     * @param messageId 消息ID
     */
    void sendStopMessage(String messageId) {
        try {
            DmqRequestMessage stopMsg = DmqRequestMessage.builder()
                .type(DMessageType.STOP)
                .payload(Map.of())
                .messageId(messageId)
                .senderId(replyTopic)
                .receiverId(remoteId)
                .expireAt(System.currentTimeMillis() / 1000.0
                    + RunnerConfig.getRunnerConfig().getDistributedConfig().getRequestTimeout())
                .build();
            mq.produceMessage(topic, stopMsg).join();
            logger.info("[MqRemoteClient] Sent STOP message for {}", messageId);
        } catch (Exception e) {
            logger.error("[MqRemoteClient] Failed to send STOP message: {}", e.getMessage());
        }
    }

    // Getters for testing/inspection

    public String getTopic() {
        return topic;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public RemoteClientConfig getConfig() {
        return config;
    }

    public boolean isStarted() {
        return started;
    }

    public MessageQueueBase getMq() {
        return mq;
    }

    public ReplyTopicSubscription getSystemReplySub() {
        return systemReplySub;
    }

    public String getReplyTopic() {
        return replyTopic;
    }
}

