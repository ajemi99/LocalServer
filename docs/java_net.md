# NET Package

## URLEncoder

## URLDecoder

## InetSocketAddress

## StandardSocketOptions

- A socket option is a configuration setting that controlls how a os-level socket should behave and StandardSocketOptions are the java representation of those options
- the option used in this server sockets is **_SO_REUSEADDR_**

### SO_REUSEADDR

this option controls address reuse when binding a socket with this option disabled binding can fail because of socket state left by previous server connections
,like Tcp connection in TIME_WAIT