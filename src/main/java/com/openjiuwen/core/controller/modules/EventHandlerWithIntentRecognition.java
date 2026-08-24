/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.VirtualThreadSupport;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.IntentType;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event handler with intent recognition.
 * <p>
 * Extends {@link EventHandler} with LLM-based intent recognition,
 * routing actions by recognized intent type.
 * <p>
 * Mirrors Python's {@code EventHandlerWithIntentRecognition} in
 * {@code openjiuwen/core/controller/modules/intent_recognizer.py}.
 */
public class EventHandlerWithIntentRecognition extends EventHandler {

    private IntentRecognizer recognizer;
    private IntentRecognizer.ModelProvider modelProvider;

    public EventHandlerWithIntentRecognition(IntentRecognizer.ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    /**
     * Initialize the recognizer after dependencies are set (config, taskManager, etc.).
     */
    public void initRecognizer() {
        this.recognizer = new IntentRecognizer(
                config, taskManager, abilityManager, contextEngine, modelProvider
        );
    }

    @Override
    public Map<String, Object> handleInput(EventHandlerInput inputs) {
        if (recognizer == null) {
            initRecognizer();
        }

        List<Intent> intents = recognizer.recognize(inputs.getEvent(), inputs.getSession());
        List<Thread> threads = new ArrayList<>();

        for (Intent intent : intents) {
            Thread t = VirtualThreadSupport.startThread(() -> {
                switch (intent.getIntentType()) {
                    case CREATE_TASK -> processCreateTaskIntent(intent, inputs.getSession());
                    case PAUSE_TASK -> processPauseTaskIntent(intent, inputs.getSession());
                    case RESUME_TASK -> processResumeTaskIntent(intent, inputs.getSession());
                    case CONTINUE_TASK -> processContinueTaskIntent(intent, inputs.getSession());
                    case SUPPLEMENT_TASK -> processSupplementTaskIntent(intent, inputs.getSession());
                    case CANCEL_TASK -> processCancelTaskIntent(intent, inputs.getSession());
                    case MODIFY_TASK -> processModifyTaskIntent(intent, inputs.getSession());
                    default -> processUnknownTaskIntent(intent, inputs.getSession());
                }
            });
            threads.add(t);
        }

        // Wait for all intent processing threads
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
        if (!(inputs.getEvent() instanceof TaskInteractionEvent)) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Input Event has to be type of TaskInteractionEvent, not "
                            + inputs.getEvent().getClass().getSimpleName());
        }
        TaskInteractionEvent event = (TaskInteractionEvent) inputs.getEvent();
        inputs.getSession().writeStream(Map.of("interaction", event.getInteraction()));
        return null;
    }

    @Override
    public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
        if (!(inputs.getEvent() instanceof TaskCompletionEvent)) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Input Event has to be type of TaskCompletionEvent, not "
                            + inputs.getEvent().getClass().getSimpleName());
        }
        TaskCompletionEvent event = (TaskCompletionEvent) inputs.getEvent();
        inputs.getSession().writeStream(Map.of("result", event.getTaskResult()));
        return null;
    }

    @Override
    public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
        if (!(inputs.getEvent() instanceof TaskFailedEvent)) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Input Event has to be type of TaskFailedEvent, not "
                            + inputs.getEvent().getClass().getSimpleName());
        }
        TaskFailedEvent event = (TaskFailedEvent) inputs.getEvent();
        inputs.getSession().writeStream(Map.of("error_message", event.getErrorMessage()));
        return null;
    }

    // ==================== Intent Processors ====================

    private void processCreateTaskIntent(Intent intent, AgentSessionApi session) {
        Task task = new Task();
        task.setSessionId(session.getSessionId());
        task.setTaskId(intent.getTargetTaskId());
        task.setTaskType("default_task_type");
        task.setDescription(intent.getTargetTaskDescription());
        task.setPriority(1);
        task.setContextId(session.getSessionId() + "_" + intent.getTargetTaskId());
        if (intent.getEvent() instanceof InputEvent) {
            List<Event> inputList = new ArrayList<>();
            inputList.add(intent.getEvent());
            task.setInputs(inputList);
        }
        task.setStatus(TaskStatus.SUBMITTED);
        task.setMetadata(intent.getMetadata());
        taskManager.addTask(task);
    }

    private void processPauseTaskIntent(Intent intent, AgentSessionApi session) {
        taskScheduler.pauseTask(intent.getTargetTaskId());
    }

    private void processResumeTaskIntent(Intent intent, AgentSessionApi session) {
        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(intent.getTargetTaskId()));
        if (!tasks.isEmpty()) {
            Task task = tasks.get(0);
            if (task.getStatus() == TaskStatus.PAUSED) {
                task.setStatus(TaskStatus.SUBMITTED);
                taskManager.updateTask(task);
            }
        }
    }

    private void processContinueTaskIntent(Intent intent, AgentSessionApi session) {
        if (!(intent.getEvent() instanceof InputEvent inputEvent)) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Input Event has to be type of InputEvent, not "
                            + intent.getEvent().getClass().getSimpleName());
        }

        List<Event> previousEvents = new ArrayList<>();
        List<String> contextIds = new ArrayList<>();
        for (String taskId : intent.getDependTaskId()) {
            List<Task> oldTasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
            if (!oldTasks.isEmpty()) {
                Task oldTask = oldTasks.get(0);
                if (oldTask.getInputs() != null) {
                    previousEvents.addAll(oldTask.getInputs());
                }
                contextIds.add(oldTask.getContextId());
            }
        }

        // Add context messages as JsonDataFrame
        Map<String, Object> contextData = new HashMap<>();
        for (String ctxId : contextIds) {
            ModelContext ctx = contextEngine.getContext(ctxId, session.getSessionId());
            if (ctx != null) {
                List<BaseMessage> msgs = ctx.getMessages(null, true);
                contextData.put(ctxId, msgs);
            }
        }
        inputEvent.getInputData().add(new DataFrame.JsonDataFrame(contextData));

        previousEvents.add(inputEvent);

        Task task = new Task();
        task.setSessionId(session.getSessionId());
        task.setTaskId(intent.getTargetTaskId());
        task.setTaskType("default_task_type");
        task.setDescription(intent.getTargetTaskDescription());
        task.setPriority(1);
        task.setContextId(session.getSessionId() + "_" + intent.getTargetTaskId());
        task.setInputs(previousEvents);
        task.setStatus(TaskStatus.SUBMITTED);
        task.setMetadata(intent.getMetadata());
        taskManager.addTask(task);
    }

    private void processSupplementTaskIntent(Intent intent, AgentSessionApi session) {
        if (intent.getIntentType() != IntentType.SUPPLEMENT_TASK) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Intent type must be SUPPLEMENT_TASK");
        }

        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(intent.getTargetTaskId()));
        if (!tasks.isEmpty()) {
            Task task = tasks.get(0);
            taskScheduler.pauseTask(intent.getTargetTaskId());
            task.setDescription(task.getDescription() + "\n\n任务补充信息:\n" + intent.getSupplementaryInfo());
            task.setStatus(TaskStatus.SUBMITTED);
            taskManager.updateTask(task);
        }
    }

    private void processCancelTaskIntent(Intent intent, AgentSessionApi session) {
        if (intent.getIntentType() != IntentType.CANCEL_TASK) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Intent type must be CANCEL_TASK");
        }
        taskScheduler.cancelTask(intent.getTargetTaskId());
    }

    private void processModifyTaskIntent(Intent intent, AgentSessionApi session) {
        if (intent.getIntentType() != IntentType.MODIFY_TASK) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Intent type must be MODIFY_TASK");
        }
        taskScheduler.cancelTask(intent.getTargetTaskId());
        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(intent.getTargetTaskId()));
        if (!tasks.isEmpty()) {
            Task task = tasks.get(0);
            task.setDescription(intent.getTargetTaskDescription());
            List<Event> inputs = task.getInputs();
            if (inputs == null) {
                inputs = new ArrayList<>();
            }
            inputs.add(intent.getEvent());
            task.setInputs(inputs);
            task.setStatus(TaskStatus.SUBMITTED);
            taskManager.updateTask(task);
        }
    }

    private void processUnknownTaskIntent(Intent intent, AgentSessionApi session) {
        if (intent.getIntentType() != IntentType.UNKNOWN_TASK) {
            throw new IllegalArgumentException("Intent type must be UNKNOWN_TASK");
        }
        session.writeStream(Map.of("clarification_prompt", intent.getClarificationPrompt()));
    }
}
