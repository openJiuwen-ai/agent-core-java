/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.humaneval;

/**
 * One HumanEval sample loaded from the dataset.
 *
 * @param taskId     HumanEval task id, e.g. {@code HumanEval/0}
 * @param prompt     function signature + docstring (and any imports) the model must complete
 * @param test       the assertion block provided by the dataset
 * @param entryPoint function name to call in {@code check(entry_point)}
 * @since 2026-08-08
 */
public record HumanEvalTask(
        String taskId,
        String prompt,
        String test,
        String entryPoint
) {
}
