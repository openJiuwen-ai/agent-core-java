// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.multiagent.BaseGroup;
import com.openjiuwen.core.runner.drunner.dmessagequeue.MessageQueueFactory;
import com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteAgent;
import com.openjiuwen.core.runner.resourcesmanager.ResourceMgr;
import com.openjiuwen.core.session.AgentSessionWrapper;
import com.openjiuwen.core.session.SessionModule;
import com.openjiuwen.core.session.WorkflowSessionWrapper;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Runner主类 — 运行时引擎
 * 
 * <p>负责管理Agent、Workflow、Tool等资源，支持本地和分布式两种运行模式。
 * 
 * <p>注意：Python中Runner是单例实例（类变量被实例覆盖），Java中使用静态方法模式。
 * 
 * 对应Python: runner.py - Runner
 */
public class Runner {

    private static final Logger logger = LoggerFactory.getLogger(Runner.class);

    private static final String DEFAULT_RUNNER_ID = "global";
    private static final String DEFAULT_AGENT_SESSION_ID = "default_session";
    private static final String AGENT_CONVERSATION_ID = "conversation_id";

    // 核心组件
    private static ResourceMgr resourceMgr = new ResourceMgr();
    private static LocalMessageQueue messageQueue = new LocalMessageQueue();

    // 分布式组件
    private static MessageQueueBase distPubsub;
    private static ReplyTopicSubscription systemReplySub;

    private Runner() {
        // Utility class - prevent instantiation
    }

    // ==========================================
    // 内部记录类
    // ==========================================

    /**
     * 准备好的Agent执行上下文，包含agent实例和session。
     *
     * <p>对应Python: _prepare_agent返回的(agent_instance, agent_session)元组
     *
     * @param agent   agent实例（BaseAgent或RemoteAgent）
     * @param session agent session（RemoteAgent时为null）
     */
    public record PreparedAgent(Object agent, AgentSessionWrapper session) {}

    /**
     * 包装迭代器，在迭代完成后自动调用postRun。
     *
     * <p>对应Python: run_agent_streaming中async for结束后的post_run()调用
     */
    private static class PostRunIterator implements Iterator<Object> {
        private final Iterator<Object> delegate;
        private final AgentSessionWrapper session;
        private boolean postRunCalled = false;

        PostRunIterator(Iterator<Object> delegate, AgentSessionWrapper session) {
            this.delegate = delegate;
            this.session = session;
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext && !postRunCalled && session != null) {
                postRunCalled = true;
                try {
                    session.getInner().postRun().join();
                } catch (Exception e) {
                    logger.warn("[Runner] postRun failed: {}", e.getMessage());
                }
            }
            return hasNext;
        }

