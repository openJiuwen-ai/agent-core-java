/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.task;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a task plan with goal and tasks.
 * Mirrors Python's TaskPlan schema.
 */
public class TaskPlan {
    
    private final String goal;
    private final List<TodoItem> tasks;
    
    public TaskPlan(String goal, List<TodoItem> tasks) {
        this.goal = goal;
        this.tasks = tasks != null ? tasks : List.of();
    }
    
    public String getGoal() {
        return goal;
    }
    
    public List<TodoItem> getTasks() {
        return tasks;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("goal", goal);
        map.put("tasks", tasks.stream().map(TodoItem::toMap).toList());
        return map;
    }
    
    public static TaskPlan fromMap(Map<String, Object> map) {
        String goal = (String) map.get("goal");
        List<Map<String, Object>> taskMaps = (List<Map<String, Object>>) map.get("tasks");
        List<TodoItem> tasks = taskMaps != null 
            ? taskMaps.stream().map(TodoItem::fromMap).toList() 
            : List.of();
        return new TaskPlan(goal, tasks);
    }
}