# G.FIO File IO 文件IO

共 4 条规则。

## `G.FIO.04 防止外部进程阻塞在输入输出流上` 🔴 🔴[安全] `security_standard_rule`

防止外部进程阻塞在输入输出流上。

Java中有两种方式启动一个外部进程并与其交互：
1. java.lang.Runtime的exec()方法。
2. java.lang.ProcessBuilder的start()方法。

他们都返回一个java.lang.Process对象，该对象封装了这个外部进程。每个Process对象，包含输入流、输出流及错误流各一个。应该恰当地处理这些流，避免外部进程阻塞在这些流上。不正确的处理会产生异常、DoS，及其他安全问题。

**修改建议：** 在运行一个外部进程时，如果此进程往其输出流发送任何数据，则必须将其输出流清空。类似的，如果进程会往其错误流发送数据，其错误流也必须被清空。

✅ **正确示例：**

- 修复示例1：分别处理外部进程的输出流、错误流

```java
public class ProcessExecutor {
    public void callExtProcess() throws IOException, InterruptedException {
        Process proc = Runtime.getRuntime().exec("ProcessHasOutput");

        StreamConsumer errConsumer = new StreamConsumer(proc.getErrorStream());
        StreamConsumer outputConsumer = new StreamConsumer(proc.getInputStream());

        errConsumer.start();
        outputConsumer.start();

        int exitVal = proc.waitFor();

        errConsumer.join();
        outputConsumer.join();
    }

    class StreamConsumer extends Thread {
        InputStream is;

        StreamConsumer(InputStream is) {
            this.is = is;
        }

        @Override
        public void run() {
            try {
                byte data;
                int result;
                while ((result = is.read()) != -1) {
                    data = (byte) result;
                    handleData(data);
                }
            } catch (IOException ex) {
                ... // 处理异常
            }
        }

        private void handleData(byte data) {
            ...
        }
    }
}
```
- 修复示例2：将错误流重定向到输出流中，单独处理输出流

```java
public class ProcessExecutor {
    public void callExtProcess() throws IOException, InterruptedException {
        ProcessBuilder proc = new ProcessBuilder("ProcessHasOutput");
                proc.start();

        proc.redirectErrorStream(true);
        StreamConsumer outputConsumer = new StreamConsumer(proc.getInputStream());
        outputConsumer.start();

        int exitVal = proc.waitFor();

        outputConsumer.join();
    }

    class StreamConsumer extends Thread {
        InputStream is;

        StreamConsumer(InputStream is) {
            this.is = is;
        }

        @Override
        public void run() {
            try {
                byte data;
                int result;
                while ((result = is.read()) != -1) {
                    data = (byte) result;
                    handleData(data);
                }
            } catch (IOException ex) {
                ... // 处理异常
            }
        }

        private void handleData(byte data) {
            ...
        }
    }
}
```

❌ **错误示例：**

- 错误示例：处理进程的返回值前、或等待进程结束前，未处理外部进程的输出流、错误流

```java
// 处理进程的返回值前未处理外部进程的输出流、错误流
public void execExtProcess() throws IOException {
    Process proc = Runtime.getRuntime().exec("ProcessMaybeStillRunning");
    int exitVal = proc.exitValue();
}

// 等待进程结束前未处理外部进程的输出流、错误流
public void execExtProcess() throws IOException, InterruptedException {
    Process proc = Runtime.getRuntime().exec("ProcessHasOutput");
    int exitVal = proc.waitFor();
}
```

---

## `G.FIO.01 使用外部数据构造的文件路径前必须进行校验，校验前必须对文件路径进行规范化处理` 🟠 🔴[安全] `security_standard_rule`

禁止使用getAbsolutePath()获取文件的绝对路径。

文件路径来自外部数据时，必须对其合法性进行校验，否则可能会产生路径遍历（Path Traversal）漏洞。文件路径有多种表现形式，如绝对路径、相对路径，路径中可能会含各种链接、快捷方式、影子文件等，这些都会对文件路径的校验产生影响，所以在文件路径校验前要对文件路径进行规范化处理，使用规范化的文件路径进行校验。对文件路径的规范化处理必须使用getCanonicalPath()，禁止使用getAbsolutePath()（该方法无法保证在所有的平台上对文件路径进行正确的规范化处理）。

