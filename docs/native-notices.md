# Bundled application notices

Integration Operations Console is by Jason Stys; its license is in `LICENSE.txt`.
The self-contained application also contains third-party software:

- The Java runtime is built from the selected OpenJDK distribution. Its bundled
  `runtime/legal` directory retains the runtime's license and legal notices.
- React and React DOM are MIT licensed; complete notices are copied into
  `licenses/React-LICENSE.txt` and `licenses/ReactDOM-LICENSE.txt`.
- The executable Spring Boot archive retains nested dependency archives,
  including their `META-INF` license/notice and Maven metadata. The source
  dependency inventory is declared by `backend/pom.xml` and its resolved graph.
  Important runtime families include Spring, Tomcat, Jackson, HikariCP, H2,
  Flyway, Micrometer and the PostgreSQL driver.

These notices describe the application image, not a claim that source or binary
redistribution eliminates the obligations in the individual licenses. The build
does not alter OS security checks, code-sign or grant administrator privileges.
