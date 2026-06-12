/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's base transport config helpers in
 * {@code openjiuwen/agent_teams/messager/base.py}.
 */
class MessagerBaseTest {

    @Test
    void transportConfigAndFactoryMirrorPythonDefaults() {
        MessagerTransportConfig config = new MessagerTransportConfig();

        assertThat(config.getBackend()).isEqualTo("inprocess");
        assertThat(config.getTeamName()).isEqualTo("default");
        assertThat(config.getListenAddrs()).isEmpty();
        assertThat(config.getBootstrapPeers()).isEmpty();
        assertThat(config.getKnownPeers()).isEmpty();
        assertThat(config.getMetadata()).isEmpty();
        assertThat(config.getRequestTimeout()).isEqualTo(10.0);
        assertThat(config.broadcastTopic()).isEqualTo("team:default:broadcast");
        assertThat(Messagers.createMessager(config)).isInstanceOf(InProcessMessager.class);
    }

    @Test
    void peerAndSubscriptionMetadataRemainJsonSafe() {
        MessagerPeerConfig peer = new MessagerPeerConfig();
        peer.setAgentId("agent-a");
        peer.setPeerId("peer-a");
        peer.setAddrs(List.of("tcp://127.0.0.1:9001"));
        peer.setMetadata(Map.of("role", "leader"));

        SubscriptionHandle handle = new SubscriptionHandle("sub-1", "topic-a");
        handle.setAgentId("agent-a");
        handle.setBackendMetadata(Map.of("backend", "pyzmq"));

        assertThat(peer.getAgentId()).isEqualTo("agent-a");
        assertThat(peer.getPeerId()).isEqualTo("peer-a");
        assertThat(peer.getAddrs()).containsExactly("tcp://127.0.0.1:9001");
        assertThat(peer.getMetadata()).containsEntry("role", "leader");
        assertThat(handle.getSubscriptionId()).isEqualTo("sub-1");
        assertThat(handle.getTopic()).isEqualTo("topic-a");
        assertThat(handle.getAgentId()).isEqualTo("agent-a");
        assertThat(handle.getBackendMetadata()).containsEntry("backend", "pyzmq");
    }

    @Test
    void createMessagerSupportsConfiguredBackendsOnly() {
        MessagerTransportConfig pyzmq = new MessagerTransportConfig();
        pyzmq.setBackend("pyzmq");
        assertThat(Messagers.createMessager(pyzmq)).isInstanceOf(PyZmqMessager.class);

        MessagerTransportConfig unsupported = new MessagerTransportConfig();
        unsupported.setBackend("unsupported");
        assertThatThrownBy(() -> Messagers.createMessager(unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported messager backend: unsupported");
    }
}
