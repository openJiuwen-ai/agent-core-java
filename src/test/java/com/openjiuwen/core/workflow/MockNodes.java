/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock workflow nodes for workflow tests.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.workflow.mock_nodes}.</p>
 */
public final class MockNodes {

    private MockNodes() {
    }

    /**
     * <p>Mirrors Python's {@code MockNodeBase}.</p>
     */
    public static class MockNodeBase extends WorkflowComponent {
        protected final String nodeId;

        public MockNodeBase() {
            this("");
        }

        public MockNodeBase(String nodeId) {
            this.nodeId = nodeId != null ? nodeId : "";
        }

        public String getNodeId() {
            return nodeId;
        }
    }

    /**
     * <p>Mirrors Python's {@code MockStartNode}.</p>
     */
    public static class MockStartNode extends Start {
        private final String nodeId;

        public MockStartNode(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * <p>Mirrors Python's {@code MockEndNode}.</p>
     */
    public static class MockEndNode extends End {
        private final String nodeId;

        public MockEndNode(String nodeId) {
            super(Map.of("responseTemplate", "hello:{{end_input}}"));
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * <p>Mirrors Python's {@code Node1}.</p>
     */
    public static class Node1 extends MockNodeBase {
        public Node1(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * <p>Mirrors Python's {@code CountNode}.</p>
     */
    public static class CountNode extends MockNodeBase {
        private int times = 0;

        public CountNode(String nodeId) {
            super(nodeId);
        }

        public int getTimes() {
            return times;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            times++;
            return Map.of("count", times);
        }
    }

    /**
     * <p>Mirrors Python's {@code SlowNode}.</p>
     */
    public static class SlowNode extends MockNodeBase {
        private final int waitSeconds;

        public SlowNode(String nodeId, int waitSeconds) {
            super(nodeId);
            this.waitSeconds = waitSeconds;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            sleepMillis(waitSeconds * 1000L);
            return inputs;
        }
    }

    /**
     * <p>Mirrors Python's {@code StreamNode}.</p>
     */
    public static class StreamNode extends MockNodeBase {
        private final List<Map<String, Object>> datas;

        public StreamNode(String nodeId, List<Map<String, Object>> datas) {
            super(nodeId);
            this.datas = datas != null ? datas : List.of();
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            for (Map<String, Object> data : datas) {
                sleepMillis(100L);
                session.writeCustomStream(data);
            }
            return inputs;
        }
    }

    /**
     * <p>Mirrors Python's {@code StreamNodeWithSubWorkflow}.</p>
     */
    public static class StreamNodeWithSubWorkflow extends MockNodeBase {
        private final Workflow subWorkflow;

        public StreamNodeWithSubWorkflow(String nodeId, Workflow subWorkflow) {
            super(nodeId);
            this.subWorkflow = subWorkflow;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            if (subWorkflow != null) {
                Iterator<WorkflowChunk> chunks = subWorkflow.streamSubWorkflow(
                        Map.of("a", 1, "b", "haha"), session.getInner(), context);
                while (chunks.hasNext()) {
                    Object chunk = chunks.next();
                    if (chunk instanceof Map<?, ?> map) {
                        session.writeCustomStream(toStringObjectMap(map));
                    } else {
                        session.writeCustomStream(Map.of("chunk", chunk));
                    }
                }
            }
            return inputs;
        }
    }

    /**
     * <p>Mirrors Python's {@code MockStartNode4Cp}.</p>
     */
    public static class MockStartNode4Cp extends Start {
        private final String nodeId;
        private int runtime = 0;

        public MockStartNode4Cp(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public int getRuntime() {
            return runtime;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            runtime++;
            Object value = session.getGlobalState("a");
            if (value != null) {
                throw new RuntimeException("value is not None");
            }
            System.out.println("start: output = " + inputs);
            session.updateGlobalState(Map.of("a", 10));
            return inputs;
        }
    }

    /**
     * <p>Mirrors Python's {@code Node4Cp}.</p>
     */
    public static class Node4Cp extends MockNodeBase {
        private int runtime = 0;

        public Node4Cp(String nodeId) {
            super(nodeId);
        }

        public int getRuntime() {
            return runtime;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            runtime++;
            Object value = session.getGlobalState("a");
            if (!(value instanceof Number number) || number.intValue() < 20) {
                throw new RuntimeException("value < 20");
            }
            return inputs;
        }
    }

    /**
     * <p>Mirrors Python's {@code AddTenNode4Cp}.</p>
     */
    public static class AddTenNode4Cp extends WorkflowComponent {
        private final String nodeId;
        private boolean raiseException = true;

        public AddTenNode4Cp(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public boolean isRaiseException() {
            return raiseException;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = asMap(inputs);
            if (raiseException) {
                raiseException = false;
                throw new RuntimeException("inner error: " + inputMap.get("source"));
            }
            raiseException = true;
            return Map.of("result", asInt(inputMap.get("source")) + 10);
        }
    }

    /**
     * <p>Mirrors Python's {@code InteractiveNode4Cp}.</p>
     */
    public static class InteractiveNode4Cp extends MockNodeBase {
        public InteractiveNode4Cp(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result1 = session.interact("Please enter any key");
            System.out.println(result1);
            return session.interact("Please enter any key");
        }
    }

    /**
     * <p>Mirrors Python's {@code InteractiveNode4StreamCp}.</p>
     */
    public static class InteractiveNode4StreamCp extends MockNodeBase {
        public InteractiveNode4StreamCp(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = session.interact("Please enter any key");
            session.writeStream(new OutputSchema("output", 0, new Object[]{nodeId, result}));
            return result;
        }
    }

    /**
     * <p>Mirrors Python's {@code InteractiveNode4Collect}.</p>
     */
    public static class InteractiveNode4Collect extends MockNodeBase {
        public InteractiveNode4Collect(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = session.interact("Please enter any key");
            System.out.println(result);
            return result;
        }
    }

    /**
     * <p>Mirrors Python's {@code StreamCompNode}.</p>
     */
    public static class StreamCompNode extends MockNodeBase {
        public StreamCompNode(String nodeId) {
            super(nodeId);
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            if (inputs == null) {
                frames.add(1);
            } else {
                int value = asInt(asMap(inputs).get("value"));
                for (int i = 1; i < 3; i++) {
                    frames.add(Map.of("value", i * value));
                }
            }
            return frames.iterator();
        }
    }

    /**
     * <p>Mirrors Python's {@code CollectCompNode}.</p>
     */
    public static class CollectCompNode extends MockNodeBase {
        public CollectCompNode(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = asMap(inputs);
            int result = 0;
            Iterator<?> iterator = toIterator(inputMap.get("value"));
            while (iterator.hasNext()) {
                Object value = iterator.next();
                if (value == null) {
                    continue;
                }
                result += asInt(value);
            }
            return Map.of("value", result);
        }
    }

    /**
     * <p>Mirrors Python's {@code TransformCompNode}.</p>
     */
    public static class TransformCompNode extends MockNodeBase {
        public TransformCompNode(String nodeId) {
            super(nodeId);
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = asMap(inputs);
            List<Object> frames = new ArrayList<>();
            Iterator<?> iterator = toIterator(inputMap.get("value"));
            while (iterator.hasNext()) {
                frames.add(Map.of("value", iterator.next()));
            }
            return frames.iterator();
        }
    }

    /**
     * <p>Mirrors Python's {@code MultiCollectCompNode}.</p>
     */
    public static class MultiCollectCompNode extends MockNodeBase {
        public MultiCollectCompNode(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            int aCollect = 0;
            int bCollect = 0;
            Iterator<?> iterator = toIterator(asMap(inputs).get("value"));
            while (iterator.hasNext()) {
                Map<String, Object> value = asMap(iterator.next());
                Object aValue = value.get("a");
                if (aValue != null) {
                    aCollect += asInt(aValue);
                }
                Object bValue = value.get("b");
                if (bValue != null) {
                    bCollect += asInt(bValue);
                }
            }
            return Map.of("a_collect", aCollect, "b_collect", bCollect);
        }
    }

    /**
     * <p>Mirrors Python's {@code CommonNode}.</p>
     */
    public static class CommonNode extends WorkflowComponent {
        private final String nodeId;

        public CommonNode(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            frames.add(invoke(inputs, session, context));
            return frames.iterator();
        }
    }

    /**
     * <p>Mirrors Python's {@code AddTenNode}.</p>
     */
    public static class AddTenNode extends WorkflowComponent {
        private final String nodeId;
        private final Map<String, Object> checkMap;

        public AddTenNode(String nodeId) {
            this(nodeId, null);
        }

        public AddTenNode(String nodeId, Map<String, Object> checkMap) {
            this.nodeId = nodeId;
            this.checkMap = checkMap;
        }

        public String getNodeId() {
            return nodeId;
        }

        public static Object generateValue(NodeSessionApi session, Object value) {
            if (value instanceof String str && SessionUtils.isRefPath(str)) {
                return session.getGlobalState(SessionUtils.extractOriginKey(str));
            }
            return value;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = asMap(inputs);
            if (checkMap != null) {
                for (Map.Entry<String, Object> entry : checkMap.entrySet()) {
                    Object expected = generateValue(session, entry.getValue());
                    Object actual = inputMap.get(entry.getKey());
                    if (!java.util.Objects.equals(actual, expected)) {
                        throw new AssertionError(
                                "Expected " + entry.getKey() + "=" + expected + ", got " + actual);
                    }
                }
            }
            return Map.of("result", asInt(inputMap.get("source")) + 10);
        }
    }

    /**
     * <p>Mirrors Python's {@code MockStreamNode}.</p>
     */
    public static class MockStreamNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            frames.add(inputs);
            return frames.iterator();
        }
    }

    /**
     * <p>Mirrors Python's {@code ComputeComponent2}.</p>
     */
    public static class ComputeComponent2 implements ComponentComposable {
        @Override
        public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
            graph.addNode(nodeId, toExecutable(), waitForAll);
        }

        @Override
        public Executable<?, ?> toExecutable() {
            return new ComputeExecutor2();
        }
    }

    /**
     * <p>Mirrors Python's {@code ComputeExecutor2}.</p>
     */
    public static class ComputeExecutor2 extends ComponentExecutable {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = asMap(inputs);
            return Map.of("result", asInt(inputMap.get("a")) + asInt(inputMap.get("b")));
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = asMap(inputs);
            List<Object> frames = new ArrayList<>();
            Object inputsA = inputMap.get("a");
            if (inputsA instanceof List<?> list) {
                frames.add(Map.of("b", inputMap.get("b")));
                frames.add(Map.of("op", "+"));
                for (Object a : list) {
                    frames.add(Map.of("a", a));
                    frames.add(Map.of("result", asInt(a) + asInt(inputMap.get("b"))));
                }
            } else {
                frames.add(Map.of("a", inputsA));
                frames.add(Map.of("op", "+"));
                frames.add(Map.of("b", inputMap.get("b")));
                frames.add(Map.of("result", asInt(inputsA) + asInt(inputMap.get("b"))));
            }
            return frames.iterator();
        }

        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            int result = 0;
            for (Map.Entry<String, Object> entry : asMap(inputs).entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nested) {
                    for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                        result += collectField(toIterator(nestedEntry.getValue()), String.valueOf(nestedEntry.getKey()));
                    }
                } else {
                    result += collectField(toIterator(value), "result");
                }
            }
            return Map.of("result_collect", result);
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            for (Map.Entry<String, Object> entry : asMap(inputs).entrySet()) {
                String dataSourceKey = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nested) {
                    for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                        Iterator<?> iterator = toIterator(nestedEntry.getValue());
                        while (iterator.hasNext()) {
                            frames.add(Map.of(String.valueOf(nestedEntry.getKey()), iterator.next()));
                        }
                    }
                } else {
                    Iterator<?> iterator = toIterator(value);
                    while (iterator.hasNext()) {
                        frames.add(Map.of(dataSourceKey, iterator.next()));
                    }
                }
            }
            return frames.iterator();
        }

