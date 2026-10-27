# G.CON Concurrency 并发

共 12 条规则。

## `G.CON.01 对共享变量做同步访问控制时需避开同步陷阱--不要使用基于高级并发对象的synchronized块` 🔴 🔴[安全] `security_standard_recommend`

避免使用 Lock或Condition实现类(高级并发类) 对象本身 作为锁传递给synchronized。

使用了基于高级并发对象的synchronized块。高级并发类是指实现java.util.concurrent.locks包中的Lock或Condition接口的类，其本身提供了lock与unlock来实现同步，不应将这些类的对象作为synchronized块的同步对象使用。当使用基于高层并发对象的synchronized块时，容易被误认为这种方式与正常使用lock接口的方式是同一个锁，而实际是两个不同的锁，会导致无法实现同步控制。

**修改建议：** 使用Lock接口提供的lock()和unlock()方法。

✅ **正确示例：**

##### 场景1：并发对象
- 修复示例1：updateResource() 和 doSomething() 方法中使用了Lock接口提供的lock()和unlock()方法。
```java
public class SomeSharedResource {
    private final Lock lock = new ReentrantLock();
    public void updateResource() {
        lock.lock();
        try {

            // 更新共享的资源
            ...
        } finally {
            lock.unlock();
        }
    }
    public void doSomething() {
        lock.lock();
        try {

            // 更新共享的资源
            ...
        } finally {
            lock.unlock();
        }
    }
}
```

❌ **错误示例：**

##### 场景1：并发对象
- 错误示例：updateResource() 和 doSomething() 方法中使用不是同一个锁。

```java
public class SomeSharedResource {
    private final Lock lock = new ReentrantLock();
    public void updateResource() {
        // synchronized (lock) {

            // 更新共享的资源
            ...
        }
    }
    public void doSomething() {
        lock.lock();
        try {

            // 更新共享的资源
            ...
        } finally {
            lock.unlock();
        }
    }
}
```

---

## `G.CON.01 对共享变量做同步访问控制时需避开同步陷阱--避免使用class这类容易造成歧义的对象锁，而应使用明确的对象` 🔴 🔴[安全] `security_standard_recommend`

避免使用class类对象作为锁传递给synchronized。

如果使用class类对象作为同步对象，父子类继承关系增加了class类对象归属的复杂度，开发人员容易犯错，导致同步行为不符合预期；故应避免使用class这类容易造成歧义的对象，而应使用明确的对象。

**修改建议：** 禁止基于getClass()返回的类对象进行同步。

✅ **正确示例：**

##### 场景1：错误使用getClass()对象
- 修复示例：使用明确Class对象作为锁

```java
class Base {
    static DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM);

    public Date parse(String str) throws ParseException {
        try {
            synchronized (Class.forName("Base")) {
                return format.parse(str);
            }
        }
        catch (ClassNotFoundException x) {
            // "Base" not found; handle error
        }
        return null;
    }
}

class Derived extends Base {
    public Date doSomethingAndParse(String str) throws ParseException {
        synchronized (Base.class) {
            ...
            return format.parse(str);
        }
    }
}
```

❌ **错误示例：**

##### 场景1：错误使用getClass()对象
- 错误示例：基于getClass()返回的类对象进行同步。

```java
class Base {
    static DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM);

    public Date parse(String str) throws ParseException {
        synchronized (getClass()) {
            return format.parse(str);
        }
    }
}

class Derived extends Base {
    public Date doSomethingAndParse(String str) throws ParseException {
        synchronized (Base.class) {
            ...
            return format.parse(str);
        }
    }
}
```

---

## `G.CON.01 对共享变量做同步访问控制时需避开同步陷阱--不要使用可被重用的对象锁` 🔴 🔴[安全] `security_standard_recommend`

可能会被重用的对象不能作为锁。

使用可被重用的对象锁。如果使用可被重用的对象作为同步对象，容易导致不同的共享变量实际依赖了同一个锁，无法实现符合预期的同步效果。常见的可被重用的对象包括Boolean、封包的Integer对象、String常量等。

