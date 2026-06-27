# com.openjiuwen.core.workflow.component.loop.LoopInput

## 类 LoopInput

```java
public class LoopInput
```

循环组件输入模型，描述循环类型、次数、数组源和中间变量配置。

## 字段

| 签名 | 说明 |
| --- | --- |
| `private String loopType = ""` | 循环类型。 |
| `private Integer loopNumber = 0` | 循环次数上限。 |
| `private Map<String, Object> loopArray = new HashMap<>()` | 数组循环的数据源。 |
| `private Object boolExpression = ""` | 布尔表达式或条件对象。 |
| `private Map<String, Object> intermediateVar = new HashMap<>()` | 中间变量提取配置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LoopInput()` | 创建 `LoopInput` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getLoopType()` | 返回循环类型。 |
| `public void setLoopType(String loopType)` | 设置循环类型。 |
| `public Integer getLoopNumber()` | 返回循环次数上限。 |
| `public void setLoopNumber(Integer loopNumber)` | 设置循环次数上限。 |
| `public Map<String, Object> getLoopArray()` | 返回数组循环的数据源。 |
| `public void setLoopArray(Map<String, Object> loopArray)` | 设置数组循环的数据源。 |
| `public Object getBoolExpression()` | 返回布尔表达式或条件对象。 |
| `public void setBoolExpression(Object boolExpression)` | 设置布尔表达式或条件对象。 |
| `public Map<String, Object> getIntermediateVar()` | 返回中间变量提取配置。 |
| `public void setIntermediateVar(Map<String, Object> intermediateVar)` | 设置中间变量提取配置。 |
| `public static LoopInput fromMap(Map<String, Object> map)` | 根据 `Map` 构造 `LoopInput` 实例。 |
