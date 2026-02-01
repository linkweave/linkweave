### Project Guidelines

#### Build and Configuration
- **Java Version**: This project requires Java 25 (see `pom.xml` `maven.compiler.release`).
- **Quarkus**: The project uses Quarkus 3.30.8.
- **Database**: SQLite is used for local development and testing. 
  - Database file: `developer-local-settings/chainlink.db`
  - JDBC URL: `jdbc:sqlite:developer-local-settings/chainlink.db`
- **SSL**: Dev mode requires SSL certificates in `developer-local-settings/config/certs/`.
- **Running in Dev Mode**:
  ```bash
  ./mvnw quarkus:dev
  ```

#### Testing
- **Framework**: Use JUnit 5 and `io.quarkus.test.junit.QuarkusTest`.
- **Assertions**: Use **AssertJ** for all assertions (`org.assertj.core.api.Assertions.assertThat`).
- **Audit Fields**: Most entities extend `AbstractEntity`, which has an `AbstractEntityListener`. This listener automatically sets `userErstellt` and `userMutiert` using the `CurrentUserService`.
- **Security in Tests**: To test persistence or services that depend on the current user, you must provide a security context. If `quarkus-test-security` is available, use `@TestSecurity`. Otherwise, manual mocking of `CurrentUserService` or manual setting of audit fields might be required.
- **Sample Test**:
  ```java
  package org.chainlink.api.shared.user;

  import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
  import io.quarkus.test.junit.QuarkusTest;
  import org.junit.jupiter.api.Test;
  import static org.assertj.core.api.Assertions.assertThat;

  @QuarkusTest
  class UserTest {
      @Test
      void testUserCreation() {
          User user = new User();
          user.setVorname("John");
          user.setNachname("Doe");
          user.setEmail(EmailAddress.fromString("john.doe@example.com"));

          assertThat(user.getVornameName()).isEqualTo("John Doe");
          assertThat(user.getEmail().toString()).isEqualTo("john.doe@example.com");
      }
  }
  ```
- **Running Tests**:
  ```bash
  ./mvnw test
  ```

#### Development Information
- **Code Style**: Follow the existing project structure. 
  - Use Lombok for boilerplate (Getter, Setter, ToString, etc.).
  - Use Jakarta Persistence annotations for mapping.
  - Entities should generally extend `AbstractEntity`.
- **Database Migrations**: Uses Flyway. Migration files are located in `src/main/resources/db/migration`.
- **Custom Types**: The project uses several custom types like `ID<T>` and `EmailAddress`. Use their `fromString` or `of` methods for instantiation.
- **Service Layer**: Use `@Service` (custom stereotype) and `@RequiredArgsConstructor` for dependency injection.