**修改建议：** 程序不能基于那些可能会被重用的对象进行同步。

✅ **正确示例：**

- 修复示例1：使用不可被重用的对象。
```java
public class SomeSharedResource {
    private final Object lock = new Object();

    public void updateResource() {
        synchronized (lock) {
            // 更新共享的资源
            ...
        }
    }
}
```

❌ **错误示例：**

- 错误示例1：使用Boolean型锁对象

```java
private final Boolean lock= Boolean.FALSE;

public void doSomething() {
    synchronized (lock) {
        ...
    }
}
```
- 错误示例2：使用基础数据的包装类型作为锁对象

```java
private int count = 0;
private final Integer lock = count; // Boxed primitive Lock is shared

public void doSomething() {
    synchronized (lock) {
        ...
    }
}
```
- 错误示例3：使用字符串常量作为锁对象

```java
private final String lock = "LOCK";

public void doSomething() {
    synchronized (lock) {
        ...
    }
}
```

---

## `G.CON.11 禁止使用Thread.stop()来终止线程` 🟠 🔴[安全] `security_standard_rule`

禁止调用Thread.stop()。

Thread.stop()已经被标记为@Deprecated，该方法是不安全的，调用Thread.stop()来终止线程会使其释放它所持有的所有锁，可能会导致这些锁保护的对象处于不一致的状态。

**修改建议：** 禁止调用Thread.stop()。

✅ **正确示例：**

- 修复示例：设置线程结束标志，在线程中迭代检查该标志来结束线程

```java
public final class Foo implements Runnable {
    private volatile boolean shouldAbort = false;

    public void stop() {
        shouldAbort = true;
    }

    @Override
    public void run() {
        ...
        while (!shouldAbort) {
           ...
        }
    }

    public static void main(String[] args){
        Foo foo = new Foo();
        Thread thread = new Thread(foo);
        thread.start();
        ...
        foo.stop(); // 此处使用 foo 对象终止线程
    }
}
```

❌ **错误示例：**

- 错误示例：使用`Thread.stop()`终止线程

```java
public final class Foo implements Runnable {
    private volatile boolean shouldAbort = false;

    public void stop() {
        shouldAbort = true;
    }

    @Override
    public void run() {
        ...
        while (!shouldAbort) {
           ...
        }
    }

    public static void main(String[] args){
        Foo foo = new Foo();
        Thread thread = new Thread(foo);
        thread.start();
        ...
        thread.stop(); // 禁止使用Thread.stop()来终止线程
    }
}

```

---

## `G.CON.01 对共享变量做同步访问控制时需避开同步陷阱` 🔴 🔴[安全] `security_standard_recommend`

不要使用公共的锁对象。

有两种方法来对共享变量的访问做同步：同步方法和同步块。声明为同步的方法以及在this引用上的同步块都使用对象自身的锁（隐式锁）。攻击者可以通过获取一个可访问类对象的隐式锁并无限期持有该锁来触发条件竞争与死锁，进而引起拒绝服务（DoS）。另外，如果同步代码块使用静态public的锁对象，攻击者也可以获取该锁对象，引发DoS攻击。防御这个漏洞一种方法就是使用私有锁对象。

**修改建议：** 使用私有不变锁对象，且禁止通过公有方法返回锁对象。

✅ **正确示例：**

同步块中使用私有锁, 没有通过公有方法返回私有锁对象。
```java
public class SomeObject {
    private final Object lock = new Object(); // private final lock object
    public void changeValue() {
        synchronized (lock) {
            // Locks on the private Object
            // ...
        }
    }
}
```

❌ **错误示例：**

同步块中使用公有锁
```java
public class SomeObject {
    public static final Object lock = new Object();
    public void changeValue() {
        synchronized (lock) {
            // Locks on the private Object
            // ...
        }
    }
}
// Untrusted code
synchronized (SomeOjbect.lock) {
    while (true) {
        // Indefinitely delay someObject
        Thread.sleep(Integer.MAX_VALUE);
    }
}
```

---

## `G.CON.09 不要依赖线程调度器、线程优先级和yield()方法` 🟡 `common_standard_rule`

