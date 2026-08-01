/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.List;
import java.util.Map;

/**
 * Public interface CronToolBackend used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public interface CronToolBackend {
    /**
     * listJobs.
     * 
     * @param isIncludeDisabled isIncludeDisabled
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    List<Map<String, Object>> listJobs(boolean isIncludeDisabled) throws Exception;

    /**
     * createJob.
     * 
     * @param params params
     * @param context context
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    Map<String, Object> createJob(Map<String, Object> params, CronToolContext context) throws Exception;

    /**
     * updateJob.
     * 
     * @param jobId jobId
     * @param patch patch
     * @param context context
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    Map<String, Object> updateJob(String jobId, Map<String, Object> patch, CronToolContext context) throws Exception;

    /**
     * deleteJob.
     * 
     * @param jobId jobId
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    boolean deleteJob(String jobId) throws Exception;

    /**
     * runNow.
     * 
     * @param jobId jobId
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    String runNow(String jobId) throws Exception;

    /**
     * status.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    Map<String, Object> status() throws Exception;

    /**
     * getRuns.
     * 
     * @param jobId jobId
     * @param limit limit
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    List<Map<String, Object>> getRuns(String jobId, int limit) throws Exception;

    /**
     * wake.
     * 
     * @param text text
     * @param context context
     * @param mode mode
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    Map<String, Object> wake(String text, CronToolContext context, String mode) throws Exception;
}
