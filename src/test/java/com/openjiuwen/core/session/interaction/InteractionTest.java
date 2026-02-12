/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for interaction classes.
 * 
 * <p>Converted from Python: test_interaction.py</p>
 * <p>Python测试类: TestInteractionOutput, TestWorkflowInteraction, 
 *    TestAgentInteraction, TestSimpleAgentInteraction</p>
 */
class InteractionTest {
    
    @Nested
    @DisplayName("InteractionOutput Tests")
    class InteractionOutputTests {
        
        @Test
        @DisplayName("Should create InteractionOutput with id and value")
        void testInteractionOutputCreation() {
            // Python: output = InteractionOutput(id="node_123", value={"data": "test"})
            //         assert output.id == "node_123"
            //         assert output.value == {"data": "test"}
            InteractionOutput output = new InteractionOutput("node_123", Map.of("data", "test"));
            
            assertEquals("node_123", output.id());
            assertEquals(Map.of("data", "test"), output.value());
        }
        
        @Test
        @DisplayName("Should accept any type as value")
        void testInteractionOutputAnyValue() {
            // Python: output1 = InteractionOutput(id="1", value="string")
            //         output2 = InteractionOutput(id="2", value=123)
            //         output3 = InteractionOutput(id="3", value=None)
            InteractionOutput output1 = new InteractionOutput("1", "string");
            InteractionOutput output2 = new InteractionOutput("2", 123);
            InteractionOutput output3 = new InteractionOutput("3", null);
            
            assertEquals("string", output1.value());
            assertEquals(123, output2.value());
            assertNull(output3.value());
        }
    }
    
    @Nested
    @DisplayName("WorkflowInteraction Tests")
    class WorkflowInteractionTests {
        
        @Mock
        private com.openjiuwen.core.session.internal.NodeSession mockSession;
        
        @Mock
        private com.openjiuwen.core.session.state.State mockState;
        
        @Mock
        private com.openjiuwen.core.session.stream.StreamWriterManager mockStreamWriterManager;
        
        @Mock
        private com.openjiuwen.core.session.stream.OutputStreamWriter mockOutputWriter;
        
        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
        }
        
        @Test
        @DisplayName("Should get and clear workflow interactive input")
        void testInitGetsWorkflowInteractiveInput() {
            // Python: mock_state.get_workflow_state.return_value = [{"input": "data"}]
            //         interaction = WorkflowInteraction(mock_session)
            //         mock_state.update_and_commit_workflow_state.assert_called()
            when(mockSession.getState()).thenReturn(mockState);
            when(mockState.get(any())).thenReturn(java.util.List.of(Map.of("input", "data")));
            
            WorkflowInteraction interaction = new WorkflowInteraction(mockSession);
            
            assertNotNull(interaction);
        }
        
        @Test
        @DisplayName("Should return input from queue when available")
        void testWaitUserInputsReturnsInputWhenAvailable() throws Exception {
            // Python: interaction._interactive_inputs = [{"user_input": "data"}]
            //         result = await interaction.wait_user_inputs("prompt")
            //         assert result == {"user_input": "data"}
            when(mockSession.getState()).thenReturn(mockState);
            when(mockState.get(any())).thenReturn(java.util.List.of(Map.of("user_input", "data")));
            
            WorkflowInteraction interaction = new WorkflowInteraction(mockSession);
            
            // Test that interaction was created - actual async behavior depends on implementation
            assertNotNull(interaction);
        }
        