Java中的线程调度，是基于操作系统以及JVM的实现，在不同的操作系统中，或者不同厂商的JVM（如Oracle、IBM等），即使是同一套代码，其多线程的调度机制也是不一样的。因此，在多线程的程序中，不要依赖于系统的线程调度器来决定程序的逻辑运作，如果程序依赖于线程调度器来达到正确性或者性能要求，会导致不可移植。

线程的优先级是高度依赖于系统的。当虚拟机依赖于系统的线程实现机制时，Java线程的优先级会被映射到系统的线程优先级上，Java线程优先级的数量会发生变化，甚至可能被忽略。所以程序功能的正确性不能依赖于线程的优先级。

而Thread.yield()对线程调度器仅仅是个提示，不保证确定的效果，因此代码也不能依赖Thread.yield()方法。

**修改建议：** 线程的运行应避免依赖通过setPriority()、Thread.yield()等方法设置的线程优先级。

✅ **正确示例：**

  ```java
    public class ProducerConsumerExample {
        private static final Lock lock = new ReentrantLock();
        private static final Condition product = lock.newCondition();
        private static final Condition consume = lock.newCondition();

        public static void main(String[] args) {
            LinkedList<Integer> buffer = new LinkedList<>();
            int maxSize = 10;
            Thread producer = new Thread(new Producer(buffer, maxSize));
            Thread consumer = new Thread(new Consumer(buffer));
            producer.start();
            consumer.start();
        }

        static class Producer implements Runnable {
            private LinkedList<Integer> buffer;
            private int maxSize;

            public Producer(LinkedList<Integer> buffer, int maxSize) {
                this.buffer = buffer;
                this.maxSize = maxSize;
            }

            public void run() {
                int num = 0;
                while (true) {
                    try {
                        lock.lock();
                        while (buffer.size() == maxSize) {
                            try {
                                product.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        buffer.add(num++);
                        System.out.println("Produced: " + num);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        consume.signal();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }

        static class Consumer implements Runnable {
            private final LinkedList<Integer> buffer;

            public Consumer(LinkedList<Integer> buffer) {
                this.buffer = buffer;
            }

            public void run() {
                while (true) {
                    try {
                        lock.lock();
                        while (buffer.isEmpty()) {
                            try {
                                consume.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        int num = buffer.removeFirst();
                        System.out.println("Consumed: " + num);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        product.signal();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
    }
  ```

❌ **错误示例：**

  ```java
    public class ProducerConsumerExample {
        public static void main(String[] args) {
            LinkedList<Integer> buffer = new LinkedList<>();
            int maxSize = 10;
            Thread producer = new Thread(new Producer(buffer, maxSize));
            Thread consumer = new Thread(new Consumer(buffer));
            producer.start();
            consumer.start();
        }

        static class Consumer implements Runnable {
            private final LinkedList<Integer> buffer;

            public Consumer(LinkedList<Integer> buffer) {
                this.buffer = buffer;
            }

            public void run() {
                while (true) {
                    while (buffer.isEmpty()) {
                        Thread.yield();
                    }
                    int num = buffer.removeFirst();
                    System.out.println("Consumed: " + num);
                }
            }
        }

        static class Producer implements Runnable {
            private LinkedList<Integer> buffer;
            private int maxSize;

            public Producer(LinkedList<Integer> buffer, int maxSize) {
                this.buffer = buffer;
                this.maxSize = maxSize;
            }

            public void run() {
                int num = 0;
                while (true) {
                    while (buffer.size() == maxSize) {
                        Thread.yield();
                    }
                    buffer.add(num++);
                    System.out.println("Produced: " + num);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
  ```

---

## `G.CON.01 对共享变量做同步访问控制时需避开同步陷阱--不要使用实例锁来保护静态共享数据` 🟡 🔴[安全] `security_standard_recommend`

禁止使用一个实例锁来同步静态共享数据。

实例锁的同步效果仅限于此实例本身，无法用来同步静态共享变量；如果试图使用实例锁来同步静态共享变量，在多实例情况下无法实现符合预期的同步效果。

