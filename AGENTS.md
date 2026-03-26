# AGENTS.md - Chainlink Project Guide

## Project Overview

**Type**: Quarkus-based Java web application with full-stack capabilities  
**Language**: Java 25
**Framework**: Quarkus 3.30.8 (Supersonic Subatomic Java)  
**Build System**: Maven 3.9.12  
**Database**: SQLite with Hibernate ORM  
**Frontend**: vueJs 

## Essential Commands

### Development
```bash
# Build application
./mvnw package

# Build uber-jar
./mvnw package -Dquarkus.package.jar.type=uber-jar

# Clean and build
./mvnw clean verify
```

### Testing
```bash
# Run all tests (unit + integration)
./mvnw verify

# Run only unit tests
./mvnw test

```

### Single Test Execution
```bash
# Run specific test class
./mvnw test -Dtest=ClassNameTest

# Run specific test method
./mvnw test -Dtest=ClassNameTest#methodName

# Run tests matching pattern
./mvnw test -Dtest="*IntegrationTest"
```

## Code Style Guidelines

### Formatting
- **Indentation**: 4 spaces for Java files, 2 spaces for most others
- **Line Length**: 120 characters maximum
- **Encoding**: UTF-8
- **Line Endings**: LF (Unix style)
- **Final Newline**: Always insert final newline
- **Trailing Whitespace**: Trim (except XML files)

### Java Conventions
- **Package**: `org.chainlink` for all application code
- **Class Naming**: PascalCase (e.g., `MyEntity`, `SomePage`)
- **Method Naming**: camelCase (e.g., `getSomePage`, `doSomething`)
- **Field Naming**: camelCase for private fields, can be public for JPA entities
- **Constants**: UPPER_SNAKE_CASE for static final fields
- **Getter/Setter**: Use Lombok annotations (`@Getter`, `@Setter`) for simple entities
### Import Organization
- Group imports: Jakarta/JEE, then third-party, then your own packages
- Use static imports for `java.util.Objects.requireNonNull`
- Avoid wildcard imports except for test classes

### JPA Entity Guidelines
- Use `@Entity` annotation on classes
- Use `@Id` and set a UUID on the server for primary keys
- Fields can be public for simple entities (following existing pattern)
- JPA Data can be used

### REST Endpoint Guidelines
- Use JAX-RS annotations (`@Path`, `@GET`, `@POST`, etc.)
- Inject dependencies via constructor
- Use `requireNonNull()` for null checks in constructors
- Return appropriate media types (`@Produces`)
- Use `@QueryParam` for query parameters

## Project Structure


## Technology Stack

### Backend
- **Quarkus**: Main framework
- **Hibernate ORM and Jakarta Data**: Database operations
- **Hibernate Validator**: Bean validation
- **Flyway**: Database migrations
- **Hibernate Envers**: Entity auditing
- **SQLite**: Local database storage
- **JAX-RS**: REST API

### Frontend
- **VueJS**: WebFramework

## Database Configuration

- **Type**: SQLite
- **Location**: `developer-local-settings/chainlink.db`
- **ORM**: Hibernate with JPA annotations
- **Migrations**: flyway (when needed)

## Testing Guidelines

### Test Framework
- **JUnit 5**: Primary testing framework
- **AssertJ**: Fluent assertions (use `org.assertj.core.api.Assertions.assertThat`)
- **Maven Surefire**: Unit tests (`src/test/java`)
- **Maven Failsafe**: Integration tests (`src/test/java`)
- **Quarkus Test**: use `@QuarkusTest` for integration tests

### Security in Tests
- Most entities extend `AbstractEntity`, which has an `AbstractEntityListener` that automatically sets `userErstellt` and `userMutiert` using `CurrentUserService`
- To test persistence/services that depend on current user, provide a security context, use `@TestSecurity`


### Test Naming
- Unit tests: `ClassNameTest`
- Integration tests: `ClassNameIT` or `*IntegrationTest`
- Test methods: descriptive camelCase starting with `should`

### Test Configuration
- Uses custom logging manager: `org.jboss.logmanager.LogManager`
- Requires `--add-opens java.base/java.lang=ALL-UNNAMED` for module access

## Development Workflow

1. **Local Development**: Use `./mvnw quarkus:dev` for hot reload
2. **Database**: SQLite automatically created in developer-local-settings/
3. **Frontend**: Use Web Bundler for asset management
4. **Templates**: Qute templates auto-reload in dev mode
5. **API Testing**: Dev UI available at `http://localhost:8080/q/dev/`

## Error Handling

- Use `requireNonNull()` for constructor parameter validation
- Follow Quarkus exception handling patterns
- Use appropriate HTTP status codes for REST endpoints
- Log errors appropriately (Quarkus uses SLF4J)

## Security Considerations

- Never commit secrets or configuration files with credentials
- Use environment variables for sensitive data
- Follow Java security best practices
- Validate all input using Hibernate Validator

## Docker Support

Multiple Dockerfiles available:
- `Dockerfile.jvm`: JVM mode

## Common Patterns

### Constructor Injection
```java
public SomePage(Template page) {
    this.page = requireNonNull(page, "page is required");
}
```
Or better yet use @RequiredArgsConstructor from lombok

Annotate the Resource with the @JaxRendereable stereotype

### JPA Entities
```java
@Entity
public class MyEntity {
    @Id
    @GeneratedValue
    public Long id;
    public String field;
}

```
## Architecture
- adhere to the layering model, the following layers exist: Entities, Repository, Repository, Service, Resource. There are stereotype annotations like org.chainlink.infrastructure.stereotypes.JaxResource available

## Custom Types
- The project uses several custom types like `ID<T>` and `EmailAddress`
- Use their `fromString` or `of` methods for instantiation
## CI/CD

- **GitTea Actions**: Runs on push/PR to main
- **JDK Version**: Temurin JDK 21
- **Build Command**: `./mvnw verify -B`
- **Caching**: Maven dependencies cached automatically



