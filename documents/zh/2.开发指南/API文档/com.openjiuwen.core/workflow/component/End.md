# com.openjiuwen.core.workflow.component.End

## 类 End

```java
public class End extends WorkflowComponent
```

`End` 是工作流结束节点，可直接输出结果，也可按模板渲染响应，并支持 streaming、collect 和 transform 场景。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public End()` | 创建默认结束节点。 |
| `public End(EndConfig conf)` | 使用 `EndConfig` 创建结束节点。 |
| `public End(Map<String, Object> confMap)` | 使用配置字典创建结束节点。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void setMix()` | 标记为混合模式。 |
| `public boolean isMix()` | 返回是否处于混合模式。 |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 生成最终批量输出。 |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | 输出流式结果。 |
| `public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context)` | 对流输入逐段变换后输出。 |
| `public Object collect(Object inputs, NodeSessionApi session, ModelContext context)` | 汇聚流输入后再生成最终结果。 |

## 说明

- 配置了模板时会输出 `response`；未配置模板时主要返回 `output`。
- `WorkflowTest` 验证了它在 streaming workflow 中可逐块返回输出。
