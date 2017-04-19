## Update

- added DelayCancelationFlow, no effect
- added an example with a BidiPipe that separates server side flows (IdleTimeout.scala runnable as Test)

### Summary
Since version 10.0.1 Akka HTTP WebSocket server does not fail
internal streams if a timeout happens.

To reproduce checkout the two commits
 - Regression in 10.0.1 and above
 - Working in 10.0.0 and before

this results in the following shortened log output:

### 10.0.1 - 10.0.5

#### server:


```
[DEBUG] [04/12/2017 14:33:29.025] [default-akka.actor.default-dispatcher-3] [akka://default/system/IO-TCP/selectors/$a/0] New connection accepted
## M ## in TextMessage.Strict(marvin)
## M ## out TextMessage.Strict(Hello marvin)
[DEBUG] [04/12/2017 14:33:34.720] [default-akka.actor.default-dispatcher-2] [akka://default/user/StreamSupervisor-0/flow-1-0-unknown-operation] Aborting tcp connection to /127.0.0.1:59752 because of upstream failure: HTTP idle-timeout encountered, no bytes passed in the last 5 seconds. This is configurable by akka.http.[server|client].idle-timeout.
## M ## out downstream finished
## M ## in downstream finished
```

#### client:

```
## M ## out TextMessage.Strict(marvin)
[DEBUG] [04/12/2017 14:33:29.026] [default-akka.actor.default-dispatcher-8] [akka://default/system/IO-TCP/selectors/$a/0] Connection established to [localhost:9000]
## M ## in TextMessage.Strict(Hello marvin)

// 2nd message produced
## M ## out TextMessage.Strict(marvin)
[DEBUG] [04/12/2017 14:33:38.987] [default-akka.actor.default-dispatcher-9] [akka://default/system/IO-TCP/selectors/$a/0] Closing connection due to IO error java.io.IOException: Eine vorhandene Verbindung wurde vom Remotehost geschlossen
## M ## out downstream finished
## M ## in upstream failure akka.stream.StreamTcpException: The connection closed with error: Eine vorhandene Verbindung wurde vom Remotehost geschlossen
```

### 10.0.0 and before

#### server:

```
[DEBUG] [04/12/2017 14:27:02.745] [default-akka.actor.default-dispatcher-3] [akka://default/system/IO-TCP/selectors/$a/0] New connection accepted
## M ## in TextMessage.Strict(marvin)
## M ## out TextMessage.Strict(Hello marvin)
[DEBUG] [04/12/2017 14:27:08.456] [default-akka.actor.default-dispatcher-2] [akka://default/user/StreamSupervisor-0/flow-1-0-unknown-operation] Aborting tcp connection to /127.0.0.1:59526 because of upstream failure: TCP idle-timeout encountered on connection to [/127.0.0.1:59526], no bytes passed in the last 5 seconds
## M ## out downstream finished
## M ## in upstream failure akka.stream.scaladsl.TcpIdleTimeoutException: TCP idle-timeout encountered on connection to [/127.0.0.1:59526], no bytes passed in the last 5 seconds
```

#### client:

```
# M ## out TextMessage.Strict(marvin)
[DEBUG] [04/12/2017 14:27:02.747] [default-akka.actor.default-dispatcher-4] [akka://default/system/IO-TCP/selectors/$a/0] Connection established to [localhost:9000]
## M ## in TextMessage.Strict(Hello marvin)

// 2nd message produced
## M ## out TextMessage.Strict(marvin)
[DEBUG] [04/12/2017 14:27:12.714] [default-akka.actor.default-dispatcher-4] [akka://default/system/IO-TCP/selectors/$a/0] Closing connection due to IO error java.io.IOException: Eine vorhandene Verbindung wurde vom Remotehost geschlossen
## M ## out downstream finished
## M ## in upstream failure akka.stream.StreamTcpException: The connection closed with error: Eine vorhandene Verbindung wurde vom Remotehost geschlossen
```
