/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Determines trajectory labels through an LLM judge.
 * <p>
 * Mirrors Python's {@code LabelDeterminator} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/update.py}.
 * </p>
 */
public final class LabelDeterminator {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final Pattern STATUS_PATTERN = Pattern.compile("Status:\\s*(success|failure)", Pattern.CASE_INSENSITIVE);

    private LabelDeterminator() {
    }

    public static CompletableFuture<Boolean> determineLabel(ReasoningBankAsyncLlm llm,
                                                            String query,
                                                            Object trajectory) {
        String trajectoryText = ReasoningBankUpdateUtils.trajectoryToText(trajectory);
        String userPrompt = ReasoningBankPrompts.LLM_JUDGE_USER_PROMPT
                .replace("{query}", query)
                .replace("{trajectory}", trajectoryText);
        LOGGER.info("Determining trajectory label using LLM-as-judge...");
        return llm.asyncGenerate(userPrompt, ReasoningBankPrompts.LLM_JUDGE_SYSTEM_PROMPT)
                .thenApply(judgeResponse -> {
                    Matcher matcher = STATUS_PATTERN.matcher(judgeResponse);
                    boolean success;
                    if (matcher.find()) {
                        success = "success".equals(matcher.group(1).toLowerCase(Locale.ROOT));
                    } else {
                        success = judgeResponse.toLowerCase(Locale.ROOT).contains("success");
                    }
                    LOGGER.info("Label determined: %s", success ? "success" : "failure");
                    return success;
                });
    }
}
