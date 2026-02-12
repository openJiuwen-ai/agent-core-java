package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tool Decorator测试
 * 
 * <p>严格对齐Python测试: test_tool_decorator.py
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
class ToolDecoratorTest {

    /**
     * 测试基本的@tool装饰器功能（使用预定义ToolCard）
     * 对应Python: test_tool
     */
    @Test
    @DisplayName("测试基本@tool装饰器功能")
    void testToolWithPredefinedCard() throws ExecutionException, InterruptedException {
        // 创建ToolCard
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> paramA = new HashMap<>();
        paramA.put("description", "first arg");
        paramA.put("type", "integer");
        properties.put("a", paramA);
        
        Map<String, Object> paramB = new HashMap<>();
        paramB.put("description", "second arg");
        paramB.put("type", "integer");
        properties.put("b", paramB);
        
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("a", "b"));
        
        ToolCard card = ToolCard.builder()
            .name("local_sub")
            .description("local function for sub")
            .inputParams(inputParams)
            .build();
        
        // 创建LocalFunction（模拟@tool装饰器）
        LocalFunction subTool = ToolDecorator.create(card, (inputs) -> {
            int a = ((Number) inputs.get("a")).intValue();
            int b = ((Number) inputs.get("b")).intValue();
            return a - b;
        });
        
        // 测试invoke
        Map<String, Object> inputs = Map.of("a", 5, "b", 1);
        Object result = subTool.invoke(inputs, new HashMap<>()).get();
        
        assertEquals(4, result);
        assertEquals("local_sub", subTool.getCard().getName());
        assertEquals("local function for sub", subTool.getCard().getDescription());
        
