# ADR 0003: Bundle the existing Java application for local desktop use

Status: implemented locally; hosted acceptance and distribution are separate gates.

The React interface already exercises a real Spring/JDBC job engine, but developer
setup and an in-memory default do not make a useful ordinary desktop application.
We use Java 21 `jlink` and `jpackage --type app-image` to ship that same application
with its runtime and compiled UI. A small Swing window owns open/help/quit instead
of introducing a Python or Electron wrapper around Java.

The packaged mode chooses a dedicated durable OS-local H2 profile, preserving
existing operator-selected database configuration and all developer defaults.
An exclusive profile lock remains held until Spring/database shutdown completes.
The listener uses port zero on literal loopback, and a per-launch session protects
the local API. A separate management HTTP listener is disabled. Readiness follows
successful service and native window creation; a window failure is a startup
failure, not a usable background application.

This keeps end-user setup to opening the complete app-image's EXE, but it increases
the download size and requires a platform-specific build. The first implementation
is Windows only and unsigned. An app-image is not an installer or a system service.
Real multiuser identity, native signing, other OS packages, encrypted database
storage and populated-user migration acceptance remain separate work.

Primary references: [jpackage](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)
and [Spring graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html).