        @Override
        public Object next() {
            return delegate.next();
        }
    }

    // ==========================================
    // 属性访问
    // ==========================================

    /**
     * 获取资源管理器
     */
    public static ResourceMgr getResourceMgr() {
        return resourceMgr;
    }

    /**
     * 获取本地消息队列
     */
    public static LocalMessageQueue getPubsub() {
        return messageQueue;
    }

    /**
     * 获取分布式消息队列实例
     */
    public static MessageQueueBase getDistPubsub() {
        return distPubsub;
    }

    /**
     * 设置分布式消息队列实例（供测试使用）
     */
    public static void setDistPubsub(MessageQueueBase mq) {
        distPubsub = mq;
    }

    /**
     * 获取系统回复主题订阅
     */
    public static ReplyTopicSubscription getSystemReplySub() {
        return systemReplySub;
    }

    /**
     * 设置系统回复主题订阅（供测试使用）
     */
    public static void setSystemReplySub(ReplyTopicSubscription sub) {
        systemReplySub = sub;
    }

    /**
     * 设置Runner配置
     */
    public static void setConfig(RunnerConfig config) {
        RunnerConfig.setRunnerConfig(config);
    }

    /**
     * 获取Runner配置
     */
    public static RunnerConfig getConfig() {
        return RunnerConfig.getRunnerConfig();
    }

    // ==========================================
    // 生命周期管理
    // ==========================================

    /**
     * 启动Runner及其关联的组件
     *
     * @return 启动是否成功
     */
    public static boolean start() {
        logger.info("[Runner] Starting...");
        boolean result = true;
        RunnerConfig config = RunnerConfig.getRunnerConfig();
        if (config != null && config.isDistributedMode()) {
            try {
                // Start distributed message queue
                distPubsub = MessageQueueFactory.create(
                        config.getDistributedConfig().getMessageQueueConfig());
                distPubsub.start();

                // Start reply topic subscription (uses config-derived topic)
                systemReplySub = new ReplyTopicSubscription(distPubsub, null);
                systemReplySub.activate();

                messageQueue.start().join();
            } catch (Exception e) {
                logger.error("[Runner] Failed to start distributed components: {}", e.getMessage(), e);
                result = false;
            }
        }
        if (result) {
            logger.info("[Runner] Started.");
        } else {
            logger.error("[Runner] Start failed.");
        }
        return result;
    }

    /**
     * 停止Runner并清理资源
     *
     * @return 停止是否成功
     */
    public static boolean stop() {
        logger.info("[Runner] Stopping...");
        try {
            RunnerConfig config = RunnerConfig.getRunnerConfig();
            if (config != null && config.isDistributedMode()) {
                // 1. Stop ReplyTopicSubscription
                if (systemReplySub != null) {
                    systemReplySub.deactivate();
                    systemReplySub = null;
                }
                // 2. Stop distributed MQ
                if (distPubsub != null) {
                    distPubsub.stop();
                    distPubsub = null;
                }
            }

            messageQueue.stop().join();
            return true;
        } catch (Exception e) {
            logger.error("[Runner] Error during stop: {}", e.getMessage(), e);
            return false;
        } finally {
            resourceMgr.release();
            logger.info("[Runner] Stopped.");
        }
    }

    // ==========================================
    // Agent准备方法
    // ==========================================

    /**
     * 准备Agent执行上下文（从agent ID获取）。
     *
     * <p>对应Python: _prepare_agent(agent: str | BaseAgent, inputs, session)
     *
     * @param agentId agent ID
     * @param inputs  输入参数
     * @param session 可选的session ID
     * @return PreparedAgent包含agent实例和session
     */
    private static PreparedAgent prepareAgent(String agentId, Map<String, Object> inputs, String session) {
        // 获取session_id：优先从inputs中获取conversation_id，其次使用传入的session，最后使用默认值
        String sessionId = inputs.containsKey(AGENT_CONVERSATION_ID)
            ? (String) inputs.get(AGENT_CONVERSATION_ID)
            : (session != null ? session : DEFAULT_AGENT_SESSION_ID);

        // 从resource_mgr获取agent实例（通过ResourceMgr统一接口，与Python一致）
        Object agentInstance = resourceMgr.getAgent(agentId, null, null).join();
        if (agentInstance == null) {
            throw new JiuWenBaseException(
                StatusCode.AGENT_NOT_FOUND.getCode(),
                StatusCode.AGENT_NOT_FOUND.getMessage().replace("{}", agentId)
            );
        }

        // RemoteAgent不创建session
        if (agentInstance instanceof RemoteAgent) {
            if (!inputs.containsKey(AGENT_CONVERSATION_ID)) {
                inputs.put(AGENT_CONVERSATION_ID, sessionId);
            }
            return new PreparedAgent(agentInstance, null);
        }

        // 本地BaseAgent：创建session并调用checkpointer
        if (agentInstance instanceof BaseAgent baseAgent) {
            AgentCard card = baseAgent.getCard();
            AgentSessionWrapper taskSession = AgentSessionWrapper.createAgentSession(sessionId, null, card);

            // 调用checkpointer的preAgentExecute
            try {
                SessionModule.getDefaultInMemoryCheckpointer()
                    .preAgentExecute(taskSession.getInner().getInnerSession(), inputs)
                    .join();
            } catch (Exception e) {
                logger.warn("[Runner] preAgentExecute failed: {}", e.getMessage());
            }

            return new PreparedAgent(baseAgent, taskSession);
        }

        throw new UnsupportedOperationException(
            "Runner.prepareAgent: unknown agent type: " + agentInstance.getClass().getName());
    }

    /**
     * 准备Agent执行上下文（直接传入agent实例）。
     *
     * <p>对应Python: _prepare_agent(agent: BaseAgent, inputs, session)
     *
     * @param agent   BaseAgent实例
     * @param inputs  输入参数
     * @param session 可选的session ID
     * @return PreparedAgent包含agent实例和session
     */
    private static PreparedAgent prepareAgent(BaseAgent agent, Map<String, Object> inputs, String session) {
        // 获取session_id
        String sessionId = inputs.containsKey(AGENT_CONVERSATION_ID)
            ? (String) inputs.get(AGENT_CONVERSATION_ID)
            : (session != null ? session : DEFAULT_AGENT_SESSION_ID);

        AgentCard card = agent.getCard();
        AgentSessionWrapper taskSession = AgentSessionWrapper.createAgentSession(sessionId, null, card);

        // 调用checkpointer的preAgentExecute
        try {
            SessionModule.getDefaultInMemoryCheckpointer()
                .preAgentExecute(taskSession.getInner().getInnerSession(), inputs)
                .join();
        } catch (Exception e) {
            logger.warn("[Runner] preAgentExecute failed: {}", e.getMessage());
        }

        return new PreparedAgent(agent, taskSession);
    }

    // ==========================================
    // Workflow 准备方法
    // ==========================================

    /**
     * 判断当前是否由Agent调用。
     *
     * <p>对应Python: _is_called_by_agent(session)
     *
     * @param session session对象
     * @return 如果session是AgentSessionWrapper则返回true
     */
    private static boolean isCalledByAgent(Object session) {
        return session instanceof AgentSessionWrapper;
    }

    /**
     * 创建WorkflowSession。
     *
     * <p>对应Python: _create_workflow_session(session)
     * 根据session类型进行不同处理：
     * - null: 创建新的WorkflowSessionWrapper
     * - String: 以sessionId创建WorkflowSessionWrapper
     * - AgentSessionWrapper: 从AgentSession创建WorkflowSession
     * - WorkflowSessionWrapper: 直接返回
     *
     * @param session session对象（可以是null, String, AgentSessionWrapper, WorkflowSessionWrapper）
     * @return WorkflowSessionWrapper实例
     */
    private static WorkflowSessionWrapper createWorkflowSession(Object session) {
        if (session == null) {
            return new WorkflowSessionWrapper();
        } else if (session instanceof String sessionId) {
            return new WorkflowSessionWrapper(sessionId);
        } else if (session instanceof AgentSessionWrapper agentSession) {
            return agentSession.createWorkflowSession();
        } else if (session instanceof WorkflowSessionWrapper workflowSession) {
            return workflowSession;
        }
        return new WorkflowSessionWrapper();
    }

    // ==========================================
    // Workflow 执行
    // ==========================================

    /**
     * 执行workflow（非流式）
     *
     * <p>对应Python: run_workflow(workflow, inputs, *, session, context, envs)
     *
     * @param workflowId Workflow ID
     * @param inputs     输入参数
     * @return 执行结果
     */
    public static Object runWorkflow(String workflowId, Object inputs) {
        return runWorkflow(workflowId, inputs, null);
    }

    /**
     * 执行workflow（非流式，带session参数）
     *
     * <p>对应Python: run_workflow(workflow, inputs, *, session, context, envs)
     *
     * @param workflowId Workflow ID
     * @param inputs     输入参数
     * @param session    可选的session（String sessionId 或 WorkflowSessionWrapper 或 AgentSessionWrapper）
     * @return 执行结果
     */
    public static Object runWorkflow(String workflowId, Object inputs, Object session) {
        // TODO: 完善Workflow执行逻辑 (依赖Workflow类完整转换)
        // Python实现:
        //   workflow_instance, workflow_session = await self._prepare_workflow(workflow, session)
        //   return await workflow_instance.invoke(inputs, session=workflow_session, context=context)
        throw new UnsupportedOperationException("Runner.runWorkflow not yet fully implemented (requires Workflow class)");
    }

    /**
     * 执行workflow（流式）
     *
     * <p>对应Python: run_workflow_streaming(workflow, inputs, *, session, context, stream_modes, envs)
     *
     * @param workflowId  Workflow ID
     * @param inputs      输入参数
     * @return 流式结果迭代器
     */
    public static Iterator<Object> runWorkflowStreaming(String workflowId, Object inputs) {
        return runWorkflowStreaming(workflowId, inputs, null);
    }

    /**
     * 执行workflow（流式，带session参数）
     *
     * <p>对应Python: run_workflow_streaming(workflow, inputs, *, session, context, stream_modes, envs)
     *
     * @param workflowId  Workflow ID
     * @param inputs      输入参数
     * @param session     可选的session（String sessionId 或 WorkflowSessionWrapper 或 AgentSessionWrapper）
     * @return 流式结果迭代器
     */
    public static Iterator<Object> runWorkflowStreaming(String workflowId, Object inputs, Object session) {
        // TODO: 完善Workflow流式执行逻辑 (依赖Workflow类完整转换)
        // Python实现:
        //   workflow_instance, workflow_session = await self._prepare_workflow(workflow, session)
        //   async for chunk in workflow_instance.stream(inputs, session=workflow_session,
        //                                               stream_modes=stream_modes, context=context):
        //       yield chunk
        throw new UnsupportedOperationException("Runner.runWorkflowStreaming not yet fully implemented (requires Workflow class)");
    }

    // ==========================================
    // Agent 执行
    // ==========================================

    /**
     * 运行Agent（非流式）
     *
     * <p>对应Python: run_agent(agent: str, inputs, *, session, context, envs)
     * <p>注意：Python版本中session参数不传给_prepare_agent
     *
     * @param agentId Agent ID或agent实例标识
     * @param inputs  输入参数
     * @return 执行结果
     */
    public static Object runAgent(String agentId, Map<String, Object> inputs) {
        return runAgent(agentId, inputs, (String) null);
    }

    /**
     * 运行Agent（非流式，带session参数）
     *
     * <p>对应Python: run_agent(agent: str, inputs, *, session, context, envs)
     * <p>注意：Python版本中run_agent的session参数不传递给_prepare_agent
     *
     * @param agentId Agent ID
     * @param inputs  输入参数
     * @param session 可选的session ID
     * @return 执行结果
     */
    public static Object runAgent(String agentId, Map<String, Object> inputs, String session) {
        try {
            // 准备agent执行上下文（Python run_agent不传session给_prepare_agent）
            PreparedAgent prepared = prepareAgent(agentId, inputs, null);

            if (prepared.agent() instanceof RemoteAgent remoteAgent) {
                // RemoteAgent直接调用，不管理session
                return remoteAgent.invoke(inputs, null);
            }

            if (prepared.agent() instanceof BaseAgent baseAgent) {
                // 本地BaseAgent：使用session调用invoke，然后postRun
                Object result = baseAgent.invoke(inputs, prepared.session() != null ? prepared.session().getInner() : null).join();

                // 执行后调用postRun
                if (prepared.session() != null) {
                    prepared.session().getInner().postRun().join();
                }

                return result;
            }

            throw new UnsupportedOperationException(
                "Runner.runAgent: unknown agent type: " + prepared.agent().getClass().getName());
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 运行Agent（流式）
     *
     * <p>对应Python: run_agent_streaming(agent: str, inputs, *, session, context, stream_modes, envs)
     * <p>注意：Python版本中run_agent_streaming的session参数传给_prepare_agent
     *
     * @param agentId Agent ID
     * @param inputs  输入参数
     * @return 流式结果迭代器
     */
    public static Iterator<Object> runAgentStreaming(String agentId, Map<String, Object> inputs) {
        return runAgentStreaming(agentId, inputs, (String) null);
    }

    /**
     * 运行Agent（流式，带session参数）
     *
     * <p>对应Python: run_agent_streaming(agent: str, inputs, *, session, context, stream_modes, envs)
     * <p>注意：Python版本中run_agent_streaming的session参数传给_prepare_agent
     *
     * @param agentId Agent ID
     * @param inputs  输入参数
     * @param session 可选的session ID
     * @return 流式结果迭代器
     */
    public static Iterator<Object> runAgentStreaming(String agentId, Map<String, Object> inputs, String session) {
        try {
            // 准备agent执行上下文（Python run_agent_streaming传session给_prepare_agent）
            PreparedAgent prepared = prepareAgent(agentId, inputs, session);

            if (prepared.agent() instanceof RemoteAgent remoteAgent) {
                // RemoteAgent直接调用
                return remoteAgent.stream(inputs, null).iterator();
            }

            if (prepared.agent() instanceof BaseAgent baseAgent) {
                // 本地BaseAgent：使用session调用stream
                Iterator<Object> result = baseAgent.stream(inputs, prepared.session() != null ? prepared.session().getInner() : null, null).join();

                // 返回包装的迭代器，在迭代完成后调用postRun
                return new PostRunIterator(result, prepared.session());
            }

            throw new UnsupportedOperationException(
                "Runner.runAgentStreaming: unknown agent type: " + prepared.agent().getClass().getName());
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 运行Agent（非流式）— 直接传入 Agent 实例。
     *
     * <p>对齐 Python: {@code Runner.run_agent(agent=react_agent, inputs=...)}
     *
     * @param agent  BaseAgent 实例
     * @param inputs 输入参数
     * @return 执行结果
     */
    public static Object runAgent(BaseAgent agent, Map<String, Object> inputs) {
        return runAgent(agent, inputs, (String) null);
    }

    /**
     * 运行Agent（非流式，带session参数）— 直接传入 Agent 实例。
     *
     * <p>对齐 Python: {@code Runner.run_agent(agent=react_agent, inputs=..., session=...)}
     *
     * @param agent   BaseAgent 实例
     * @param inputs  输入参数
     * @param session 可选的session ID
     * @return 执行结果
     */
    public static Object runAgent(BaseAgent agent, Map<String, Object> inputs, String session) {
        if (agent == null) {
            throw new JiuWenBaseException(StatusCode.AGENT_NOT_FOUND.getCode(), "Agent instance is null");
        }
        try {
            // 准备agent执行上下文（Python run_agent不传session给_prepare_agent）
            PreparedAgent prepared = prepareAgent(agent, inputs, null);

            // 使用session调用invoke
            Object result = agent.invoke(inputs, prepared.session() != null ? prepared.session().getInner() : null).join();

            // 执行后调用postRun
            if (prepared.session() != null) {
                prepared.session().getInner().postRun().join();
            }

            return result;
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 运行Agent（流式）— 直接传入 Agent 实例。
     *
     * <p>对齐 Python: {@code async for chunk in Runner.run_agent_streaming(agent=react_agent, inputs=...)}
     *
     * @param agent  BaseAgent 实例
     * @param inputs 输入参数
     * @return 流式结果迭代器
     */
    public static Iterator<Object> runAgentStreaming(BaseAgent agent, Map<String, Object> inputs) {
        return runAgentStreaming(agent, inputs, (String) null);
    }

    /**
     * 运行Agent（流式，带session参数）— 直接传入 Agent 实例。
     *
     * <p>对齐 Python: {@code async for chunk in Runner.run_agent_streaming(agent=react_agent, inputs=..., session=...)}
     *
     * @param agent   BaseAgent 实例
     * @param inputs  输入参数
     * @param session 可选的session ID
     * @return 流式结果迭代器
     */
    public static Iterator<Object> runAgentStreaming(BaseAgent agent, Map<String, Object> inputs, String session) {
        if (agent == null) {
            throw new JiuWenBaseException(StatusCode.AGENT_NOT_FOUND.getCode(), "Agent instance is null");
        }
        try {
            // 准备agent执行上下文（Python run_agent_streaming传session给_prepare_agent）
            PreparedAgent prepared = prepareAgent(agent, inputs, session);

            // 使用session调用stream
            Iterator<Object> result = agent.stream(inputs, prepared.session() != null ? prepared.session().getInner() : null, null).join();

            // 返回包装的迭代器，在迭代完成后调用postRun
            return new PostRunIterator(result, prepared.session());
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
        }
    }

    // ==========================================
    // AgentGroup 准备方法
    // ==========================================

    /**
     * 准备AgentGroup执行上下文。
     *
     * <p>对应Python: _prepare_agent_group(agent_group: str | BaseGroup)
     *
     * @param groupId AgentGroup ID
     * @return BaseGroup实例
     */
    private static BaseGroup prepareAgentGroup(String groupId) {
        Object groupInstance = resourceMgr.getAgentGroup(groupId, null, null).join();
        if (groupInstance == null) {
            throw new JiuWenBaseException(
                StatusCode.AGENT_NOT_FOUND.getCode(),
                "AgentGroup not found: " + groupId
            );
        }
        if (groupInstance instanceof BaseGroup baseGroup) {
            return baseGroup;
        }
        throw new UnsupportedOperationException(
            "Runner.prepareAgentGroup: unknown group type: " + groupInstance.getClass().getName());
    }

    // ==========================================
    // AgentGroup 执行
    // ==========================================

    /**
     * 执行AgentGroup（非流式）— 通过 group ID。
     *
     * <p>对应Python: run_agent_group(agent_group: str, inputs, *, session, context, envs)
     *
     * @param groupId AgentGroup ID
     * @param inputs  输入参数
     * @return 执行结果
     */
    public static Object runAgentGroup(String groupId, Object inputs) {
        try {
            BaseGroup groupInstance = prepareAgentGroup(groupId);
            return groupInstance.invoke(inputs, null).join();
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 执行AgentGroup（非流式）— 直接传入 BaseGroup 实例。
     *
     * <p>对应Python: run_agent_group(agent_group: BaseGroup, inputs, *, session, context, envs)
     *
     * @param agentGroup BaseGroup 实例
     * @param inputs     输入参数
     * @return 执行结果
     */
    public static Object runAgentGroup(BaseGroup agentGroup, Object inputs) {
        if (agentGroup == null) {
            throw new JiuWenBaseException(StatusCode.AGENT_NOT_FOUND.getCode(), "AgentGroup instance is null");
        }
        try {
            return agentGroup.invoke(inputs, null).join();
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 执行AgentGroup（流式）— 通过 group ID。
     *
     * <p>对应Python: run_agent_group_streaming(agent_group: str, inputs, *, session, context, stream_modes, envs)
     *
     * @param groupId AgentGroup ID
     * @param inputs  输入参数
     * @return 流式结果迭代器
     */
    public static Iterator<Object> runAgentGroupStreaming(String groupId, Object inputs) {
        try {
            BaseGroup groupInstance = prepareAgentGroup(groupId);
            Stream<Object> stream = groupInstance.stream(inputs, null).join();
            return stream.iterator();
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 执行AgentGroup（流式）— 直接传入 BaseGroup 实例。
     *
     * <p>对应Python: run_agent_group_streaming(agent_group: BaseGroup, inputs, *, session, context, stream_modes, envs)
     *
     * @param agentGroup BaseGroup 实例
     * @param inputs     输入参数
     * @return 流式结果迭代器
     */
    public static Iterator<Object> runAgentGroupStreaming(BaseGroup agentGroup, Object inputs) {
        if (agentGroup == null) {
            throw new JiuWenBaseException(StatusCode.AGENT_NOT_FOUND.getCode(), "AgentGroup instance is null");
        }
        try {
            Stream<Object> stream = agentGroup.stream(inputs, null).join();
            return stream.iterator();
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            throw new JiuWenBaseException(StatusCode.ERROR.getCode(), e.getMessage());
        }
    }

    // ==========================================
    // 工具方法
    // ==========================================

    /**
     * 释放与session关联的资源
     *
     * <p>对应Python: await get_default_inmemory_checkpointer().release(session_id)
     *
     * @param sessionId session ID
     */
    public static void release(String sessionId) {
        SessionModule.getDefaultInMemoryCheckpointer().release(sessionId).join();
    }

    /**
     * 重置Runner状态（主要用于测试）
     */
    public static void reset() {
        resourceMgr = new ResourceMgr();
        messageQueue = new LocalMessageQueue();
        distPubsub = null;
        systemReplySub = null;
        RunnerConfig.setRunnerConfig(null);
    }
}
