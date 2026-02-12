/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BaseInteraction and AgentInterrupt classes.
 */
class BaseInteractionTest {
    
    /**
     * Mock state accessor for testing.
     */
    static class MockStateAccessor implements BaseInteraction.SessionStateAccessor {
        private Map<String, Object> state = new HashMap<>();
        
        MockStateAccessor() {}
        
        MockStateAccessor(Map<String, Object> initialState) {
            if (initialState != null) {
                this.state = new HashMap<>(initialState);
            }
        }
        
        @Override
        public Object get(String key) {
            return state.get(key);
        }
        
        @Override
        public void update(Map<String, Object> data) {
            state.putAll(data);
        }
        
        public Map<String, Object> getState() {
            return state;
        }
    }
    
    /**
     * Concrete implementation of BaseInteraction for testing.
     */
    static class ConcreteInteraction extends BaseInteraction {
        ConcreteInteraction(SessionStateAccessor stateAccessor) {
            super(stateAccessor);
        }
        
        ConcreteInteraction(SessionStateAccessor stateAccessor, Object defaultInput) {
            super(stateAccessor, defaultInput);
        }
        
        @Override
        public CompletableFuture<Object> waitUserInputs(Object value) {
            return CompletableFuture.completedFuture(value);
        }
    }
    
    @Nested
    @DisplayName("AgentInterrupt Tests")
    class AgentInterruptTests {
        
        @Test
        @DisplayName("construction with message")
        void testConstructionWithMessage() {
            AgentInterrupt interrupt = new AgentInterrupt("Please provide input");
            assertEquals("Please provide input", interrupt.getInterruptMessage());
        }
        
        @Test
        @DisplayName("is an Exception")
        void testIsException() {
            AgentInterrupt interrupt = new AgentInterrupt("test");
            assertInstanceOf(Exception.class, interrupt);
        }
        
        @Test
        @DisplayName("can be raised and caught")
        void testCanBeRaisedAndCaught() {
            AgentInterrupt caught = assertThrows(AgentInterrupt.class, () -> {
                throw new AgentInterrupt("Need input");
            });
            assertEquals("Need input", caught.getInterruptMessage());
        }
        
        @Test
        @DisplayName("message can be null")
        void testMessageCanBeNull() {
            AgentInterrupt interrupt = new AgentInterrupt(null);
            assertNull(interrupt.getInterruptMessage());
        }
    }
    
    @Nested
    @DisplayName("BaseInteraction Tests")
    class BaseInteractionTests {
        
        private MockStateAccessor stateAccessor;
        
        @BeforeEach
        void setUp() {
            stateAccessor = new MockStateAccessor();
        }
        
        @Test
        @DisplayName("construction with no inputs")
        void testConstructionWithNoInputs() {
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor);
            assertNull(interaction.interactiveInputs);
            assertNull(interaction.latestInteractiveInput);
            assertEquals(0, interaction.idx);
        }
        
        @Test
        @DisplayName("construction with default input")
        void testConstructionWithDefaultInput() {
            Map<String, Object> defaultInput = Map.of("user_response", "yes");
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor, defaultInput);
            
