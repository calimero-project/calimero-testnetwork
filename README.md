Calimero test-network [![CI with Gradle](https://github.com/calimero-project/calimero-testnetwork/actions/workflows/gradle.yml/badge.svg)](https://github.com/calimero-project/calimero-testnetwork/actions/workflows/gradle.yml)
=====================

A generic and adaptable KNX test-network for automatic unit-testing, as well as client-side development. The test network enables a developer to test client-side software behavior, without the requirement of having a physical test-setup (i.e., hardware).

### Network setup

* Calimero KNXnet/IP server, with KNXnet/IP discovery, tunneling, routing, and bus-monitoring enabled
* Power-line (PL) 110 virtual KNX network
* 2 Calimero KNX devices
	* 1 device in programming mode
	* 1 device not in programming mode

The KNXnet/IP server configuration is provided by the file `src/main/resources/server-config.xml`.

### Logging

By default, the test-network uses the `slf4j` simple logger, configured via `src/main/resources/simplelogger.properties`.

### Adapting the network

The test-network can easily be adapted to different testing environments by adjusting the server configuration, the network in `TestNetwork.java`, or modifying the KNX device logic in `TestDeviceLogic.java`.
