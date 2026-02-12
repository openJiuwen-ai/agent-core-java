// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.serveradapter;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

/**
 * Agent适配器
 * 
 * <p>封装 {@link MqServerAdapter}，将MQ消息路由到 Runner.runAgent / Runner.runAgentStreaming。
 * 
 * 对应Python: drunner/server_adapter/agent_adapter.py - AgentAdapter
 */
public class AgentAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AgentAdapter.class);

    private final String agentId;
    private final String version;
    private final String topic;
    private final MqServerAdapter server;

    public AgentAdapter(String agentId) {
        this(agentId, "");
    }

    public AgentAdapter(String agentId, String version) {
        this.agentId = agentId;
        this.version = version;
        this.topic = RunnerConfig.getRunnerConfig().agentTopicTemplate()
                .replace("{agent_id}", agentId)
                .replace("{version}", version);

        this.server = new MqServerAdapter(
                agentId,
                this.topic,
                this::handleInvoke,
                this::handleStream
        );
    }

    /**
     * 启动适配器
     */
    public void start() {
        server.start();
    }

    /**
     * 停止适配器
     */
    public void stop() {
        server.stop();
    }

    /**
     * 处理非流式请求：调用Runner.runAgent
     */
    @SuppressWarnings("unchecked")
    private Object handleInvoke(Map<String, Object> inputs) {
        return Runner.runAgent(agentId, inputs);
    }

    /**
     * 处理流式请求：调用Runner.runAgentStreaming
     */
    @SuppressWarnings("unchecked")
    private Iterator<Object> handleStream(Map<String, Object> inputs) {
        return Runner.runAgentStreaming(agentId, inputs);
    }

    public String getAgentId() {
        return agentId;
    }

    public String getVersion() {
        return version;
    }

    public String getTopic() {
        return topic;
    }

    public MqServerAdapter getServer() {
        return server;
    }
}