        // 测试tool_info
        ToolInfo toolInfo = subTool.getCard().toolInfo();
        assertEquals("local_sub", toolInfo.name());
        assertEquals("local function for sub", toolInfo.description());
        assertNotNull(toolInfo.parameters());
    }
    
    /**
     * 测试带复杂输入的工具
     * 对应Python: test_annotated (Java简化版，因为Java无Annotated)
     */
    @Test
    @DisplayName("测试带复杂输入的工具")
    void testToolWithAutoSchema() throws ExecutionException, InterruptedException {
        // 创建简单的工具卡片
        ToolCard card = ToolCard.builder()
            .name("summarize")
            .description("汇总商品信息")
            .inputParams(new HashMap<>())
            .build();
        
        LocalFunction summarizeTool = ToolDecorator.create(card, (inputs) -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> products = (List<Map<String, Object>>) inputs.get("products");
            
            double total = products.stream()
                .mapToDouble(product -> {
                    Number price = (Number) product.get("price");
                    Number sales = (Number) product.get("sales");
                    return price.doubleValue() * sales.doubleValue();
                })
                .sum();
            
            return total;
        });
        
        // 准备输入
        Map<String, Object> input = new HashMap<>();
        input.put("title", "水果信息汇总");
        
        Map<String, Object> product1 = new HashMap<>();
        product1.put("name", "苹果");
        product1.put("sales", 2);
        product1.put("price", 1.5);
        product1.put("is_season", true);
        product1.put("color", List.of("red", "yellow"));
        product1.put("note", Map.of("key", "备注", "value", 10));
        
        Map<String, Object> product2 = new HashMap<>();
        product2.put("name", "香蕉");
        product2.put("sales", 4);
        product2.put("price", 1.0);
        product2.put("is_season", false);
        product2.put("color", List.of("yellow"));
        product2.put("note", Map.of("key", "备注", "value", 20));
        
        input.put("products", List.of(product1, product2));
        
        // 测试invoke
        Object result = summarizeTool.invoke(input, new HashMap<>()).get();
        
        assertEquals(7.0, (Double) result, 0.001);
        assertEquals("summarize", summarizeTool.getCard().getName());
        assertEquals("汇总商品信息", summarizeTool.getCard().getDescription());
    }
    
    /**
     * 测试ToolInfo的生成
     * 对应Python: test_tool中的tool_info验证
     */
    @Test
    @DisplayName("测试ToolInfo生成")
    void testToolInfo() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        params.put("properties", new HashMap<>());
        
        ToolCard card = ToolCard.builder()
            .name("test_tool")
            .description("Test tool description")
            .inputParams(params)
            .build();
        
        ToolInfo info = card.toolInfo();
        
        assertEquals("function", info.type());
        assertEquals("test_tool", info.name());
        assertEquals("Test tool description", info.description());
        assertNotNull(info.parameters());
    }
    
    /**
     * 测试fromMethod自动schema提取
     * 对应Python: @tool无card参数时自动提取schema
     * 
     * 注意：Java反射获取的参数名默认是arg0, arg1（除非编译时开启-parameters选项），
     * 因此需要使用反射获取实际参数名来构建输入Map。
     */
    @Test
    @DisplayName("测试fromMethod自动schema提取")
    void testFromMethodAutoExtract() throws Exception {
        Method method = TestMethods.class.getMethod("addWrapper", Integer.class, Integer.class);
        
        LocalFunction tool = ToolDecorator.fromMethod(method, new TestMethods());
        
        assertEquals("addWrapper", tool.getCard().getName());
        assertNotNull(tool.getCard().getDescription());
        
        // 验证schema已提取（至少不为空Map）
        Object inputParams = tool.getCard().getInputParams();
        assertNotNull(inputParams);
        
        if (inputParams instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramsMap = (Map<String, Object>) inputParams;
            // 如果schema提取成功，应该有properties
            if (paramsMap.containsKey("properties")) {
                assertNotNull(paramsMap.get("properties"));
            }
        }
        
        // 获取反射参数名（Java默认是arg0, arg1）
        String param0Name = method.getParameters()[0].getName();
        String param1Name = method.getParameters()[1].getName();
        
        // 验证执行 - 使用反射获取的实际参数名
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(param0Name, 3);
        inputs.put(param1Name, 5);
        Object result = tool.invoke(inputs, new HashMap<>()).get();
        assertEquals(8, result);
    }
    
    /**
     * 测试createWithOverride覆盖属性
     * 对应Python: @tool(card=existing_card, name="override")
     */
    @Test
    @DisplayName("测试createWithOverride覆盖属性")
    void testCreateWithOverride() throws ExecutionException, InterruptedException {
        ToolCard originalCard = ToolCard.builder()
            .name("original_name")
            .description("original description")
            .inputParams(Map.of("type", "object"))
            .build();
        
        LocalFunction tool = ToolDecorator.createWithOverride(
            originalCard,
            (inputs) -> "result",
            "overridden_name",       // 覆盖名称
            "overridden description", // 覆盖描述
            null                      // 保留原始inputParams
        );
        
        assertEquals("overridden_name", tool.getCard().getName());
        assertEquals("overridden description", tool.getCard().getDescription());
        assertEquals(Map.of("type", "object"), tool.getCard().getInputParams());
    }
    
    /**
     * 测试Builder模式
     * 对应Python: @tool多种参数组合
     */
    @Test
    @DisplayName("测试Builder模式创建工具")
    void testBuilder() throws ExecutionException, InterruptedException {
        LocalFunction tool = ToolDecorator.builder()
            .name("builder_tool")
            .description("Tool created with builder")
            .inputParams(Map.of("type", "object", "properties", Map.of()))
            .function(inputs -> "builder result")
            .build();
        
        assertEquals("builder_tool", tool.getCard().getName());
        assertEquals("Tool created with builder", tool.getCard().getDescription());
        
        Object result = tool.invoke(Map.of(), new HashMap<>()).get();
        assertEquals("builder result", result);
    }
    
    /**
     * 测试Builder使用预构建card
     */
    @Test
    @DisplayName("测试Builder使用预构建card")
    void testBuilderWithCard() throws ExecutionException, InterruptedException {
        ToolCard card = ToolCard.builder()
            .name("preset_card")
            .description("Preset description")
            .inputParams(Map.of())
            .build();
        
        LocalFunction tool = ToolDecorator.builder()
            .card(card)
            .function(inputs -> 42)
            .build();
        
        assertEquals("preset_card", tool.getCard().getName());
        assertEquals("Preset description", tool.getCard().getDescription());
    }
    
    /**
     * 测试Builder使用Method自动提取
     * 
     * 注意：Java反射获取的参数名默认是arg0, arg1（除非编译时开启-parameters选项），
     * 因此需要使用反射获取实际参数名来构建输入Map。
     */
    @Test
    @DisplayName("测试Builder使用Method自动提取")
    void testBuilderWithMethod() throws Exception {
        Method method = TestMethods.class.getMethod("multiplyWrapper", Double.class, Double.class);
        TestMethods instance = new TestMethods();
        
        LocalFunction tool = ToolDecorator.builder()
            .method(method, instance)
            .description("Custom multiplication description") // 覆盖自动提取的描述
            .autoExtract(true)
            .build();
        
        assertEquals("multiplyWrapper", tool.getCard().getName());
        assertEquals("Custom multiplication description", tool.getCard().getDescription());
        
        // 获取反射参数名（Java默认是arg0, arg1）
        String param0Name = method.getParameters()[0].getName();
        String param1Name = method.getParameters()[1].getName();
        
        // 使用反射获取的实际参数名
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(param0Name, 3.0);
        inputs.put(param1Name, 4.0);
        Object result = tool.invoke(inputs, new HashMap<>()).get();
        assertEquals(12.0, (Double) result, 0.001);
    }
    
    /**
     * 测试LocalFunction的stream方法
     * 对应Python: 生成器函数支持
     */
    @Test
    @DisplayName("测试LocalFunction的stream方法")
    void testLocalFunctionStream() {
        ToolCard card = ToolCard.builder()
            .name("stream_tool")
            .description("Tool that returns stream")
            .inputParams(Map.of())
            .build();
        
        // 创建带有流式函数的LocalFunction
        LocalFunction tool = new LocalFunction(
            card,
            inputs -> List.of(1, 2, 3), // 普通函数
            inputs -> List.of(10, 20, 30).iterator() // 流式函数
        );
        
        // 测试stream方法
        Stream<Object> stream = tool.stream(Map.of(), new HashMap<>());
        List<Object> results = stream.toList();
        
        assertEquals(3, results.size());
        assertEquals(10, results.get(0));
        assertEquals(20, results.get(1));
        assertEquals(30, results.get(2));
    }
    
    /**
     * 测试LocalFunction的stream方法 - 函数返回Iterator
     */
    @Test
    @DisplayName("测试LocalFunction的stream方法 - 返回Iterator")
    void testLocalFunctionStreamWithIterator() {
        ToolCard card = ToolCard.builder()
            .name("iterator_tool")
            .description("Tool that returns iterator")
            .inputParams(Map.of())
            .build();
        
        LocalFunction tool = new LocalFunction(
            card,
            inputs -> List.of("a", "b", "c").iterator()
        );
        
        Stream<Object> stream = tool.stream(Map.of(), new HashMap<>());
        List<Object> results = stream.toList();
        
        assertEquals(3, results.size());
        assertEquals("a", results.get(0));
    }
    
    /**
     * 测试LocalFunction的stream方法 - 函数返回Iterable
     */
    @Test
    @DisplayName("测试LocalFunction的stream方法 - 返回Iterable")
    void testLocalFunctionStreamWithIterable() {
        ToolCard card = ToolCard.builder()
            .name("iterable_tool")
            .description("Tool that returns iterable")
            .inputParams(Map.of())
            .build();
        
        LocalFunction tool = new LocalFunction(
            card,
            inputs -> List.of("x", "y", "z") // List是Iterable
        );
        
        Stream<Object> stream = tool.stream(Map.of(), new HashMap<>());
        List<Object> results = stream.toList();
        
        assertEquals(3, results.size());
        assertEquals("x", results.get(0));
    }
    
    /**
     * 测试supportsStream方法
     */
    @Test
    @DisplayName("测试supportsStream方法")
    void testSupportsStream() {
        ToolCard card = ToolCard.builder()
            .name("test")
            .description("test")
            .inputParams(Map.of())
            .build();
        
        // 没有streamFunc
        LocalFunction tool1 = new LocalFunction(card, inputs -> "result");
        assertFalse(tool1.supportsStream());
        
        // 有streamFunc
        LocalFunction tool2 = new LocalFunction(card, inputs -> "result", inputs -> List.of(1, 2).iterator());
        assertTrue(tool2.supportsStream());
        
        // 使用withStreamFunction添加
        LocalFunction tool3 = new LocalFunction(card, inputs -> "result");
        tool3.withStreamFunction(inputs -> List.of(1, 2, 3).iterator());
        assertTrue(tool3.supportsStream());
    }
    
    // 测试用辅助类
    public static class TestMethods {
        public int add(int a, int b) {
            return a + b;
        }
        
        /**
         * 使用包装类以避免反射调用基本类型时的null问题
         */
        public Integer addWrapper(Integer a, Integer b) {
            return a + b;
        }
        
        public double multiply(double a, double b) {
            return a * b;
        }
        
        /**
         * 使用包装类以避免反射调用基本类型时的null问题
         */
        public Double multiplyWrapper(Double a, Double b) {
            return a * b;
        }
        
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }
}