**修改建议：** 禁止使用一个实例锁来同步静态共享数据。

✅ **正确示例：**

##### 场景1：同步块
- 修复示例：使用静态锁来同步静态共享变量。
```java
public class SomeSharedResource {
    private static volatile int counter;
    private static final Object lock = new Object();
    public void updateResource() {
        synchronized (lock) {
            counter++;
        }
    }
}
```
##### 场景2：同步方法
- 修复示例1：同步静态方法访问静态资源
```java
public class SomeSharedResource {
    private static volatile int counter;

    public static synchronized void updateResource() {
        counter++;
    }
}
```

❌ **错误示例：**

##### 场景1：同步块
- 错误示例：使用实例锁来同步静态共享变量。

```java
public class SomeSharedResource {
    private static volatile int counter;

    // 非静态
    private final Object lock = new Object();
    public void updateResource() {
        synchronized (lock) {
            counter++;
        }
    }
}
```
##### 场景2：同步方法
- 错误示例：同步非静态方法中访问静态资源

```java
public class SomeSharedResource {
    private static volatile int counter;

    // 同步方法
    public synchronized void updateResource() {
        counter++;
    }
}
```

---

## `G.CON.10 线程中断由业务代码来协作完成，慎用Thread.interrupt方法` 🟡 `common_standard_recommend`

线程中断由业务代码来协作完成，__慎用Thread.interrupt方法，它依赖执行线程对interrupted status的处理逻辑__。在使用Thread.interrupt()方法请求目标线程中止时，仅仅是在目标线程上将interrupted status标记为true，目标线程本身需用Thread.interrupted()方法检查该标记，当状态为true时，应主动执行清理，并抛出InterruptedException。

在编写需要中止的多线程程序时，必须选用能够响应interrupt的标准库或第三方库。Java标准库中的会阻塞的方法（如Thread.sleep()或者SocketChannel.write()）一般会在interrupt之后抛出InterruptedException。但有些方法则不理会interrupt，如Socket.write()，必须回避这些方法。

**修改建议：** 1、优先使用协作式的线程同步机制来通知一个线程中止作业，如java.util.concurrent包中的各种synchronizer，加锁的共享变量、volatile共享变量等。

2、如果需要一个线程让另一个线程中止执行，Java API推荐的方式是，让被中止的线程在运行中周期性地查询自己是否被中止。如果发现自己被中止，则应该主动清理状态并中止执行，而不是忽略请求继续执行。

✅ **正确示例：**

```java
public static void main(String[] args) {    
    Thread thread = new MyThread();
    thread.start();
    ...
    thread.requestStop(); // 使用通知变量机制来控制线程的终止
}

class MyThread extends Thread {
    private volatile boolean hasStopRequested;

    public void requestStop() {
        hasStopRequested = true;
    }

    @Override
    public void run() {
        while (!hasStopRequested) {
            doSomething();
        }
    }
}
```

❌ **错误示例：**

```java
public static void main(String[] args) {    
    Thread thread = new MyThread();
    thread.start();
    ...
    thread.interrupt(); // 预期使用interrupt机制终止线程，但线程无法被正确终止
}

class MyThread extends Thread {
    ...
    public void run() {
        boolean running = true;
        while (running) {
            try {
                doSomething();
            } catch (InterruptedException ex) {
                running = false;
            }
        }
    }
}
```

---

## `G.CON.07 创建新线程时必须指定线程名` 🟡 `common_standard_rule`

指定线程名可以给问题定位带来很多方便。日志或者dump文件中会包含线程的名字，但缺省的线程名Thread-n无法区分出是哪个线程，不便于问题定位。

**修改建议：** 创建线程时为线程指定具体的名称。

✅ **正确示例：**

  ```java
    Thread t1 = new Thread();
    t1.setName("xxx");
    t1.start();

    Thread t2 = new Thread();
    t2.setName("xxx");
    t2.start();
  ```

❌ **错误示例：**

  ```java
    Thread t1 = new Thread();
    t1.start(); // 没有指定线程名

    Thread t2 = new Thread();
    t2.start();
    t2.setName("xxx"); // 线程启动后再指定线程名无效
  ```

