/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Package facade metadata for the runner spawn module.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.runner.spawn} package facade in
 * {@code openjiuwen/core/runner/spawn/__init__.py}.</p>
 */
public final class SpawnPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/runner/spawn/__init__.py";

    private static final String PROTOCOL_SOURCE = "openjiuwen.core.runner.spawn.protocol.";
    private static final String AGENT_CONFIG_SOURCE = "openjiuwen.core.runner.spawn.agent_config.";
    private static final String CHILD_PROCESS_SOURCE = "openjiuwen.core.runner.spawn.child_process.";
    private static final String PROCESS_MANAGER_SOURCE = "openjiuwen.core.runner.spawn.process_manager.";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Message",
            "MessageType",
            "serialize_message",
            "deserialize_message",
            "serialize_message_to_stream",
            "deserialize_message_from_stream",
            "read_input_from_stdin",
            "write_output_to_stdout",
            "handle_health_check",
            "handle_shutdown",
            "execute_agent",
            "process_message_loop",
            "run_spawned_process",
            "ClassAgentSpawnConfig",
            "parse_spawn_agent_config",
            "SpawnAgentConfig",
            "SpawnAgentKind",
            "deserialize_runner_config",
            "serialize_runner_config",
            "SpawnConfig",
            "SpawnedProcessHandle",
            "spawn_process"
    );

    public static final Map<String, String> EXPORT_SOURCES = Map.ofEntries(
            Map.entry("Message", PROTOCOL_SOURCE + "Message"),
            Map.entry("MessageType", PROTOCOL_SOURCE + "MessageType"),
            Map.entry("serialize_message", PROTOCOL_SOURCE + "serialize_message"),
            Map.entry("deserialize_message", PROTOCOL_SOURCE + "deserialize_message"),
            Map.entry("serialize_message_to_stream", PROTOCOL_SOURCE + "serialize_message_to_stream"),
            Map.entry("deserialize_message_from_stream", PROTOCOL_SOURCE + "deserialize_message_from_stream"),
            Map.entry("read_input_from_stdin", CHILD_PROCESS_SOURCE + "read_input_from_stdin"),
            Map.entry("write_output_to_stdout", CHILD_PROCESS_SOURCE + "write_output_to_stdout"),
            Map.entry("handle_health_check", CHILD_PROCESS_SOURCE + "handle_health_check"),
            Map.entry("handle_shutdown", CHILD_PROCESS_SOURCE + "handle_shutdown"),
            Map.entry("execute_agent", CHILD_PROCESS_SOURCE + "execute_agent"),
            Map.entry("process_message_loop", CHILD_PROCESS_SOURCE + "process_message_loop"),
            Map.entry("run_spawned_process", CHILD_PROCESS_SOURCE + "run_spawned_process"),
            Map.entry("ClassAgentSpawnConfig", AGENT_CONFIG_SOURCE + "ClassAgentSpawnConfig"),
            Map.entry("parse_spawn_agent_config", AGENT_CONFIG_SOURCE + "parse_spawn_agent_config"),
            Map.entry("SpawnAgentConfig", AGENT_CONFIG_SOURCE + "SpawnAgentConfig"),
            Map.entry("SpawnAgentKind", AGENT_CONFIG_SOURCE + "SpawnAgentKind"),
            Map.entry("deserialize_runner_config", AGENT_CONFIG_SOURCE + "deserialize_runner_config"),
            Map.entry("serialize_runner_config", AGENT_CONFIG_SOURCE + "serialize_runner_config"),
            Map.entry("SpawnConfig", PROCESS_MANAGER_SOURCE + "SpawnConfig"),
            Map.entry("SpawnedProcessHandle", PROCESS_MANAGER_SOURCE + "SpawnedProcessHandle"),
            Map.entry("spawn_process", PROCESS_MANAGER_SOURCE + "spawn_process")
    );

    public static final Map<String, String> JAVA_SYMBOL_NAMES = Map.ofEntries(
            Map.entry("Message", "com.openjiuwen.core.runner.spawn.SpawnMessage"),
            Map.entry("MessageType", "com.openjiuwen.core.runner.spawn.SpawnMessageType"),
            Map.entry("serialize_message", "com.openjiuwen.core.runner.spawn.SpawnMessage#serializeMessage"),
            Map.entry("deserialize_message", "com.openjiuwen.core.runner.spawn.SpawnMessage#deserializeMessage"),
            Map.entry("serialize_message_to_stream",
                    "com.openjiuwen.core.runner.spawn.SpawnMessage#serializeMessageToStream"),
            Map.entry("deserialize_message_from_stream",
                    "com.openjiuwen.core.runner.spawn.SpawnMessage#deserializeMessageFromStream"),
            Map.entry("read_input_from_stdin",
                    "com.openjiuwen.core.runner.spawn.SpawnChildProcess#readInputFromStdin"),
            Map.entry("write_output_to_stdout",
                    "com.openjiuwen.core.runner.spawn.SpawnChildProcess#writeOutputToStdout"),
            Map.entry("handle_health_check",
                    "com.openjiuwen.core.runner.spawn.SpawnChildProcess#handleHealthCheck"),
            Map.entry("handle_shutdown",
                    "com.openjiuwen.core.runner.spawn.SpawnChildProcess#handleShutdown"),
            Map.entry("execute_agent",
                    "com.openjiuwen.core.runner.spawn.SpawnChildProcess#executeAgent"),
            Map.entry("process_message_loop",
                    "com.openjiuwen.core.runner.spawn.SpawnChildProcess#processMessageLoop"),
            Map.entry("run_spawned_process",
                    "com.openjiuwen.core.runner.spawn.SpawnChildProcess#runSpawnedProcess"),
            Map.entry("ClassAgentSpawnConfig", "com.openjiuwen.core.runner.spawn.ClassAgentSpawnConfig"),
            Map.entry("SpawnAgentConfig", "com.openjiuwen.core.runner.spawn.SpawnAgentConfig"),
            Map.entry("SpawnAgentKind", "com.openjiuwen.core.runner.spawn.SpawnAgentKind"),
            Map.entry("parse_spawn_agent_config",
                    "com.openjiuwen.core.runner.spawn.SpawnAgentConfigs#parseSpawnAgentConfig"),
            Map.entry("deserialize_runner_config",
                    "com.openjiuwen.core.runner.spawn.SpawnAgentConfigs#deserializeRunnerConfig"),
            Map.entry("serialize_runner_config",
                    "com.openjiuwen.core.runner.spawn.SpawnAgentConfigs#serializeRunnerConfig"),
            Map.entry("SpawnConfig", "com.openjiuwen.core.runner.spawn.SpawnConfig"),
            Map.entry("SpawnedProcessHandle", "com.openjiuwen.core.runner.spawn.SpawnedProcessHandle"),
            Map.entry("spawn_process", "com.openjiuwen.core.runner.spawn.SpawnProcesses#spawnProcess")
    );

    private SpawnPackage() {
    }

    /**
     * Mirrors Python's ordered {@code __all__} list.
     *
     * @return exported spawn symbol names
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether Python's spawn package exports a symbol.
     *
     * @param name attribute name
     * @return true when the attribute is in {@code __all__}
     */
    public static boolean exports(String name) {
        return EXPORTED_SYMBOLS.contains(name);
    }

    /**
     * Returns the dotted Python object imported by {@code spawn.__init__}.
     *
     * @param name attribute name
     * @return dotted Python source object, or null when absent
     */
    public static String sourceFor(String name) {
        return EXPORT_SOURCES.get(name);
    }

    /**
     * Returns the translated Java symbol name when that export is already translated.
     *
     * @param name attribute name
     * @return Java class or method symbol name, or null when pending
     */
    public static String javaSymbolNameFor(String name) {
        return JAVA_SYMBOL_NAMES.get(name);
    }

    /**
     * Checks whether a Python export already has a translated Java symbol.
     *
     * @param name attribute name
     * @return true when translated in the current Java tree
     */
    public static boolean translated(String name) {
        return JAVA_SYMBOL_NAMES.containsKey(name);
    }

    /**
     * Resolves translated class exports lazily.
     *
     * @param name attribute name
     * @return Java class for class-like exports, or empty for methods, missing names, and pending exports
     */
    public static Optional<Class<?>> resolveType(String name) {
        String javaType = JAVA_SYMBOL_NAMES.get(name);
        if (javaType == null || javaType.contains("#")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(javaType));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Resolves a translated Java symbol or raises a Python-like missing attribute error.
     *
     * @param name attribute name
     * @return Java symbol name
     */
    public static String getAttr(String name) {
        if (!exports(name)) {
            throw missingAttribute(name);
        }
        String javaSymbolName = javaSymbolNameFor(name);
        if (javaSymbolName == null) {
            throw new NoSuchElementException(
                    "module 'openjiuwen.core.runner.spawn' has no translated attribute '" + name + "'");
        }
        return javaSymbolName;
    }

    private static NoSuchElementException missingAttribute(String name) {
        return new NoSuchElementException(
                "module 'openjiuwen.core.runner.spawn' has no attribute '" + name + "'");
    }
}
