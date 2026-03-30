# AGENTS.md - Chainlink Project Guide

## Project Overview

**Type**: Quarkus-based Java web application with full-stack capabilities  
**Language**: Java 25
**Framework**: Quarkus 3.30.8  
**Build System**: Maven 3.9.12  
**Database**: SQLite with Hibernate ORM  
**Frontend**: VueJS 

## Essential Commands

All Maven commands must be run from the `api/` directory (or use `-f api/pom.xml` from root).

```bash
cd api && ./mvnw package            # Build
cd api && ./mvnw verify             # All tests (unit + integration)
cd api && ./mvnw test -Dtest=ClassNameTest           # Specific class
cd api && ./mvnw quarkus:dev        # Dev mode with hot reload, assume running
cd frontend && npm run dev # frontend dev mode, assume running
```

## Architecture

Adhere to the layering model: **Entities → Repository → Service → Resource**.  
Use stereotype annotations like `org.chainlink.infrastructure.stereotypes.JaxResource` and `@JaxRendereable`.

## Conventions

- **Package**: `org.chainlink` for all application code
- Use `@RequiredArgsConstructor` (Lombok) for constructor injection, or `requireNonNull()` manually
- JPA entities have private fields made accessable using lombok @Getter and @Setter
- Serverside Architecture adheres to a 3 tiered architecture:
- **Persistence**: JPA Entites and Repositories (naming convetion: `ClassNameRepo`)
- **Services**: Services are responsible for business logic and orchestration of repositories. They should not contain any persistence logic. Naming convention: `ClassNameService`
- **Resources**: Resources are responsible for exposing the services to the outside world. They never return entites but rather DTOs ending in Json Naming convention: `ClassNameResource`
- Test naming: unit tests `ClassNameTest`, integration tests `ClassNameIT` or `*IntegrationTest`
- Test methods: descriptive camelCase starting with `should`
- Use `@TestSecurity` when testing persistence/services that depend on current user
- Most entities extend `AbstractEntity` which auto-sets `userErstellt` and `userMutiert` via `CurrentUserService`

## Custom Types

The project uses custom types like `ID<T>` and `EmailAddress`. Use their `fromString` or `of` methods for instantiation.

## Database

- **Location**: `developer-local-settings/chainlink.db`
- **Migrations**: Flyway

### Security in Tests
- Most entities extend `AbstractEntity`, which has an `AbstractEntityListener` that automatically sets `userErstellt` and `userMutiert` using `CurrentUserService`
- To test persistence/services that depend on current user, provide a security context, use `@TestSecurity`


### Test Naming
- Unit tests: `ClassNameTest`
- Integration tests: `ClassNameIT` or `*IntegrationTest`
- Test methods: descriptive camelCase starting with `should`
