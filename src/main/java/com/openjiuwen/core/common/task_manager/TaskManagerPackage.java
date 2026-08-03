/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for coroutine task manager exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.common.task_manager} package facade in
 * {@code openjiuwen/core/common/task_manager/__init__.py}.</p>
 */
public final class TaskManagerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/common/task_manager/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "TaskManager",
            "Task",
            "TaskStatus",
            "TERMINAL_STATES",
            "TaskError",
            "TaskNotFoundError",
            "DuplicateTaskError",
            "get_task_manager",
            "create_task",
            "cancel_group",
            "cancel_all",
            "print_task_tree",
            "get_task_group",
            "set_task_group",
            "get_current_task_id",
            "TaskManagerEvents"
    );

    public static final List<String> MODULE_SYMBOLS = List.of(
            "TaskManager",
            "Task",
            "TaskStatus",
            "TERMINAL_STATES",
            "TaskError",
            "TaskNotFoundError",
            "DuplicateTaskError",
            "get_task_manager",
            "create_task",
            "cancel_group",
            "cancel_all",
            "print_task_tree",
            "get_task_group",
            "set_task_group",
            "reset_task_group",
            "get_current_task_id",
            "TaskManagerEvents"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private TaskManagerPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether a symbol is re-exported by Python {@code __all__}.
     *
     * @param symbolName symbol name
     * @return {@code true} when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Checks whether a symbol is imported into the Python module namespace.
     *
     * @param symbolName symbol name
     * @return {@code true} when direct module access exposes the symbol
     */
    public static boolean importsSymbol(String symbolName) {
        return MODULE_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the Java type name expected to mirror the Python object.
     *
     * @param symbolName symbol name
     * @return fully qualified Java type name, or {@code null} when absent
     */
    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("TaskManager", "openjiuwen.core.common.task_manager.manager.TaskManager");
        sources.put("Task", "openjiuwen.core.common.task_manager.task.Task");
        sources.put("TaskStatus", "openjiuwen.core.common.task_manager.types.TaskStatus");
        sources.put("TERMINAL_STATES", "openjiuwen.core.common.task_manager.types.TERMINAL_STATES");
        sources.put("TaskError", "openjiuwen.core.common.task_manager.exceptions.TaskError");
        sources.put("TaskNotFoundError", "openjiuwen.core.common.task_manager.exceptions.TaskNotFoundError");
        sources.put("DuplicateTaskError", "openjiuwen.core.common.task_manager.exceptions.DuplicateTaskError");
        sources.put("get_task_manager", "openjiuwen.core.common.task_manager.manager.get_task_manager");
        sources.put("create_task", "openjiuwen.core.common.task_manager.manager.create_task");
        sources.put("cancel_group", "openjiuwen.core.common.task_manager.manager.cancel_group");
        sources.put("cancel_all", "openjiuwen.core.common.task_manager.manager.cancel_all");
        sources.put("print_task_tree", "openjiuwen.core.common.task_manager.manager.print_task_tree");
        sources.put("get_task_group", "openjiuwen.core.common.task_manager.context.get_task_group");
        sources.put("set_task_group", "openjiuwen.core.common.task_manager.context.set_task_group");
        sources.put("reset_task_group", "openjiuwen.core.common.task_manager.context.reset_task_group");
        sources.put("get_current_task_id", "openjiuwen.core.common.task_manager.context.get_current_task_id");
        sources.put("TaskManagerEvents", "openjiuwen.core.runner.callback.events.TaskManagerEvents");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put("TaskManager", "com.openjiuwen.core.common.task_manager.TaskManager");
        javaTypeNames.put("Task", "com.openjiuwen.core.common.task_manager.Task");
        javaTypeNames.put("TaskStatus", "com.openjiuwen.core.common.task_manager.TaskStatus");
        javaTypeNames.put("TERMINAL_STATES", "com.openjiuwen.core.common.task_manager.TaskStates#TERMINAL_STATES");
        javaTypeNames.put("TaskError", "com.openjiuwen.core.common.task_manager.TaskError");
        javaTypeNames.put("TaskNotFoundError", "com.openjiuwen.core.common.task_manager.TaskNotFoundError");
        javaTypeNames.put("DuplicateTaskError", "com.openjiuwen.core.common.task_manager.DuplicateTaskError");
        javaTypeNames.put("get_task_manager", "com.openjiuwen.core.common.task_manager.TaskManager#getTaskManager");
        javaTypeNames.put("create_task", "com.openjiuwen.core.common.task_manager.TaskManager#createTaskGlobal");
        javaTypeNames.put("cancel_group", "com.openjiuwen.core.common.task_manager.TaskManager#cancelGroupGlobal");
        javaTypeNames.put("cancel_all", "com.openjiuwen.core.common.task_manager.TaskManager#cancelAllGlobal");
        javaTypeNames.put("print_task_tree", "com.openjiuwen.core.common.task_manager.TaskManager#printTaskTree");
        javaTypeNames.put("get_task_group", "com.openjiuwen.core.common.task_manager.TaskContext#getTaskGroup");
        javaTypeNames.put("set_task_group", "com.openjiuwen.core.common.task_manager.TaskContext#setTaskGroup");
        javaTypeNames.put("reset_task_group", "com.openjiuwen.core.common.task_manager.TaskContext#resetTaskGroup");
        javaTypeNames.put("get_current_task_id", "com.openjiuwen.core.common.task_manager.TaskContext#getCurrentTaskId");
        javaTypeNames.put("TaskManagerEvents", "com.openjiuwen.core.runner.callback.TaskManagerEvents");
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