---

## `G.CON.06 使用新并发工具代替wait()和notify()` 🟡 `common_standard_rule`

Java 5开始提供了更高级的并发工具，这些工具可以有效替代wait()和notify()。新开发的代码应该优先使用这些并发工具。

这些高级的并发工具主要位于java.util.concurrent中，包括：

Executor Framework：可参考G.CON.12 避免不加控制地创建新线程，应该使用线程池来管控资源;
并发集合（Concurrent Collection）：提供了高性能的并发实现的集合接口，在其内部实现了同步管理，不需要额外加锁，常用的并发集合包括ConcurrentHashMap、ConcurrentSkipListSet、ConcurrentLinkedQueue等；
同步器（Synchronizer）：为每种特定的同步需求提供了解决方案，常用的同步器包括Phaser、CountDownLatch、Semaphore等。

**修改建议：** 新开发的代码应避免使用wait()和notify()方法。

✅ **正确示例：**

  ```java
    public class ProducerConsumerExample {
        private static final Lock lock = new ReentrantLock();
        private static final Condition product = lock.newCondition();
        private static final Condition consume = lock.newCondition();

        public static void main(String[] args) {
            LinkedList<Integer> buffer = new LinkedList<>();
            int maxSize = 10;
            Thread producer = new Thread(new Producer(buffer, maxSize));
            Thread consumer = new Thread(new Consumer(buffer));
            producer.start();
            consumer.start();
        }

        static class Producer implements Runnable {
            private LinkedList<Integer> buffer;
            private int maxSize;

            public Producer(LinkedList<Integer> buffer, int maxSize) {
                this.buffer = buffer;
                this.maxSize = maxSize;
            }

            public void run() {
                int num = 0;
                while (true) {
                    try {
                        lock.lock();
                        while (buffer.size() == maxSize) {
                            try {
                                product.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        buffer.add(num++);
                        System.out.println("Produced: " + num);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        consume.signal();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }

        static class Consumer implements Runnable {
            private final LinkedList<Integer> buffer;

            public Consumer(LinkedList<Integer> buffer) {
                this.buffer = buffer;
            }

            public void run() {
                while (true) {
                    try {
                        lock.lock();
                        while (buffer.isEmpty()) {
                            try {
                                consume.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        int num = buffer.removeFirst();
                        System.out.println("Consumed: " + num);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        product.signal();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
    }
  ```

❌ **错误示例：**

  ```java
    public class ProducerConsumerExample {
        public static void main(String[] args) {
            LinkedList<Integer> buffer = new LinkedList<>();
            int maxSize = 10;
            Thread producer = new Thread(new Producer(buffer, maxSize));
            Thread consumer = new Thread(new Consumer(buffer));
            producer.start();
            consumer.start();
        }

        static class Consumer implements Runnable {
            private final LinkedList<Integer> buffer;

            public Consumer(LinkedList<Integer> buffer) {
                this.buffer = buffer;
            }

            public void run() {
                while (true) {
                    synchronized (buffer) {
                        while (buffer.isEmpty()) {
                            try {
                                buffer.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        int num = buffer.removeFirst();
                        System.out.println("Consumed: " + num);
                        buffer.notifyAll();
                    }
                }
            }
        }

        static class Producer implements Runnable {
            private LinkedList<Integer> buffer;
            private int maxSize;

            public Producer(LinkedList<Integer> buffer, int maxSize) {
                this.buffer = buffer;
                this.maxSize = maxSize;
            }

            public void run() {
                int num = 0;
                while (true) {
                    synchronized (buffer) {
                        while (buffer.size() == maxSize) {
                            try {
                                buffer.wait();
                            } catch (InterruptedException e) {

                            }
                        }
                        buffer.add(num++);
                        System.out.println("Produced: " + num);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        buffer.notifyAll();
                    }
                }
            }
        }
    }
  ```

---

## `G.CON.08 使用Thread对象的setUncaughtExceptionHandler方法注册未捕获异常处理者` 🟠 🔴[安全] `common_standard_rule`