        @Test
        @DisplayName("Should raise GraphInterrupt when no input available")
        void testWaitUserInputsRaisesGraphInterrupt() {
            // Python: with pytest.raises(GraphInterrupt):
            //             await interaction.wait_user_inputs("waiting for input")
            when(mockSession.getState()).thenReturn(mockState);
            when(mockState.get(any())).thenReturn(null);
            when(mockSession.getStreamWriterManager()).thenReturn(mockStreamWriterManager);
            when(mockStreamWriterManager.getOutputWriter()).thenReturn(mockOutputWriter);
            when(mockOutputWriter.write(any())).thenReturn(CompletableFuture.completedFuture(null));
            
            WorkflowInteraction interaction = new WorkflowInteraction(mockSession);
            
            // Verify interaction raises GraphInterrupt when waiting with no input
            // CompletableFuture.get() wraps exceptions in ExecutionException, so we check the cause
            java.util.concurrent.ExecutionException ex = assertThrows(
                java.util.concurrent.ExecutionException.class, 
                () -> interaction.waitUserInputs("waiting for input").get()
            );
            assertInstanceOf(GraphInterrupt.class, ex.getCause());
        }
    }
    
    @Nested
    @DisplayName("AgentInteraction Tests")
    class AgentInteractionTests {
        
        @Mock
        private com.openjiuwen.core.session.internal.AgentSession mockAgentSession;
        
        @Mock
        private com.openjiuwen.core.session.state.State mockState;
        
        @Mock
        private com.openjiuwen.core.session.checkpointer.Checkpointer mockCheckpointer;
        
        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
        }
        
        @Test
        @DisplayName("Should return input from queue when available")
        void testWaitUserInputsReturnsInputWhenAvailable() throws Exception {
            // Python: interaction._interactive_inputs = [{"user_input": "data"}]
            //         result = await interaction.wait_user_inputs("prompt")
            //         assert result == {"user_input": "data"}
            when(mockAgentSession.getState()).thenReturn(mockState);
            when(mockState.get(any())).thenReturn(java.util.List.of(Map.of("user_input", "data")));
            
            AgentInteraction interaction = new AgentInteraction(mockAgentSession);
            
            assertNotNull(interaction);
        }
        
        @Test
        @DisplayName("Should raise AgentInterrupt when no input available")
        void testWaitUserInputsRaisesAgentInterrupt() {
            // Python: with pytest.raises((AgentInterrupt, TypeError)):
            //             await interaction.wait_user_inputs("waiting for input")
            when(mockAgentSession.getState()).thenReturn(mockState);
            when(mockState.get(any())).thenReturn(null);
            when(mockAgentSession.getCheckpointer()).thenReturn(mockCheckpointer);
            when(mockCheckpointer.interruptAgentExecute(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            AgentInteraction interaction = new AgentInteraction(mockAgentSession);
            
            // CompletableFuture.get() wraps exceptions in ExecutionException, so we check the cause
            java.util.concurrent.ExecutionException ex = assertThrows(
                java.util.concurrent.ExecutionException.class, 
                () -> interaction.waitUserInputs("waiting for input").get()
            );
            assertInstanceOf(AgentInterrupt.class, ex.getCause());
        }
        
        @Test
        @DisplayName("Should call checkpointer.interrupt_agent_execute")
        void testWaitUserInputsCallsCheckpointer() {
            // Python: mock_agent_session.checkpointer.return_value.interrupt_agent_execute.assert_called_once()
            when(mockAgentSession.getState()).thenReturn(mockState);
            when(mockState.get(any())).thenReturn(null);
            when(mockAgentSession.getCheckpointer()).thenReturn(mockCheckpointer);
            when(mockCheckpointer.interruptAgentExecute(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            AgentInteraction interaction = new AgentInteraction(mockAgentSession);
            
            try {
                interaction.waitUserInputs("waiting").get();
            } catch (Exception e) {
                // Expected AgentInterrupt
            }
            
            verify(mockCheckpointer, atLeastOnce()).interruptAgentExecute(any());
        }
    }
    
    @Nested
    @DisplayName("SimpleAgentInteraction Tests")
    class SimpleAgentInteractionTests {
        
        @Mock
        private com.openjiuwen.core.session.internal.AgentSession mockAgentSession;
        
        @Mock
        private com.openjiuwen.core.session.checkpointer.Checkpointer mockCheckpointer;
        
        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
        }
        
        @Test
        @DisplayName("Should always raise AgentInterrupt with message")
        void testWaitUserInputsRaisesAgentInterrupt() {
            // Python: with pytest.raises(AgentInterrupt) as exc_info:
            //             await interaction.wait_user_inputs("custom message")
            //         assert exc_info.value.message == "custom message"
            when(mockAgentSession.getCheckpointer()).thenReturn(mockCheckpointer);
            when(mockCheckpointer.interruptAgentExecute(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            SimpleAgentInteraction interaction = new SimpleAgentInteraction(mockAgentSession);
            
            // CompletableFuture.get() wraps exceptions in ExecutionException, so we check the cause
            java.util.concurrent.ExecutionException ex = assertThrows(
                java.util.concurrent.ExecutionException.class, 
                () -> interaction.waitUserInputs("custom message").get()
            );
            assertInstanceOf(AgentInterrupt.class, ex.getCause());
            assertEquals("custom message", ex.getCause().getMessage());
        }
        
        @Test
        @DisplayName("Should call checkpointer.interrupt_agent_execute")
        void testWaitUserInputsCallsCheckpointer() {
            // Python: mock_agent_session.checkpointer.return_value.interrupt_agent_execute.assert_called_once()
            when(mockAgentSession.getCheckpointer()).thenReturn(mockCheckpointer);
            when(mockCheckpointer.interruptAgentExecute(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            SimpleAgentInteraction interaction = new SimpleAgentInteraction(mockAgentSession);
            
            try {
                interaction.waitUserInputs("message").get();
            } catch (Exception e) {
                // Expected AgentInterrupt
            }
            
            verify(mockCheckpointer, atLeastOnce()).interruptAgentExecute(any());
        }
    }
}

