package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SingleDimUpdaterTest {

    @Test
    void bindDelegatesTargetsFromArgumentsOrConfig() {
        BaseOptimizer optimizer = Mockito.mock(BaseOptimizer.class);
        when(optimizer.bind(any(), any(), any())).thenReturn(2);
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        int count = updater.bind(Map.of("op1", new Object()), null, Map.of("targets", List.of("system_prompt")));

        assertEquals(2, count);
        Mockito.verify(optimizer).bind(any(), Mockito.eq(List.of("system_prompt")), any());
    }

    @Test
    void updateRunsTrajectoryBackwardStepInOrder() {
        BaseOptimizer optimizer = Mockito.mock(BaseOptimizer.class);
        Updates expected = Updates.of("op1", "system_prompt", "prompt");
        when(optimizer.step()).thenReturn(expected);

        SingleDimUpdater updater = new SingleDimUpdater(optimizer);
        Trajectory trajectory = Trajectory.builder().caseId("c1").executionId("e1").steps(List.of()).build();
        EvaluatedCase evaluatedCase = EvaluatedCase.builder().score(0.5).build();

        Updates result = updater.update(List.of(trajectory), List.of(evaluatedCase), Map.of());

        InOrder inOrder = Mockito.inOrder(optimizer);
        inOrder.verify(optimizer).addTrajectory(trajectory);
        inOrder.verify(optimizer).backward(List.of(evaluatedCase));
        inOrder.verify(optimizer).step();
        assertSame(expected, result);
    }

    @Test
    void requiresForwardDataDelegatesToOptimizer() {
        BaseOptimizer optimizer = Mockito.mock(BaseOptimizer.class);
        when(optimizer.requiresForwardData()).thenReturn(false);

        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        assertEquals(false, updater.requiresForwardData());
    }

    @Test
    void updateHandlesEmptyTrajectories() {
        BaseOptimizer optimizer = Mockito.mock(BaseOptimizer.class);
        when(optimizer.step()).thenReturn(new Updates());

        SingleDimUpdater updater = new SingleDimUpdater(optimizer);
        Updates result = updater.update(List.of(), List.of(), Map.of());

        Mockito.verify(optimizer, Mockito.never()).addTrajectory(any());
        Mockito.verify(optimizer).backward(List.of());
        Mockito.verify(optimizer).step();
        assertEquals(0, result.size());
    }

    @Test
    void getStateReturnsEmptyMapAndLoadStateIsNoOp() {
        BaseOptimizer optimizer = Mockito.mock(BaseOptimizer.class);
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        assertEquals(Map.of(), updater.getState());
        assertDoesNotThrow(() -> updater.loadState(Map.of("ignored", "value")));
    }
}
