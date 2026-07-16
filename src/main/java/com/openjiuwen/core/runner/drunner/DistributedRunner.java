/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageQueueFactory;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.mq.MessageQueueBase;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight runtime holder for distributed-runner components.
 * 
 * @since 0.1.7
 */
public final class DistributedRunner {
    private static final AtomicReference<MessageQueueBase> MQ = new AtomicReference<>();

    /**
     * AtomicReference<>.
     * 
     * @since 0.1.7
     */
    private static final AtomicReference<ReplyTopicSubscription> REPLY_SUBSCRIPTION = new AtomicReference<>();

    /**
     * DistributedRunner.
     * 
     * @since 0.1.7
     */
    private DistributedRunner() {
    }

    /**
     * ensureStarted.
     * 
     * @since 0.1.7
     */
    public static synchronized void ensureStarted() {
        if (MQ.get() != null && REPLY_SUBSCRIPTION.get() != null) {
            return;
        }
        MessageQueueBase messageQueue =
            MessageQueueFactory.create(RunnerConfig.getRunnerConfig().getDistributedConfig().getMessageQueueConfig());
        messageQueue.start();
        MQ.set(messageQueue);

        ReplyTopicSubscription replyTopicSubscription = new ReplyTopicSubscription(messageQueue, replyTopic());
        replyTopicSubscription.activate();
        REPLY_SUBSCRIPTION.set(replyTopicSubscription);
    }

    /**
     * messageQueue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static MessageQueueBase messageQueue() {
        ensureStarted();
        return MQ.get();
    }

    /**
     * replySubscription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static ReplyTopicSubscription replySubscription() {
        ensureStarted();
        return REPLY_SUBSCRIPTION.get();
    }

    /**
     * shutdown.
     * 
     * @since 0.1.7
     */
    public static synchronized void shutdown() {
        ReplyTopicSubscription replyTopicSubscription = REPLY_SUBSCRIPTION.getAndSet(null);
        if (replyTopicSubscription != null) {
            replyTopicSubscription.deactivate();
        }
        MessageQueueBase messageQueue = MQ.getAndSet(null);
        if (messageQueue != null) {
            messageQueue.stop();
        }
    }

    /**
     * replyTopic.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String replyTopic() {
        RunnerConfig config = RunnerConfig.getRunnerConfig();
        return config.replyTopicTemplate().replace("{instance_id}", config.getInstanceId());
    }

    /**
     * agentTopic.
     * 
     * @param agentId agentId
     * @param version version
     * @return the result
     * @since 0.1.7
     */
    public static String agentTopic(String agentId, String version) {
        RunnerConfig config = RunnerConfig.getRunnerConfig();
        return config.agentTopicTemplate().replace("{agent_id}", agentId != null ? agentId : "").replace("{version}",
                version != null ? version : "");
    }
}
