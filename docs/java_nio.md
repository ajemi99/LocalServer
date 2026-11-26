# NIO Package

## Selector

```java
selector = Selector.open();
```

The java selector creates an OS-backed I/O multiplexing resource so conceptually the JDK performs something equivilant to:

```c
int epoll_fd = epoll_create1(0);
```

this system call creates an epoll instance and return its file descriptor so the selector can managae multiple i/o operations

### epoll

the epoll is i/o notification system that is registring socket othe the epoll unlike **poll** when u should pass the enitre array of fds that u want to monitor and to determine readiness u should loop through the array repeadatly each time in the other hand the epoll relies on the kerenel following this steps:

1. A network packet arrives
2. the linux network stack proccess the packet
3. tcp parses the packet and send the payload to the targetted socket
4. the kernel now knows that the socket is readable
5. the kernel make the readiness event available to **epoll**

## ServerSocketChannel

### Open

create a non-lisning socket

```java
ServerSocketChannel socket = ServerSocketChannel.open();
```

### SetOption

```java
socket.setOption(StandardSocketOptions.SO_REUSEADDR,true);
```

It allows address reuse in certain situations, such as restarting a server when previous connections are in TIME_WAIT.

### Bind

assign the socket to an IP address assigned to one of the machine's network interfaces.

```java
socket.bind(new InetSocketAddress(epoint.address(), epoint.port()));
```

the bind method put the socket on the listening mode but only for the **ServerSocketChannel** object

### ConfigureBlocking

```java
socket.configureBlocking(false);
```

tells the jdk that the i/o operations on this channel will be non-blocking

### Register

```java
socket.register(Selector,int,Object)
```

register the ServerSocketChannel to the selector and notify when the socket is ready to accept incoming TCP connections

```c
// Register the listening socket
epoll_ctl(epfd, EPOLL_CTL_ADD, server_fd, &event);
```

## Select

```java
selector.select(SELECTOR_TIMEOUT_MS)
```

wait for the readiness of the regitred sockets(chanels) if no socket is ready the thread blocks(sleep) and wakes when epoll event occur

```c
// Wait for readiness events
epoll_wait(epfd, events, 100, -1);
```

the flow:

```txt
Java application
      |
      | server.register(...)
      v
   epoll_ctl()
      |
      v
Kernel stores socket registration
      |
      | selector.select()
      v
   epoll_wait()
      |
      v
Thread sleeps if no events are ready
      |
      | Client connects
      v
Kernel detects accept readiness
      |
      v
Thread wakes up
      |
      v
selector.select() returns
      |
      v
Java event loop processes the event
```

## ByteBuffer

this is a class extends **Buffer** that holds the connection readed of written bytes instead of using a byte[] and its used because SocketChannel.read() and SocketChannel.write() operates on buffers

### capacity,limit,position

intially the buffer has

```java
capacity = 1024
limit = 1024
position = 0
```

client send data with length 10 trought tcp socket the buffer now has:

```java
int bytesRead = client.read(buffer);
```

```java
capacity = 1024
limit = 1024
position = 10
```

```java
buffer.flip();
```

```java
capacity = 1024
limit = 10
position = 0
```

consume the buffer and reset

```java
SocketChannel.append(buffer);
buffer.clean();
```

```java
capacity = 1024
limit = 1024
position = 0
```

## SelectionKey

this key is created after a socket registration to the selector

```java
SelectioKey server_key = ServerSocketChannel.register(sel, ops, att);
SelectioKey client_key = SocketChannel.register(sel, ops, att);
```

the key is considred selected only if its operation is ready and we use this methods to monitor the listening/connected Tcp sockets:

| Method | Purpose |
|---|---|
| `channel()` | Returns the channel associated with the key. |
| `selector()` | Returns the selector associated with the key. |
| `interestOps()` | Returns the operations you want to monitor. |
| `interestOps(int ops)` | Changes the operations you want to monitor. |
| `readyOps()` | Returns the operations currently reported as ready. |
| `isAcceptable()` | Checks whether accepting a connection is ready. |
| `isReadable()` | Checks whether reading is ready. |
| `isWritable()` | Checks whether writing is ready. |
| `isConnectable()` | Checks whether a non-blocking connection can be completed. |
| `attach(Object obj)` | Associates application data with the key. |
| `attachment()` | Retrieves the associated application data. |
| `isValid()` | Checks whether the registration is still valid. |
| `cancel()` | Cancels the channel's registration with the selector. |

## SocketChannel

This is a an object that represent an os-backed tcp connected socket resulted by an accept from the listening socket (ServerSocketChannel)

```java
ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

SocketChannel client = serverChannel.accept();
```

the equivlant is:

```c
struct sockaddr_in client_addr;
socklen_t client_len = sizeof(client_addr);

int client_fd = accept(
    server_fd,
    (struct sockaddr *)&client_addr,
    &client_len
);
```
