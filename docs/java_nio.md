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

### Select

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
