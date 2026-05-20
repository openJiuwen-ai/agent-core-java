/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Peer endpoint definition used by messager bootstrap and known-peer routing.
 *
 * @since 1.0
 */
public class MessagerPeerConfig {
    private String agentId;
    private String peerId;
    @Builder.Default
    private List<String> addrs = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
