/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Base evaluator-pipeline benchmark adapter contract.
 *
 * <p>Mirrors Python's {@code BaseBenchAdapter} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/base.py}.</p>
 */
public abstract class BaseBenchAdapter {

    private final Map<String, Object> config;

    protected BaseBenchAdapter() {
        this(null);
    }

    protected BaseBenchAdapter(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
    }

    public abstract String name();

    public abstract List<Task> loadTasks();

    public abstract CompletableFuture<Void> prepareEnvironment(Task task, DockerEnvironment env);

    public abstract CompletableFuture<EvalResult> evaluate(DockerEnvironment env, Task task);

    public boolean cloneRepo() {
        return true;
    }

    public String taskBasePath() {
        return "";
    }

    public List<Task> filterTasks(
            List<Task> tasks,
            List<String> taskIds,
            List<String> categories,
            List<String> difficulties) {
        List<Task> filtered = tasks;
        if (taskIds != null && !taskIds.isEmpty()) {
            filtered = filtered.stream()
                    .filter(task -> taskIds.contains(task.getTaskId()))
                    .collect(Collectors.toList());
        }
        if (categories != null && !categories.isEmpty()) {
            filtered = filtered.stream()
                    .filter(task -> categories.contains(String.valueOf(task.getMetadata().get("category"))))
                    .collect(Collectors.toList());
        }
        if (difficulties != null && !difficulties.isEmpty()) {
            filtered = filtered.stream()
                    .filter(task -> difficulties.contains(String.valueOf(task.getMetadata().get("difficulty"))))
                    .collect(Collectors.toList());
        }
        return filtered;
    }

    public Map<String, Object> aggregate(List<EvalResult> results) {
        if (results == null || results.isEmpty()) {
            return Map.of(
                    "overall_score", 0.0,
                    "passed", 0,
                    "total", 0
            );
        }
        int total = results.size();
        long passed = results.stream().filter(EvalResult::isPassed).count();
        double avgRate = results.stream().mapToDouble(EvalResult::getPassRate).sum() / total;
        return Map.of(
                "overall_score", avgRate,
                "passed", passed,
                "total", total
        );
    }

    public Map<String, Object> getConfig() {
        return config;
    }
}
