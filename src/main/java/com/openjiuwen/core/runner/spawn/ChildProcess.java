/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.runner.Runner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * ChildProcess.
 * 
 * @since 0.1.7
 */
public final class ChildProcess {
    /**
     * ChildProcess.
     * 
     * @since 0.1.7
     */
    private ChildProcess() {
    }

    /**
     * main.
     * 
     * @param args args
     * @throws Exception Exception
     * @since 0.1.7
     */
    public static void main(String[] args) throws Exception {
        PrintStream protocolOut = System.out;
        if ("1".equals(System.getenv("OPENJIUWEN_SPAWN_PROCESS"))) {
            System.setOut(System.err);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(protocolOut, StandardCharsets.UTF_8));
        Object writerLock = new Object();
        ExecutorService agentExecutor = OpenJiuwenExecutors.newSingleThreadExecutor("runner-spawn-agent-task", true);
        Future<?> agentTask = null;
        Runner.start();
        try {
            Message message;
            while ((message = MessageProtocol.deserializeMessageFromStream(reader)) != null) {
                if (message.getType() == MessageType.HEALTH_CHECK) {
                    writeMessage(
                            Message.builder().type(MessageType.HEALTH_CHECK_RESPONSE)
                                    .payload(Map.of("status", "healthy")).messageId(message.getMessageId()).build(),
                            writer, writerLock);
                    continue;
                }
                if (message.getType() == MessageType.SHUTDOWN) {
                    if (agentTask != null && !agentTask.isDone()) {
                        agentTask.cancel(true);
                    }
                    writeMessage(Message.builder().type(MessageType.SHUTDOWN_ACK).payload(Map.of())
                            .messageId(message.getMessageId()).build(), writer, writerLock);
                    return;
                }
                if (message.getType() == MessageType.INPUT) {
                    // Only start work when no agent task is active.
                    if (agentTask == null || agentTask.isDone()) {
                        Message inputMessage = message;
                        agentTask = agentExecutor.submit(() -> runInput(inputMessage, writer, writerLock));
                    }
                }
            }
        } finally {
            agentExecutor.shutdownNow();
            Runner.stop();
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * runInput.
     * 
     * @param message message
     * @param writer writer
     * @param writerLock writerLock
     * @since 0.1.7
     */
    private static void runInput(Message message, BufferedWriter writer, Object writerLock) {
        Map<String, Object> payload =
            message.getPayload() instanceof Map<?, ?> rawPayload ? stringifyMap(rawPayload) : Map.of();
        try {
            Object rawAgentConfig = payload.get("agent_config");
            SpawnAgentConfig agentConfig = rawAgentConfig instanceof Map<?, ?> rawConfig
                    ? SpawnAgentConfigs.parseSpawnAgentConfig(stringifyMap(rawConfig))
                    : null;
            Map<String, Object> inputs =
                payload.get("inputs") instanceof Map<?, ?> rawInputs ? stringifyMap(rawInputs) : Map.of();
            boolean isStreaming = Boolean.TRUE.equals(payload.get("streaming"));
            Object result =
                isStreaming ? executeStreaming(agentConfig, inputs, writer, writerLock) : execute(agentConfig, inputs);
            writeMessage(Message.builder().type(MessageType.DONE)
                    .payload(Map.of("result", result != null ? result : Map.of())).messageId(message.getMessageId())
                    .build(), writer, writerLock);
        } catch (Exception exception) {
            writeMessage(Message.builder().type(MessageType.ERROR)
                    .payload(Map.of("error", exception.getMessage() != null ? exception.getMessage() : "", "error_type",
                            exception.getClass().getSimpleName()))
                    .messageId(message.getMessageId()).build(), writer, writerLock);
        }
    }

    /**
     * execute.
     * 
     * @param agentConfig agentConfig
     * @param inputs inputs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private static Object execute(SpawnAgentConfig agentConfig, Map<String, Object> inputs) throws Exception {
        if (agentConfig == null) {
            throw new IllegalArgumentException("Missing agent_config in child process input message.");
        }
        Object session = agentConfig.getSessionId();
        if (agentConfig.getRunnerConfig() != null) {
            Runner.setConfig(agentConfig.getRunnerConfig());
        }
        if (agentConfig.getAgentKind() == SpawnAgentKind.CLASS_AGENT) {
            if (!(agentConfig instanceof ClassAgentSpawnConfig classConfig)) {
                throw new IllegalArgumentException("agent_config kind CLASS_AGENT requires ClassAgentSpawnConfig");
            }
            Class<?> agentClass = Class.forName(resolveClassName(classConfig));
            Object agent = instantiate(agentClass, classConfig.getInitKwargs());
            return Runner.runAgent(agent, inputs, session, null);
        }
        if (agentConfig.getAgentKind() == SpawnAgentKind.TEAM_AGENT) {
            TeamAgent agent = TeamAgent.fromSpawnPayload(agentConfig.getPayload());
            Object query = inputs.getOrDefault("query", inputs.getOrDefault("data", ""));
            return agent.dispatchTask(String.valueOf(query));
        }
        throw new IllegalArgumentException("Unsupported spawned agent kind: " + agentConfig.getAgentKind());
    }

    /**
     * executeStreaming.
     * 
     * @param agentConfig agentConfig
     * @param inputs inputs
     * @param writer writer
     * @param writerLock writerLock
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private static Object executeStreaming(SpawnAgentConfig agentConfig, Map<String, Object> inputs,
            BufferedWriter writer, Object writerLock) throws Exception {
        if (agentConfig == null) {
            throw new IllegalArgumentException("Missing agent_config in child process input message.");
        }
        Object session = agentConfig.getSessionId();
        if (agentConfig.getRunnerConfig() != null) {
            Runner.setConfig(agentConfig.getRunnerConfig());
        }
        if (agentConfig.getAgentKind() == SpawnAgentKind.CLASS_AGENT) {
            if (!(agentConfig instanceof ClassAgentSpawnConfig classConfig)) {
                throw new IllegalArgumentException("agent_config kind CLASS_AGENT requires ClassAgentSpawnConfig");
            }
            Class<?> agentClass = Class.forName(resolveClassName(classConfig));
            Object agent = instantiate(agentClass, classConfig.getInitKwargs());
            Iterator<Object> iterator = Runner.runAgentStreaming(agent, inputs, session, null, null);
            List<Object> chunks = new ArrayList<>();
            while (iterator.hasNext()) {
                Object chunk = iterator.next();
                chunks.add(chunk);
                writeMessage(Message.builder().type(MessageType.STREAM_CHUNK).payload(chunk).build(), writer,
                        writerLock);
            }
            return chunks;
        }
        if (agentConfig.getAgentKind() == SpawnAgentKind.TEAM_AGENT) {
            TeamAgent agent = TeamAgent.fromSpawnPayload(agentConfig.getPayload());
            Iterator<Object> iterator = Runner.runAgentGroupStreaming(agent, inputs, session, null, null);
            List<Object> chunks = new ArrayList<>();
            while (iterator.hasNext()) {
                Object chunk = iterator.next();
                chunks.add(chunk);
                writeMessage(Message.builder().type(MessageType.STREAM_CHUNK).payload(chunk).build(), writer,
                        writerLock);
            }
            return chunks;
        }
        return execute(agentConfig, inputs);
    }

    /**
     * writeMessage.
     * 
     * @param message message
     * @param writer writer
     * @param writerLock writerLock
     * @since 0.1.7
     */
    private static void writeMessage(Message message, BufferedWriter writer, Object writerLock) {
        synchronized (writerLock) {
            try {
                MessageProtocol.serializeMessageToStream(message, writer);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write spawn message", exception);
            }
        }
    }

    /**
     * instantiate.
     * 
     * @param agentClass agentClass
     * @param initKwargs initKwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private static Object instantiate(Class<?> agentClass, Map<String, Object> initKwargs) throws Exception {
        if (initKwargs == null || initKwargs.isEmpty()) {
            Constructor<?> constructor = agentClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
        try {
            Constructor<?> constructor = agentClass.getDeclaredConstructor(Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(initKwargs);
        } catch (NoSuchMethodException ignored) {
            Constructor<?> constructor = agentClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
    }

    /**
     * resolveClassName.
     * 
     * @param classConfig classConfig
     * @return the result
     * @since 0.1.7
     */
    private static String resolveClassName(ClassAgentSpawnConfig classConfig) {
        String agentClass = classConfig.getAgentClass();
        String module = classConfig.getAgentModule();
        if (agentClass != null && agentClass.contains(".")) {
            return agentClass;
        }
        if (module == null || module.isBlank()) {
            return agentClass;
        }
        return module + "." + agentClass;
    }

    /**
     * stringifyMap.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> stringifyMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
