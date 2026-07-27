/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Remote client configuration.
 *
 * <p>Mirrors Python's {@code RemoteClientConfig} in
 * {@code openjiuwen/core/runner/drunner/remote_client/remote_client_config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteClientConfig {

    private String id;

    private String version;

    private String name;

    private String description;

    @Builder.Default
    private ProtocolEnum protocol = ProtocolEnum.MQ;

    private String type;

    private String topic;

    private String url;

    @Builder.Default
    private Map<String, Object> kwargs = new LinkedHashMap<>();
}
