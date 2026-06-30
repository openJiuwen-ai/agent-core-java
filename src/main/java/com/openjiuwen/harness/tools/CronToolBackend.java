/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.List;
import java.util.Map;

/**
 * Public interface CronToolBackend used by the Java parity implementation.
 *
 * @since 1.0
 */
public interface CronToolBackend {
    List<Map<String, Object>> listJobs(boolean isIncludeDisabled) throws Exception;

    Map<String, Object> createJob(Map<String, Object> params, CronToolContext context) throws Exception;

    Map<String, Object> updateJob(String jobId, Map<String, Object> patch, CronToolContext context) throws Exception;

    boolean deleteJob(String jobId) throws Exception;

    String runNow(String jobId) throws Exception;

    Map<String, Object> status() throws Exception;

    List<Map<String, Object>> getRuns(String jobId, int limit) throws Exception;

    Map<String, Object> wake(String text, CronToolContext context, String mode) throws Exception;
}
