# G.COL Collections & Generics 集合与泛型

共 3 条规则。

## `G.COL.03 声明一个泛型类通过限定符限制可用的泛型类型` 🟡 🔴[安全] `common`

Java的泛型可按PECS(Producer Extends，Consumer Super)原则来设计上界和下界类型。声明一个带泛型的类或接口的时候，建议限制可以用的泛型类型，避免接口使用者乱用。

`<? extends T>`实现了泛型的协变，表现在泛型集合中，适合从集合中读取元素，不能添加元素。`<? super T>`实现了泛型的逆变，表现在泛型集合中，适合向集合中添加元素，不适合读取元素。
Collections的一个典型的使用场景：`public static <T> void copy(List<? super T> dest, List<? extends T> src) `。

**修改建议：** 声明一个带泛型的类或接口的时候，建议对可以使用的泛型类型进行限制，避免接口使用者乱用。

✅ **正确示例：**

  ```java
    public class GenericClassWithQualifier<T extends Number> {

    }
  ```

❌ **错误示例：**

  ```java
    public class GenericClassWithQualifier<T> {

    }
  ```

---

## `G.COL.04 不要在foreach循环中通过remove()/add()方法更改集合` 🟠 🔴[安全] `common_standard_rule`

java.util.concurrent包之外的（非concurrent）集合在foreach循环中不要更改，否则可能会导致ConcurrentModificationException。当需要遍历集合并删除部分元素时，可采用removeIf()方法或Iterator的remove()方法。个别集合（例如CopyOnWriteArrayList）的Iterator中remove()方法会抛出UnsupportedOperationException。

**修改建议：** 在遍历集合中的元素时，如果需要对集合进行更改，应该使用迭代器。

✅ **正确示例：**

  ```java
  // 使用Java 8 Collection中的removeIf方法
  list.removeIf(item -> shouldDelete(item));

  // 使用Iterator删除元素
  Iterator<String> iterator = list.iterator();
  while (iterator.hasNext()) {
      String item = iterator.next();
      if (shouldDelete(item)) {
          iterator.remove();
      }
  }
  ```

❌ **错误示例：**

  ```java
  for (String item : list) {
      if (shouldDelete(item)) {
          list.remove(item);
      }
  }
  ```

---

## `G.COL.02 优先使用泛型集合，而不是数组` 🟡 `common_standard_recommend`

泛型是不可变的，数组是协变的。当向数组中添加类型不匹配的元素时，在运行期才会发生错误，而对于集合，在编译期就会报错。另外，由于类型不安全无法创建泛型数组。

泛型与数组不能很好地混合使用，如果需要使用一个“泛型化的数组”，更好地选择是使用集合。

**修改建议：** 相对于数组，推荐优先使用泛型集合。

✅ **正确示例：**

  ```java
    private final List<T> lists; // 泛型列表
    private final List<String> longs; // 具体化的列表

    List<Object> objectList = new ArrayList<String>(DEFAULT_CAPACITY); // 不兼容类型，编译报错
    objectList.add("test value");
  ```

❌ **错误示例：**

  ```java
    private final T[] someArray; // 泛型数组，不建议
    private final Object[] objArray; // 协变化的数组声明，不建议

    Object[] objectArray = new Integer[10]; // 协变化的数组初始化，不建议
    objectArray[0] = "test value"; // 运行时抛出ArrayStoreException
  ```

---
