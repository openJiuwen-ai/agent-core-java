// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ControllerAgent}.
 */
class ControllerAgentTest {

    private Controller mockController;
    private AgentCard card;

    @BeforeEach
    void setUp() {
        mockController = mock(Controller.class);
        card = AgentCard.builder()
                .name("ctrl-agent")
                .description("Controller agent")
                .build();
    }

    // ========== Construction ==========

    @Test
    void testConstructionWithoutConfig() {
        ControllerAgent agent = new ControllerAgent(card, mockController);

        assertThat(agent.getCard().getName()).isEqualTo("ctrl-agent");
        assertThat(agent.getController()).isSameAs(mockController);
        assertThat(agent.getContextEngine()).isNotNull();
        assertThat(agent.getConfig()).isInstanceOf(ControllerConfig.class);

        // Verify controller.init was called
        verify(mockController).init(eq(card), any(ControllerConfig.class), any(), any());
    }

    @Test
    void testConstructionWithConfig() {
        ControllerConfig config = new ControllerConfig();
        ControllerAgent agent = new ControllerAgent(card, mockController, config);

        assertThat(agent.getConfig()).isSameAs(config);
        verify(mockController).init(eq(card), eq(config), any(), any());
    }

    // ========== Configure ==========

    @Test
    void testConfigureWithControllerConfig() {
        ControllerAgent agent = new ControllerAgent(card, mockController);

        ControllerConfig newConfig = new ControllerConfig();
        BaseAgent result = agent.configure(newConfig);

        assertThat(result).isSameAs(agent);
        assertThat(agent.getConfig()).isSameAs(newConfig);
        verify(mockController).setConfig(newConfig);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testConfigureWithMapLogsWarning() {
        ControllerAgent agent = new ControllerAgent(card, mockController);

        agent.configure(Map.of("maxConcurrentTasks", 7, "enableTaskPersistence", true));

        ControllerConfig actual = (ControllerConfig) agent.getConfig();
        assertThat(actual.getMaxConcurrentTasks()).isEqualTo(7);
        assertThat(actual.isEnableTaskPersistence()).isTrue();
        assertThat(actual.getEventQueueSize()).isEqualTo(10000);
        verify(mockController).setConfig(actual);
    }

    // ========== Getters ==========

    @Test
    void testGetController() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        assertThat(agent.getController()).isSameAs(mockController);
    }

    @Test
    void testGetContextEngine() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        assertThat(agent.getContextEngine()).isNotNull();
    }

    // ========== Invoke ==========

    @Test
    void testInvokeNullSessionThrows() {
        ControllerAgent agent = new ControllerAgent(card, mockController);

        assertThatThrownBy(() -> agent.invoke("hello", null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testInvokeSuccess() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        Session mockSession = mock(Session.class);
        when(mockSession.getSessionId()).thenReturn("sess-1");

        ControllerOutput mockOutput = mock(ControllerOutput.class);
        when(mockController.invoke(any(InputEvent.class), any(AgentSessionApi.class)))
                .thenReturn(mockOutput);

        Object result = agent.invoke("hello", mockSession);

        assertThat(result).isSameAs(mockOutput);
        verify(mockController).invoke(any(InputEvent.class), any(AgentSessionApi.class));
    }

    @Test
    void testInvokeControllerThrows() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        Session mockSession = mock(Session.class);
        when(mockSession.getSessionId()).thenReturn("sess-1");

        when(mockController.invoke(any(InputEvent.class), any(AgentSessionApi.class)))
                .thenThrow(new RuntimeException("controller failed"));

        assertThatThrownBy(() -> agent.invoke("hello", mockSession))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testInvokeBaseErrorPassThrough() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        Session mockSession = mock(Session.class);
        when(mockSession.getSessionId()).thenReturn("sess-base-error");

        BaseError error = ErrorHelper.buildError(
                StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                "error_msg", "base error"
        );
        when(mockController.invoke(any(InputEvent.class), any(AgentSessionApi.class)))
                .thenThrow(error);

        assertThatThrownBy(() -> agent.invoke("hello", mockSession))
                .isSameAs(error);
    }

    @Test
    void testInvokeWithMapInput() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        Session mockSession = mock(Session.class);
        when(mockSession.getSessionId()).thenReturn("sess-2");

        ControllerOutput mockOutput = mock(ControllerOutput.class);
        when(mockController.invoke(any(InputEvent.class), any(AgentSessionApi.class)))
                .thenReturn(mockOutput);

        Object result = agent.invoke(Map.of("key", "val"), mockSession);

        assertThat(result).isSameAs(mockOutput);
    }

    // ========== Stream ==========

    @Test
    void testStreamNullSessionThrows() {
        ControllerAgent agent = new ControllerAgent(card, mockController);

        assertThatThrownBy(() -> agent.stream("hello", null, List.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStreamSuccess() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        Session mockSess = mock(Session.class);
        when(mockSess.getSessionId()).thenReturn("sess-3");

        Iterator<Object> mockIter = mock(Iterator.class);
        when(mockController.stream(any(InputEvent.class), any(AgentSessionApi.class), any()))
                .thenReturn(mockIter);

        Iterator<Object> result = agent.stream("hello", mockSess, List.of());
        assertThat(result).isSameAs(mockIter);
    }

    @Test
    void testStreamControllerThrows() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        Session mockSess = mock(Session.class);
        when(mockSess.getSessionId()).thenReturn("sess-4");

        when(mockController.stream(any(InputEvent.class), any(AgentSessionApi.class), any()))
                .thenThrow(new RuntimeException("stream failed"));

        assertThatThrownBy(() -> agent.stream("hello", mockSess, List.of()))
                .isInstanceOf(RuntimeException.class);
    }

    // ========== Release Session ==========

    @Test
    void testReleaseSession() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        EventQueue eventQueue = mock(EventQueue.class);
        when(mockController.getEventQueue()).thenReturn(eventQueue);

        agent.releaseSession("sess-123");

        verify(eventQueue).unsubscribe(card.getId(), "sess-123");
    }

    // ========== Inherited BaseAgent ==========

    @Test
    void testAbilityManagerAvailable() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        assertThat(agent.getAbilityManager()).isNotNull();
    }

    @Test
    void testCallbackManagerAvailable() {
        ControllerAgent agent = new ControllerAgent(card, mockController);
        assertThat(agent.getAgentCallbackManager()).isNotNull();
    }
}