        private static int collectField(Iterator<?> iterator, String dataKey) {
            int result = 0;
            while (iterator.hasNext()) {
                Object data = iterator.next();
                if ("result".equals(dataKey)) {
                    result += asInt(data);
                }
            }
            return result;
        }
    }

    /**
     * <p>Mirrors Python's {@code DualAbilityWithErrorComponent}.</p>
     */
    public static class DualAbilityWithErrorComponent implements ComponentComposable {
        private final boolean errorInStream;
        private final boolean errorInTransform;

        public DualAbilityWithErrorComponent() {
            this(false, false);
        }

        public DualAbilityWithErrorComponent(boolean errorInStream) {
            this(errorInStream, false);
        }

        public DualAbilityWithErrorComponent(boolean errorInStream, boolean errorInTransform) {
            this.errorInStream = errorInStream;
            this.errorInTransform = errorInTransform;
        }

        @Override
        public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
            graph.addNode(nodeId, toExecutable(), waitForAll);
        }

        @Override
        public Executable<?, ?> toExecutable() {
            return new DualAbilityWithErrorExecutor(errorInStream, errorInTransform);
        }
    }

    /**
     * <p>Mirrors Python's {@code DualAbilityWithErrorExecutor}.</p>
     */
    public static class DualAbilityWithErrorExecutor extends ComponentExecutable {
        private final boolean errorInStream;
        private final boolean errorInTransform;

        public DualAbilityWithErrorExecutor() {
            this(false, false);
        }

        public DualAbilityWithErrorExecutor(boolean errorInStream, boolean errorInTransform) {
            this.errorInStream = errorInStream;
            this.errorInTransform = errorInTransform;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = asMap(inputs);
            return Map.of("result", asInt(inputMap.getOrDefault("a", 0)) + asInt(inputMap.getOrDefault("b", 0)));
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            if (errorInStream) {
                throw new RuntimeException("Simulated error in STREAM ability");
            }
            Map<String, Object> inputMap = asMap(inputs);
            int a = asInt(inputMap.getOrDefault("a", 0));
            int b = asInt(inputMap.getOrDefault("b", 0));
            return List.<Object>of(
                    Map.of("a", a),
                    Map.of("op", "+"),
                    Map.of("b", b),
                    Map.of("result", a + b)).iterator();
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            if (errorInTransform) {
                throw new RuntimeException("Simulated error in TRANSFORM ability");
            }
            List<Object> frames = new ArrayList<>();
            for (Map.Entry<String, Object> entry : asMap(inputs).entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> nested) {
                    for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                        Iterator<?> iterator = toIterator(nestedEntry.getValue());
                        while (iterator.hasNext()) {
                            frames.add(Map.of(String.valueOf(nestedEntry.getKey()), iterator.next()));
                        }
                    }
                } else {
                    Iterator<?> iterator = toIterator(entry.getValue());
                    while (iterator.hasNext()) {
                        frames.add(Map.of(entry.getKey(), iterator.next()));
                    }
                }
            }
            return frames.iterator();
        }
    }

    /**
     * <p>Mirrors Python's {@code MockNodeWithAllAbility}.</p>
     */
    public static class MockNodeWithAllAbility extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            for (String key : asMap(inputs).keySet()) {
                for (int index = 0; index < 5; index++) {
                    frames.add(Map.of(key, index));
                }
            }
            return frames.iterator();
        }

        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> results = new ArrayList<>();
            for (Map.Entry<String, Object> entry : asMap(inputs).entrySet()) {
                Iterator<?> iterator = toIterator(entry.getValue());
                while (iterator.hasNext()) {
                    results.add(Map.of(entry.getKey(), iterator.next()));
                }
            }
            return Map.of("collect_result", results);
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            for (Map.Entry<String, Object> entry : asMap(inputs).entrySet()) {
                Iterator<?> iterator = toIterator(entry.getValue());
                while (iterator.hasNext()) {
                    frames.add(Map.of(entry.getKey(), iterator.next()));
                }
            }
            return frames.iterator();
        }
    }

    /**
     * <p>Mirrors Python's {@code MockIntentNode}.</p>
     */
    public static class MockIntentNode extends WorkflowComponent {
        private final Object classificationId;
        private final BranchRouter router = new BranchRouter();

        public MockIntentNode(Object classificationId) {
            this.classificationId = classificationId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            router.setSession(session);
            return Map.of("classification_id", classificationId);
        }

        @Override
        public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
            graph.addNode(nodeId, toExecutable(), waitForAll);
            graph.addConditionalEdges(nodeId, router);
        }

        public void addBranch(Object condition, Object target, String branchId) {
            Object normalizedTarget = target instanceof String ? List.of(target) : target;
            router.addBranch(condition, normalizedTarget, branchId);
        }

        public void addBranch(Object condition, Object target) {
            addBranch(condition, target, null);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map<?, ?> map) {
            return toStringObjectMap(map);
        }
        throw new IllegalArgumentException("Expected map input, got " + value.getClass().getSimpleName());
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Iterator<?> toIterator(Object value) {
        if (value == null) {
            return Collections.emptyIterator();
        }
        if (value instanceof Iterator<?> iterator) {
            return iterator;
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator();
        }
        return List.of(value).iterator();
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
