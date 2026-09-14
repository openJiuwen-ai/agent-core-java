/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import java.util.Map;

public interface ServerAdapterProvider {

    String protocol();

    Object create(Map<String, Object> kwargs);
}
