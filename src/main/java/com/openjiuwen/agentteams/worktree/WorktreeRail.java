/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.worktree;

import java.util.List;

/**
 * Worktree lifecycle hook interface.
 */
public interface WorktreeRail {
    default String beforeWorktreeCreate(String slug, String repoRoot) {
        return "";
    }

    default void afterWorktreeCreate(WorktreeSession session) {
    }

    default String beforeWorktreeExit(WorktreeSession session, String action) {
        return "";
    }

    default void afterWorktreeExit(WorktreeSession session, String action) {
    }

    default boolean onWorktreeFileWrite(WorktreeSession session, String filePath) {
        return true;
    }

    default String beforeWorktreeCommit(WorktreeSession session, String message, List<String> files) {
        return "";
    }

    default void afterWorktreeCommit(WorktreeSession session, String commitSha) {
    }

    default List<String> onWorktreeSync(WorktreeSession session, String direction, List<String> files) {
        return files;
    }

    static Object fire(List<WorktreeRail> rails, String method, Object[] args) {
        if (rails == null || rails.isEmpty()) {
            return "";
        }
        Object result = null;
        for (WorktreeRail rail : rails) {
            Object next = switch (method) {
                case "beforeWorktreeCreate" -> {
                    yield rail.beforeWorktreeCreate(
                            requireArg(method, args, 0, String.class),
                            requireArg(method, args, 1, String.class));
                }
                case "afterWorktreeCreate" -> {
                    rail.afterWorktreeCreate(requireArg(method, args, 0, WorktreeSession.class));
                    yield null;
                }
                case "beforeWorktreeExit" -> {
                    yield rail.beforeWorktreeExit(
                            requireArg(method, args, 0, WorktreeSession.class),
                            requireArg(method, args, 1, String.class));
                }
                case "afterWorktreeExit" -> {
                    rail.afterWorktreeExit(
                            requireArg(method, args, 0, WorktreeSession.class),
                            requireArg(method, args, 1, String.class));
                    yield null;
                }
                case "onWorktreeFileWrite" -> {
                    yield rail.onWorktreeFileWrite(
                            requireArg(method, args, 0, WorktreeSession.class),
                            requireArg(method, args, 1, String.class));
                }
                case "beforeWorktreeCommit" -> rail.beforeWorktreeCommit(
                        requireArg(method, args, 0, WorktreeSession.class),
                        requireArg(method, args, 1, String.class),
                        requireStringList(method, args, 2));
                case "afterWorktreeCommit" -> {
                    rail.afterWorktreeCommit(
                            requireArg(method, args, 0, WorktreeSession.class),
                            requireArg(method, args, 1, String.class));
                    yield null;
                }
                case "onWorktreeSync" -> rail.onWorktreeSync(
                        requireArg(method, args, 0, WorktreeSession.class),
                        requireArg(method, args, 1, String.class),
                        requireStringList(method, args, 2));
                default -> null;
            };
            if (next != null) {
                result = next;
            }
        }
        return result;
    }

    private static <T> T requireArg(String method, Object[] args, int index, Class<T> type) {
        if (args.length <= index || !type.isInstance(args[index])) {
            throw new IllegalArgumentException("Invalid argument " + index + " for worktree rail method: " + method);
        }
        return type.cast(args[index]);
    }

    private static List<String> requireStringList(String method, Object[] args, int index) {
        if (args.length <= index || !(args[index] instanceof List<?> values)) {
            throw new IllegalArgumentException("Invalid argument " + index + " for worktree rail method: " + method);
        }
        if (values.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException("Invalid string list argument for worktree rail method: " + method);
        }
        @SuppressWarnings("unchecked")
        List<String> stringValues = (List<String>) values;
        return (List<String>) values;
    }
}
