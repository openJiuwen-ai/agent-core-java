/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

/**
 * Trajectory observability for the JiuwenSwarm WebUI trace viewer.
 *
 * <p>Producers ({@link com.openjiuwen.extensions.trajectory.TrajectoryRail},
 * {@link com.openjiuwen.extensions.trajectory.RunSpans}) create OTel spans
 * with the trajectory semantic conventions; the
 * {@link com.openjiuwen.extensions.trajectory.SpanRecordProcessor} converts
 * them into self-contained single-span OTLP JSON records and forwards them to
 * registered {@link com.openjiuwen.extensions.trajectory.TrajectoryRecordConsumer}s.
 * Mirrors Python Agent Core {@code openjiuwen/extensions/observability}.</p>
 *
 * @since 0.1.18
 */
package com.openjiuwen.extensions.trajectory;
