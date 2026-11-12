/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.concurrent.StripedKeyLocks;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.SessionContextHolder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * 待办修改工具 todo_modify 的调用入口：在通用参数校验前规范化参数，并按会话串行执行修改。
 *
 * <p>以下 4 类示例中的任务 ID 为示例值。</p>
 *
 * <p>1. 正常参数（当前 Schema 要求）：修改字段放在 todos 的每个任务对象内，可在一次调用中更新多个任务。</p>
 * <pre>{@code
 * {"action": "update", "todos": [
 *     {"id": "inspect_file", "status": "completed", "result_summary": "..."},
 *     {"id": "parse_data", "status": "in_progress"}]}
 * }</pre>
 *
 * <p>2. 旧格式参数：顶层键 updates，任务 ID 用 task_id，可不带 action。
 * 当前由 {@link #normalizeInputs(Map)} 转换为第 1 类格式。0.1.17 起已不再支持该格式，出于兼容性仍保留转换。</p>
 * <pre>{@code
 * {"updates": [{"task_id": "inspect_file", "status": "completed"}]}
 * {"action": "update", "updates": [{"task_id": "inspect_file", "status": "completed"}]}
 * 两者均转换为：
 * {"action": "update", "todos": [{"id": "inspect_file", "status": "completed"}]}
 * }</pre>
 *
 * <p>3. 在做测试时发现的 GLM-5.3 工具调用：
 * 同一条助手消息带两个 todo_modify 调用，每个调用只放一个任务，status 在 arguments 顶层。</p>
 * <pre>{@code
 * "tool_calls": [
 *   {"index": 0, "id": "...92d9", "type": "function", "function": {"name": "todo_modify",
 *     "arguments": "{\"action\": \"update\", \"status\": \"completed\", \"todos\": [{\"id\": \"inspect_file\"}]}"}},
 *   {"index": 1, "id": "...92da", "type": "function", "function": {"name": "todo_modify",
 *     "arguments": "{\"action\": \"update\", \"status\": \"in_progress\", \"todos\": [{\"id\": \"parse_data\"}]}"}}
 * ]
 * }</pre>
 * <p>问题 1：顶层 status 不在 Schema 声明的顶层字段中，直接交给父类会校验失败。
 * {@link #liftTopLevelUpdateFields(Map)} 将其补入每个缺少该字段的任务对象（任务对象内已有的同名字段优先），
 * 上例两个调用分别变为：</p>
 * <pre>{@code
 * {"action": "update", "todos": [{"id": "inspect_file", "status": "completed"}]}
 * {"action": "update", "todos": [{"id": "parse_data", "status": "in_progress"}]}
 * }</pre>
 * <p>问题 2：上例拆成的两个调用由框架并发执行。本类对同一会话的修改加锁串行，
 * 避免读到旧列表或互相覆盖；加锁不保证两个调用的执行顺序。</p>
 *
 * <p>4. GLM-5.2 对同一请求返回的工具调用（同上来源，id 与 result_summary 已截短）：一个调用，
 * 修改字段在任务对象内，即第 1 类格式，无需转换。</p>
 * <pre>{@code
 * "tool_calls": [
 *   {"index": 0, "id": "...5c88", "type": "function", "function": {"name": "todo_modify",
 *     "arguments": "{\"action\": \"update\", \"todos\": [{\"id\": \"inspect_file\", \"status\": \"completed\",
 *       \"result_summary\": \"...\"}, {\"id\": \"parse_data\", \"status\": \"in_progress\"}]}"}}
 * ]
 * }</pre>
 *
 * @since 0.1.16
 */
public final class TodoModifyFunction extends LocalFunction {
    /** 这些属性应该在todoModify的每一项中，如果模型让其出现在参数顶层，会被补入每个任务对象的修改字段。 */
    private static final List<String> TODO_UPDATE_FIELDS = List.of("content", "activeForm", "active_form",
            "description", "status", "priority", "selected_model_id", "depends_on", "result_summary", "meta_data");

    /** 会话锁的分段数，同一会话 ID 总是映射到同一段。 */
    private static final int SESSION_LOCK_STRIPES = 64;

    /** 按会话 ID 分段的锁，保证同一会话的 todo 修改串行执行。 */
    private static final StripedKeyLocks SESSION_LOCKS = new StripedKeyLocks(SESSION_LOCK_STRIPES);

    /**
     * 创建待办修改工具，保留父类的参数校验和会话上下文处理。
     *
     * @param card 统一参数结构的工具元数据
     * @param function 执行待办修改的函数
     * @since 0.1.16
     */
    public TodoModifyFunction(ToolCard card, Function<Map<String, Object>, Object> function) {
        super(card, function);
    }

    /** {@inheritDoc} */
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> normalized = normalizeInputs(inputs);
        ReentrantLock lock = SESSION_LOCKS.get(resolveSessionId(normalized, kwargs));
        lock.lock();
        try {
            return super.invoke(normalized, kwargs);
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> normalized = normalizeInputs(inputs);
        ReentrantLock lock = SESSION_LOCKS.get(resolveSessionId(normalized, kwargs));
        lock.lock();
        try {
            return super.stream(normalized, kwargs);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 解析加锁用的会话 ID，与存储侧实际使用的会话保持一致。
     *
     * <p>有效会话：kwargs 中有 session 时用它，否则用线程绑定的会话（与 LocalFunction 调用函数前
     * 用 kwargs 中的 session 覆盖线程会话的行为一致）。之后与 TaskPlanningRail.sessionId 相同：
     * 有效会话的 ID 非空白时用它，否则用非空白的参数 session_id，否则为 default。</p>
     *
     * @param inputs 已规范化的调用参数
     * @param kwargs 调用附加参数，可能包含 session
     * @return 会话 ID
     */
    private static String resolveSessionId(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Object session = kwargs == null ? null : kwargs.get("session");
        Session effective = session instanceof Session current ? current : SessionContextHolder.getCurrentSession();
        if (effective != null && hasText(effective.getSessionId())) {
            return effective.getSessionId();
        }
        Object explicit = inputs.get("session_id");
        if (explicit != null && hasText(String.valueOf(explicit))) {
            return String.valueOf(explicit);
        }
        return "default";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 把调用参数整理成当前 Schema 能通过校验的形式。返回副本，不改调用方传入的 Map。
     *
     * <p>第一步：兼容旧写法 updates（类注释第 2 类）。旧版本 Schema 里有 updates 参数，任务 ID 叫 task_id；
     * 现在的 Schema 没有 updates，直接校验会报错，所以先改写成新写法：
     * 去掉 updates，把其中每一项的 task_id 改名为 id 后放进 todos；没写 action 时补成 update；
     * 调用里已经有 todos 时忽略 updates，以 todos 为准。</p>
     *
     * <p>第二步：把放在顶层的 status 等字段挪进 todos 的每一项（类注释第 3 类），见 liftTopLevelUpdateFields。</p>
     *
     * @param inputs 调用方传入的参数，可为 null
     * @return 整理后的参数副本
     */
    private static Map<String, Object> normalizeInputs(Map<String, Object> inputs) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (inputs != null) {
            normalized.putAll(inputs);
        }
        if (normalized.containsKey("updates")) {
            Object legacyUpdates = normalized.remove("updates");
            Object action = normalized.get("action");
            if (action == null || (action instanceof String value && value.isBlank())) {
                normalized.put("action", "update");
            }
            if (!normalized.containsKey("todos")) {
                normalized.put("todos", normalizeLegacyUpdates(legacyUpdates));
            }
        }
        liftTopLevelUpdateFields(normalized);
        return normalized;
    }

    /**
     * 兼容部分模型把 status 等修改字段放在参数顶层的写法：仅对 update 且 todos 为任务对象列表时生效，
     * 将顶层修改字段补入每个任务对象（任务对象中已有的同名字段优先），并从顶层移除。
     * 其他情况保持原样，交由通用参数校验处理。示例见类注释第 3 类。
     *
     * @param normalized 已复制的调用参数，原地修改
     */
    private static void liftTopLevelUpdateFields(Map<String, Object> normalized) {
        if (!"update".equals(normalized.get("action"))) {
            return;
        }
        Map<String, Object> topLevelFields = new LinkedHashMap<>();
        for (String field : TODO_UPDATE_FIELDS) {
            if (normalized.containsKey(field)) {
                topLevelFields.put(field, normalized.get(field));
            }
        }
        if (topLevelFields.isEmpty()) {
            return;
        }
        List<Map<String, Object>> todos = copyTodoObjects(normalized.get("todos"));
        if (todos.isEmpty()) {
            return;
        }
        for (Map<String, Object> todo : todos) {
            for (Map.Entry<String, Object> field : topLevelFields.entrySet()) {
                if (!todo.containsKey(field.getKey())) {
                    todo.put(field.getKey(), field.getValue());
                }
            }
        }
        topLevelFields.keySet().forEach(normalized::remove);
        normalized.put("todos", todos);
        Loggers.TOOL.warning("todo_modify moved top-level update fields " + topLevelFields.keySet()
                + " into each todo item");
    }

    /**
     * 复制 todos 中的任务对象，供补入顶层字段时修改。
     *
     * @param rawTodos 参数中的 todos 值
     * @return 任务对象副本；todos 不是非空对象列表或含非字符串键时返回空列表，表示不处理
     */
    private static List<Map<String, Object>> copyTodoObjects(Object rawTodos) {
        if (!(rawTodos instanceof List<?> todos) || todos.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copies = new ArrayList<>(todos.size());
        for (Object value : todos) {
            if (!(value instanceof Map<?, ?> todo)) {
                return List.of();
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : todo.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    return List.of();
                }
                copy.put(key, entry.getValue());
            }
            copies.add(copy);
        }
        return copies;
    }

    private static List<Map<String, Object>> normalizeLegacyUpdates(Object rawUpdates) {
        if (!(rawUpdates instanceof List<?> updates)) {
            throw new IllegalArgumentException("todo_modify updates must be a list");
        }
        List<Map<String, Object>> normalized = new ArrayList<>(updates.size());
        for (Object value : updates) {
            if (!(value instanceof Map<?, ?> update)) {
                throw new IllegalArgumentException("todo_modify updates items must be objects");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : update.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("todo_modify update field names must be strings");
                }
                item.put(key, entry.getValue());
            }
            if (item.containsKey("task_id")) {
                item.put("id", item.remove("task_id"));
            }
            normalized.add(item);
        }
        return normalized;
    }
}
