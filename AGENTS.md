# AGENTS.md - Chainlink Project Guide

This document provides essential information for agentic coding agents working on the Chainlink project.

## Project Overview

**Type**: Quarkus-based Java web application with full-stack capabilities  
**Language**: Java 25
**Framework**: Quarkus 3.30.8 (Supersonic Subatomic Java)  
**Build System**: Maven 3.9.12  
**Database**: SQLite with Hibernate ORM  
**Frontend**: JavaScript/SCSS with Web Bundler  

## Essential Commands

### Development
```bash
# Start development server with hot reload
./mvnw quarkus:dev

# Build application
./mvnw package

# Build uber-jar
./mvnw package -Dquarkus.package.jar.type=uber-jar

# Build native executable
./mvnw package -Dnative

# Build native in container
./mvnw package -Dnative -Dquarkus.native.container-build=true

# Clean and build
./mvnw clean verify
```

### Testing
```bash
# Run all tests (unit + integration)
./mvnw verify

# Run only unit tests
./mvnw test

# Run only integration tests
./mvnw verify -DskipTests=false -DskipITs=false

# Run tests with native profile
./mvnw verify -Pnative
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

### Import Organization
- Group imports: Jakarta/JEE, then third-party, then your own packages
- Use static imports for `java.util.Objects.requireNonNull`
- Avoid wildcard imports except for test classes

### JPA Entity Guidelines
- Use `@Entity` annotation on classes
- Use `@Id` and set a UUID on the server for primary keys
- Fields can be public for simple entities (following existing pattern)
- Document entities with Javadoc including usage examples

### REST Endpoint Guidelines
- Use JAX-RS annotations (`@Path`, `@GET`, `@POST`, etc.)
- Inject dependencies via constructor
- Use `requireNonNull()` for null checks in constructors
- Return appropriate media types (`@Produces`)
- Use `@QueryParam` for query parameters

## Project Structure

```
src/main/java/org/chainlink/     # Java source code
src/main/resources/
├── application.properties        # Quarkus configuration
├── templates/                   # Qute templates
│   ├── *.qute.html             # Server-side templates
│   └── pub/                    # Static HTML files
└── web/                        # Frontend assets
    ├── app.js                  # JavaScript application
    └── app.scss                # SCSS stylesheets
```

## Technology Stack

### Backend
- **Quarkus 3.30.8**: Main framework
- **Hibernate ORM**: Database operations
- **Hibernate Validator**: Bean validation
- **Liquibase**: Database migrations
- **Hibernate Envers**: Entity auditing
- **SQLite**: Local database storage
- **JAX-RS**: REST API
- **Qute**: Template engine

### Frontend
- **Web Bundler 2.2.0**: Asset bundling
- **JavaScript**: ES6 modules supported
- **SCSS**: Stylesheet preprocessing
- **Qute Web**: Template serving

## Database Configuration

- **Type**: SQLite
- **Location**: `developer-local-settings/chainlink.db`
- **ORM**: Hibernate with JPA annotations
- **Migrations**: Liquibase (when needed)

## Testing Guidelines

### Test Framework
- **JUnit 5**: Primary testing framework
- **AssertJ**: Fluent assertions
- **Maven Surefire**: Unit tests (`src/test/java`)
- **Maven Failsafe**: Integration tests (`src/test/java`)
- **Quarkus Test**: use `@QuarkusTest` for integration tests

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

## Performance Guidelines

- Leverage Quarkus build-time optimizations
- Use native compilation for production when beneficial
- Consider GraalVM native image for containerized deployments
- Profile using Quarkus development tools

## Package Management

- Add dependencies via `pom.xml`
- Use Quarkus BOM for version management
- Web dependencies: Use Maven with `provided` scope for npm packages
- Frontend: Import modules in `app.js`, bundled automatically

## Docker Support

Multiple Dockerfiles available:
- `Dockerfile.jvm`: JVM mode
- `Dockerfile.native`: Native compilation
- `Dockerfile.native-micro`: Micro native
- `Dockerfile.legacy-jar`: Legacy JAR deployment

## Common Patterns

### Constructor Injection
```java
public SomePage(Template page) {
    this.page = requireNonNull(page, "page is required");
}
```

### REST Endpoints
```java
@GET
@Produces(MediaType.TEXT_HTML)
public TemplateInstance get(@QueryParam("name") String name) {
    return page.data("name", name);
}
```

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

## CI/CD

- **GitHub Actions**: Runs on push/PR to main
- **JDK Version**: Temurin JDK 21
- **Build Command**: `./mvnw verify -B`
- **Caching**: Maven dependencies cached automatically

## Environment Variables

- `maven.home`: Automatically set for tests
- `native.image.path`: Used for integration tests with native builds

## Additional Notes

- German comments in `.editorconfig` indicate previous German development
- Default port: 8080
- Development mode includes comprehensive Dev UI
- Quarkus live reloads both Java and web resources
- Database schema managed by Hibernate unless Liquibase migrations exist
