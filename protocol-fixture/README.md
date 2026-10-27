# Protocol fixture server

This dedicated server fixture is isolated from the Vibrato client mod. Before starting it, create `run-protocol-fixture/eula.txt` only if you accept the Minecraft EULA and configure `run-protocol-fixture/server.properties` with:

```
server-ip=127.0.0.1
server-port=25576
online-mode=false
enable-query=false
enable-rcon=false
enable-command-block=true
spawn-protection=0
```

The fixture refuses to start when the network settings differ. Use a disposable fixture world. The command `/vprotocol list` displays scenario names and intended packet identifiers; `/vprotocol run <name>` executes a scenario as the invoking operator. These are targets, not proof of delivery: check the client observer report for actual successful observations. `blocks` modifies blocks around the caller; `entities` creates tagged entities and `cleanup` removes them. The project is intentionally server-only and must not be installed on a public server.