**修改建议：** 在文件验证之前，使用getCanonicalPath()而非getAbsolutePath()获取文件路径。

✅ **正确示例：**

```java
public void doSomething() {
    File file = new File(HOME_PATH, fileName);
    try {
        String canonicalPath = file.getCanonicalPath(); // 使用 getCanonicalPath() 方法对文件路径进行规范化处理。

        // 校验 canonicalPath 合理性。
        if (!validatePath(canonicalPath)) {
            throw new IllegalArgumentException("Path Traversal vulnerability!");
        }
        ... // 对文件进行读写等操作
    } catch (IOException ex) {
        throw new IllegalArgumentException("An exception occurred ...", ex);
    }
}

private boolean validatePath(String path) {
    return path.startsWith(HOME_PATH);
}
```

❌ **错误示例：**

```java
public void doSomething() {
    File file = new File(HOME_PATH, fileName);
    try {
        String absolutePath = file.getAbsolutePath(); // 使用 getAbsolutePath() 方法对文件路径进行规范化处理

        // 校验 absolutePath 合理性。
        if (!validatePath(absolutePath )) {
            throw new IllegalArgumentException("Path Traversal vulnerability!");
        }
        ... // 对文件进行读写等操作
    } catch (IOException ex) {
        throw new IllegalArgumentException("An exception occurred ...", ex);
    }
}

private boolean validatePath(String path) {
    return path.startsWith(HOME_PATH);
}
```

---

## `G.FIO.03 对于从流中读取一个字符或字节的方法，使用int类型的返回值` 🔴 🔴[安全] `security_standard_rule`

从流中读取一个字符或字节的方法，未使用int类型的返回值。

Java中InputStream.read()和Reader.read()方法用于从流中读取一个字节（byte）或字符（char）。InputStream.read()读取一个字节，返回值的范围为0x00-0xFF（补码），8位；Reader.read()读取一个字符，返回值的范围为0x0000-0xFFFF（补码），16位。当读取到流的末尾时，以上方法均返回int类型的-1（补码表示为0xFFFFFFFF），32位。因此，如果在未判断返回值是否是流末尾标志-1（补码表示为0xFFFFFFFF）前将返回值转为byte或 char，会导致无法正确判断返回值是流中的内容还是结束标识。

**修改建议：** 使用int类型的变量来保存read()的返回值，并使用该返回值判断是否读取到流的末尾，流未读完时，将读取的内容转换为char或者byte类型。

✅ **正确示例：**

```java
public static void readBytesFromStream() {
    try (FileInputStream in = new FileInputStream("demo.txt")) {
        // Initialize stream
        int data;
        while ((data = in.read()) != -1) {
            LOGGER.info(data);
        }
    } catch (Exception e) {
        LOGGER.error("error");
    }
}

public static void readCharsFromStream() {
    try (FileReader in = new FileReader("demo.txt")) {
        // Initialize stream
        int data;
        while ((data = in.read()) != -1) {
            LOGGER.info(data);
        }
    } catch (Exception e) {
        LOGGER.error("error");
    }
}
```

❌ **错误示例：**

```java
public static void readBytesFromStream() {
    try (FileInputStream in = new FileInputStream("demo.txt")) {
        // Initialize stream
        byte data;

        // 对于从流中读取一个字符或字节的方法，使用int类型的返回值
        while ((data = (byte) in.read()) != -1) {
            LOGGER.info(data);
        }
    } catch (Exception e) {
        LOGGER.error("error");
    }
}

public static void readCharsFromStream() {
    try (FileReader in = new FileReader("demo.txt")) {
        // Initialize stream
        char data;

        // 对于从流中读取一个字符或字节的方法，使用int类型的返回值
        while ((data = (char) in.read()) != -1) {
            LOGGER.info(data);
        }
    } catch (Exception e) {
        LOGGER.error("error");
    }
}
```

---

## `G.FIO.02 从ZipInputStream中解压文件必须进行安全检查` 🟠 🔴[安全] `security_standard_rule`

使用ZipEntry.getSize()进行解压尺寸大小的检查。