            assertNotNull(interaction.interactiveInputs);
            assertEquals(1, interaction.interactiveInputs.size());
            assertEquals(defaultInput, interaction.interactiveInputs.get(0));
            assertEquals(defaultInput, interaction.latestInteractiveInput);
        }
        
        @Test
        @DisplayName("init interactive inputs from session state")
        void testInitInteractiveInputsFromSessionState() {
            List<Object> inputs = new ArrayList<>();
            inputs.add(Map.of("input1", "value1"));
            stateAccessor = new MockStateAccessor(Map.of(Constant.INTERACTIVE_INPUT, inputs));
            
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor);
            
            assertNotNull(interaction.interactiveInputs);
            assertEquals(1, interaction.interactiveInputs.size());
            assertEquals(Map.of("input1", "value1"), interaction.interactiveInputs.get(0));
            assertEquals(Map.of("input1", "value1"), interaction.latestInteractiveInput);
        }
        
        @Test
        @DisplayName("init interactive inputs merges with default")
        void testInitInteractiveInputsMergesWithDefault() {
            List<Object> inputs = new ArrayList<>();
            inputs.add(Map.of("input1", "value1"));
            stateAccessor = new MockStateAccessor(Map.of(Constant.INTERACTIVE_INPUT, inputs));
            
            Map<String, Object> defaultInput = Map.of("input2", "value2");
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor, defaultInput);
            
            // Should concatenate: session inputs + default input
            assertEquals(2, interaction.interactiveInputs.size());
            assertEquals(Map.of("input1", "value1"), interaction.interactiveInputs.get(0));
            assertEquals(Map.of("input2", "value2"), interaction.interactiveInputs.get(1));
            // Latest should be the last item
            assertEquals(defaultInput, interaction.latestInteractiveInput);
        }
        
        @Test
        @DisplayName("init interactive inputs updates session state")
        void testInitInteractiveInputsUpdatesSessionState() {
            List<Object> inputs = new ArrayList<>();
            inputs.add(Map.of("input1", "value1"));
            stateAccessor = new MockStateAccessor(Map.of(Constant.INTERACTIVE_INPUT, inputs));
            
            Map<String, Object> defaultInput = Map.of("input2", "value2");
            new ConcreteInteraction(stateAccessor, defaultInput);
            
            // Session state should be updated with merged inputs
            @SuppressWarnings("unchecked")
            List<Object> updatedInputs = (List<Object>) stateAccessor.get(Constant.INTERACTIVE_INPUT);
            assertEquals(2, updatedInputs.size());
        }
        
        @Test
        @DisplayName("get next interactive input returns inputs in order")
        void testGetNextInteractiveInputReturnsInputsInOrder() {
            List<Object> inputs = new ArrayList<>();
            inputs.add("input1");
            inputs.add("input2");
            inputs.add("input3");
            stateAccessor = new MockStateAccessor(Map.of(Constant.INTERACTIVE_INPUT, inputs));
            
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor);
            
            assertEquals("input1", interaction.getNextInteractiveInput());
            assertEquals("input2", interaction.getNextInteractiveInput());
            assertEquals("input3", interaction.getNextInteractiveInput());
        }
        
        @Test
        @DisplayName("get next interactive input returns null when exhausted")
        void testGetNextInteractiveInputReturnsNullWhenExhausted() {
            List<Object> inputs = new ArrayList<>();
            inputs.add("input1");
            stateAccessor = new MockStateAccessor(Map.of(Constant.INTERACTIVE_INPUT, inputs));
            
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor);
            
            assertEquals("input1", interaction.getNextInteractiveInput());
            assertNull(interaction.getNextInteractiveInput());
            assertNull(interaction.getNextInteractiveInput());
        }
        
        @Test
        @DisplayName("get next interactive input with empty inputs")
        void testGetNextInteractiveInputWithEmptyInputs() {
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor);
            assertNull(interaction.getNextInteractiveInput());
        }
        
        @Test
        @DisplayName("idx increments on each get")
        void testIdxIncrementsOnEachGet() {
            List<Object> inputs = new ArrayList<>();
            inputs.add("a");
            inputs.add("b");
            inputs.add("c");
            stateAccessor = new MockStateAccessor(Map.of(Constant.INTERACTIVE_INPUT, inputs));
            
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor);
            
            assertEquals(0, interaction.idx);
            interaction.getNextInteractiveInput();
            assertEquals(1, interaction.idx);
            interaction.getNextInteractiveInput();
            assertEquals(2, interaction.idx);
            interaction.getNextInteractiveInput();
            assertEquals(3, interaction.idx);
        }
        
        @Test
        @DisplayName("non-list interactive inputs ignored")
        void testNonListInteractiveInputsIgnored() {
            stateAccessor = new MockStateAccessor(Map.of(Constant.INTERACTIVE_INPUT, "not_a_list"));
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor);
            // Non-list should be ignored
            assertNull(interaction.interactiveInputs);
        }
        
        @Test
        @DisplayName("user latest input default implementation")
        void testUserLatestInputDefaultImplementation() {
            ConcreteInteraction interaction = new ConcreteInteraction(stateAccessor);
            // Default implementation should not throw
            assertDoesNotThrow(() -> interaction.userLatestInput("value").join());
        }
    }
}