Java多线程程序中，所有线程都不允许抛出未捕获的checked exception，也就是说各个线程需要自己把自己的checked exception处理掉。但是无法避免未捕获的RuntimeException。当子线程抛出异常时，子线程会结束，但主线程不会知道，因为主线程通过try-catch是无法捕获子线程异常的。

Thread对象提供了setUncaughtExceptionHandler方法用来获取线程中产生的异常。还可以使用Thread.setDefaultUncaughtExceptionHandler，为所有线程设置默认异常处理方法。

应注意的是，在执行周期性任务例如ScheduledExecutorService时，为了程序的健壮性，可考虑在提交的Runnable的run方法内捕获高层级的异常。

ScheduledExecutorService的各种schedule方法，可以通过其返回的ScheduledFuture对象获取其异常。

**修改建议：** 创建线程时设置异常处理方法，保证线程抛出的异常可以被正确处理。

✅ **正确示例：**

  ```java
    public class TestUncaughtException {
        public static void main(String[] args) {
            TestThread thread = new TestThread("meaningful-name");
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread tr, Throwable ex) {
                    System.out.println(tr.getName() + " : " + ex.getMessage());
                }
            });
            thread.start();
        }
        public static class TestThread extends Thread {
            public TestThread(String name) {
                super.setName(name);
            }

            @Override
            public void run() {
                ...
                throw new RuntimeException("just a test");
            }
        }
    }
  ```

❌ **错误示例：**

  ```java
    public class TestUncaughtException {
        public static void main(String[] args) {
            TestThread thread = new TestThread("meaningful-name");
            thread.start();
        }
        public static class TestThread extends Thread {
            public TestThread(String name) {
                super.setName(name);
            }

            @Override
            public void run() {
                ...
                throw new RuntimeException("just a test");
            }
        }
    }
  ```

---

## `G.CON.12 避免不加控制地创建新线程，应该使用线程池来管控资源` 🟡 `common_standard_recommend`

Java虚拟机能够管理的线程数量有限，不加控制的创建新线程可能会导致Java虚拟机崩溃。

推荐使用Java 5之后提供的线程池ThreadPoolExecutor来管理线程资源，这样可以更加明确线程池的运行规则，避免资源耗尽的风险。另外，线程池要合理规划，避免任意重复创建。

不推荐使用Executors创建线程池，因为存在以下问题：

1）Executors.newFixedThreadPool()和Executors.newSingleThreadExecutor()允许请求队列的最大长度为Integer.MAX_VALUE，可能会因为堆积大量的请求导致OOM。

2）Executors.newCachedThreadPool()允许创建线程的最大数量为Integer.MAX_VALUE，可能会因为创建大量的线程导致OOM。Executors.newScheduledThreadPool()会自动增长工作队列大小。Executors.newWorkStealingPool()实际的工作窃取线程数量会动态地增减。

**修改建议：** 避免单独创建线程，尽量使用线程池来复用线程。线程池的从创建推荐使用ThreadPoolExecutor。

✅ **正确示例：**

- 修复示例： 使用线程池机制

  ```java
    private BlockingQueue blockingQueue = new LinkedBlockingQueue(100);
    private ThreadPoolExecutor threadPool = new ThreadPoolExecutor(2, 64, 60L,
        TimeUnit.SECONDS, blockingQueue,
        new SelfThreadFactory("ProductName", "ThreadName", false),
        new DiscardOldestPolicy(LOGGER, "ThreadName"));
    public void processEntity2(List<Entity> items) {
        for (Entity entity : items) {
            threadPool.execute(new EntityProcessor(entity));
        }
    }
  ```

❌ **错误示例：**

- 错误示例1：单独创建线程

  ```java
    public void processEntity(List<Entity> items) {        
        ...       
        new Thread(new EntityProcessor(entity)).start();
        ...       
    }
  ```
- 错误示例2：在循环中创建多个线程

  ```java
    public void processEntity(List<Entity> items) {
        for (Entity entity : items) {
            new Thread(new EntityProcessor(entity)).start();
        }
    }
  ```

---