使用java.util.zip.ZipInputStream 解压zip文件时，可能会有两类安全风险：
1. 将文件解压到目标目录之外
   压缩包中的文件名中如果包含.. ，可能导致文件被解压到目标目录之外，造成任意文件注入、文件恶意篡改等风险。因此，压缩包中的文件在解压前，要先对解压的目标路径进行校验，如果解压目标路径不在预期目录之内，要么拒绝将其解压出来，要么将其解压到一个安全的位置。
2. 解压的文件消耗过多的系统资源
   zip压缩算法可能有很大的压缩比，可以把超大文件压缩成很小的zip文件（例如可以将上G的文件压缩为几K大小），这样的文件解压可能会导致zip炸弹（zip bomb）攻击。所以zip文件解压时，要对解压的实际文件大小进行检查，若解压之后的文件大小超过一定的限制，必须拒绝解压。具体的大小限制根据实际情况来确定。除此之外，解压时，还需要对解压出来的文件数量进行限制，防止zip压缩包中是数量巨大的小文件。

**修改建议：** 禁止通过ZipEntry.getSize()进行解压尺寸判断。

✅ **正确示例：**

```java
    static final int BUFFER = 512;
    static final int TOOBIG = 0x6400000; // max size of unzipped data, 100MB
    static final int TOOMANY = 1024; // max number of files

    // ...
    // The code validates the name of each entry before extracting the entry.
    // If the name is invalid, the entire extraction is aborted.
    private String sanitizeFileName(String entryName, String intendedDir) throws IOException {
        File f = new File(intendedDir, entryName);
        String canonicalPath = f.getCanonicalPath();
        File iD = new File(intendedDir);
        String canonicalID = iD.getCanonicalPath();
        if (canonicalPath.startsWith(canonicalID)) {
            return canonicalPath;
        } else {
            throw new IllegalStateException("File is outside extraction target directory.");
        }
    }

    public final void doSomething(String fileName, String destDir) throws java.io.IOException {
        FileInputStream fis = new FileInputStream(fileName);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
        ZipEntry entry;
        int total = 0;
        int entries = 0;
        try {
            while ((entry = zis.getNextEntry()) != null) {
                BufferedOutputStream dest = null;
                int count;
                byte data[] = new byte[BUFFER];

                // Write the files to the disk, but ensure that the entryName is valid,and that the file is not insanely big
                String name = sanitizeFileName(entry.getName(), destDir);

                // process file
                FileOutputStream fos = new FileOutputStream(name);
                dest = new BufferedOutputStream(fos, BUFFER);

                // check every entry's size
                while ((count = zis.read(data, 0, BUFFER)) != -1) {
                    total += count;
                    if (total > TOOBIG) {
                        break;
                    }
                    dest.write(data, 0, count);
                }
                entries++;

                // if the total number of entry is larger than the max number,it will throw exception.
                if (entries > TOOMANY) {
                    //handle exception
                }
                // if the total size of zip file is bigger than the max size value,it will throw exception.
                if (total > TOOBIG) {
                    //handle exception
                }
                …
            }
        } finally {
            zis.close();
        }
    }
```

❌ **错误示例：**

```java
    public static final int BUFFER = 512;
    public static final int TOOBIG = 0x6400000; // 100MB

    public final void doSomething(String filename) throws java.io.IOException {
        FileInputStream fis = new FileInputStream(filename);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
        ZipEntry entry;
        try {
            while ((entry = zis.getNextEntry()) != null) {
                System.out.println("Extracting: " + entry);
                int count;
                byte data[] = new byte[BUFFER];

                // Write the files to the disk, but only if the file is not
                // insanely big
                int temp = (int) entry.getSize();

                // 检查if语句中是否使用ZipEntry.getSize()来做文件大小的判断，如果是，则报告警。
                if (temp > TOOBIG) {
                    throw new IllegalStateException("File to be unzipped is huge.");
                }
                // 检查if语句中是否使用ZipEntry.getSize()来做文件大小的判断，如果是，则报告警。
                if (entry.getSize() == -1) {
                    throw new IllegalStateException("File to be unzipped might be huge.");
                }
                FileOutputStream fos = new FileOutputStream(entry.getName());
                BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
                while ((count = zis.read(data, 0, BUFFER)) != -1) {
                    dest.write(data, 0, count);
                }
                dest.flush();
                dest.close();
                zis.closeEntry();
            }
        } finally {
            zis.close();
        }
    }

```

---
