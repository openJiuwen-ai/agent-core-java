# com.openjiuwen.core.workflow.component.TemplateProcessor

## 类 TemplateProcessor

```java
public class TemplateProcessor
```

`TemplateProcessor` 负责模板拆段、同步渲染与流式渲染，是 `End` 模板输出的核心工具。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void setDataSourceCount(int count)` | 设置期望的数据源数量。 |
| `public int currentPosition()` | 返回当前渲染位置。 |
| `public String getCurrentSegment()` | 返回当前模板片段。 |
| `public boolean shouldRender()` | 返回当前是否应触发渲染。 |
| `public void advancePosition()` | 推进到下一个模板片段。 |
| `public String render(Map<String, Object> inputs)` | 执行整串模板渲染。 |
| `public void reset()` | 重置内部状态。 |
| `public boolean isFinished()` | 返回是否已完成全部片段。 |
| `public Iterator<Map<String, Object>> renderStream(Map<String, Object> inputs, NodeSessionApi session)` | 逐帧输出模板渲染结果。 |
