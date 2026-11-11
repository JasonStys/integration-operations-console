# Local native application

By Jason Stys. Version 1.1.0 adds a self-contained **Windows application image**:
extract its complete directory and open `IntegrationOperationsConsole.exe`.
The Java runtime, real Spring service and built React interface are bundled.
No Java IDE, Java installation, Node, Python or Docker is needed by the user.
This is an unsigned portable application, not an MSI/NSIS installer or a system
service. Native acceptance and publication status are recorded separately.

The small native window opens the console in your browser, explains the local
workspace and provides **Quit application**. The browser includes first-workflow
help and the same exit action. Closing a browser tab alone does not stop the
native application. Quit stops new work, allows active requests to complete,
closes the database, then releases workspace ownership. Slow or failing disk
operations can delay completion; no forced reset or universal shutdown guarantee
is claimed.

## First workflow

1. Link a fictional provider account. Provider/reference pairs are idempotent.
2. Submit a healthy or failing sync using a visible request key.
3. Run the job, review records, retry/cancel/dead-letter behavior and audit.
4. Filter all jobs and download exactly the captured review you see.
5. Quit and reopen: linked accounts, jobs and audit remain in your workspace.

These roles/providers are synthetic demonstrations, not production identities or
external accounts. This application is for a private local OS login, not public
hosting or hostile multiuser operation. There are no ads, analytics or automatic
external provider requests. Explicitly following the author/contact links opens
external sites in your browser.

## Private durable workspace and upgrades

The packaged application uses `%LOCALAPPDATA%\IntegrationOperationsConsole`,
outside both Git and the application directory. It creates a persistent H2 file
database and unique local datasource settings once; it never silently regenerates
an existing configuration. OS-owner permissions protect the managed directory.
The database is not application-level encrypted; OS administrators, malware and
your own OS login remain trusted. Use device encryption where appropriate.

Existing developer settings/defaults are unchanged. The desktop mode preserves
explicit `IOC_DATABASE_URL`, `IOC_DATABASE_USERNAME`, `IOC_DATABASE_PASSWORD` and
an operator's `database.url` in private `desktop.properties`. An explicit custom
database can have different persistence semantics; the UI labels it separately.
No user setup commands are required for the default durable workspace.

Only one process may open a workspace. On failure, existing settings/database
files are preserved. Back up the complete workspace only after the app has quit
and its process has exited. Never delete the database or regenerate credentials
to fix a startup problem. An upgrade replaces application files and retains the
separate workspace; no populated-user upgrade is claimed until actually tested.

The native service binds an OS-assigned port on 127.0.0.1 only. Separate management
HTTP exposure is disabled in this mode. Each launch has an
unpredictable session conveyed in the launch fragment, removed from the address
bar before API use. The tab retains it in origin-bound session storage/memory.
It is never a shared baked-in account password or an external provider key.
Host/Origin and session checks protect data/action endpoints; browser policy
blocks embedded third-party scripts, framing and automatic external connections.
This does not protect against a compromised OS/browser or a modified application.

## Build and validation

Developers need Java 21 JDK, Maven 3.9.6+, Node 24 and PowerShell 7 to build:

```powershell
npm --prefix frontend ci
mvn -f backend/pom.xml verify
./scripts/build-native.ps1 -Output release/windows
node scripts/native-acceptance.mjs release/windows/IntegrationOperationsConsole/IntegrationOperationsConsole.exe artifacts/native
```

The builder uses standard `jlink`/`jpackage`, includes exact source/version
metadata, refuses existing package output and does not publish or install.
The actual EXE acceptance must use disposable profiles and a fresh working
directory, exercise real compiled UI/job actions, reject invalid requests,
download a review, close/restart and verify durable records. Screenshots and
sanitized receipts are evidence about that build, not proof of every device.
`IOC_DESKTOP_TEST=1` permits an explicit absolute `IOC_DESKTOP_DATA` override
solely for this owned acceptance; normal launches ignore that override.

Primary contracts: [jpackage](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html),
[Spring graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html).

See [observed validation](reports/native-application-2026-11-10.md) for the actual
build's boundaries and screenshots. Bundled legal notices are described in
[native notices](native-notices.md).
