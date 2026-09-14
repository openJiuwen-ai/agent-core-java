/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Small reflection bridge for runner configuration values used by remote clients.
 *
 * <p>Mirrors Python's calls to {@code get_runner_config()} in
 * {@code openjiuwen/core/runner/drunner/remote_client/mq_remote_clent.py} and
 * {@code openjiuwen/core/runner/drunner/remote_client/remote_agent.py}.</p>
 */
final class RunnerConfigAccess {

    private static final double DEFAULT_REQUEST_TIMEOUT = 30.0d;
    private static final String DEFAULT_AGENT_TOPIC_TEMPLATE = "openjiuwen.single_agent.{agent_id}.{version}";

    private RunnerConfigAccess() {
    }

    static double requestTimeout() {
        Object config = runnerConfig();
        Object distributedConfig = readProperty(config, "getDistributedConfig", "distributedConfig");
        Object value = readProperty(distributedConfig, "getRequestTimeout", "requestTimeout");
        return value instanceof Number number ? number.doubleValue() : DEFAULT_REQUEST_TIMEOUT;
    }

    static String agentTopic(String agentId, String version) {
        Object config = runnerConfig();
        String template = invokeString(config, "agentTopicTemplate");
        if (template == null || template.isBlank()) {
            Object distributedConfig = readProperty(config, "getDistributedConfig", "distributedConfig");
            template = invokeString(distributedConfig, "getAgentTopicTemplate", "");
        }
        if (template == null || template.isBlank()) {
            template = DEFAULT_AGENT_TOPIC_TEMPLATE;
        }
        return template.replace("{agent_id}", agentId)
                .replace("{version}", version == null ? "" : version)
                .replace("{instance_id}", instanceId(config));
    }

    private static Object runnerConfig() {
        try {
            Class<?> runnerConfigClass = Class.forName("com.openjiuwen.core.runner.RunnerConfig");
            Method method = runnerConfigClass.getMethod("getRunnerConfig");
            return method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String instanceId(Object config) {
        Object value = readProperty(config, "getInstanceId", "instanceId");
        return value == null ? UUID.randomUUID().toString() : String.valueOf(value);
    }

    private static String invokeString(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                try {
                    Object result = method.invoke(target, args);
                    return result == null ? null : String.valueOf(result);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Object readProperty(Object target, String getterName, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
