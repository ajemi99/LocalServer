# Input/Output Modes

## I/O Modes

### Blocking I/O

This mode forces the thread to wait for the read operation(input) until its completed before moving on to excute the rest of the instructions

```java
int bytesRead = socket.getInputStream().read(buffer);
```

if there is no data in the buffer the thread keeps waiting.

### Non-Blocking I/O(Used in this project)

This mode make the thread resolves the read operation immediatly

```java
int bytesRead = socketChannel.read(buffer);
```

### Asynchronous I/O

This mode doesn't wait for the operation to finish inside the method call. The completion handler receives the result later.

```java
channel.read(buffer, attachment, completionHandler);
```


## Non-Blocking I/O

