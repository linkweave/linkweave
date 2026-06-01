# AGENTS.md - Chainlink Project Guide

## Project Overview

**Type**: Quarkus-based Java web application with vue frontend
**Language**: Java 25 / typescript
**Build System**: Maven 3.9.12 and pnpm 11
**Database**: SQLite with Hibernate ORM  
**Frontend**: VueJS 

## Essential Commands

Always make sure that at the end of your task you run a build for the relevant module (api or frontend)
Verify that your changes don't break any existing tests.
**Always add tests for new features and bug fixes** — backend integration tests for new endpoints/services, frontend component or E2E tests as appropriate.
The web app is running at https://local-chainlink.localhost:5173 the quarkus is at 8443


```bash
cd api && ./mvnw package            # Build
cd api && ./mvnw verify             # All tests (unit + integration)
cd api && ./mvnw test -Dtest=ClassNameTest           # Specific class
cd api && ./mvnw quarkus:dev        # Dev mode with hot reload, assume running
cd frontend && pnpm run dev # frontend dev mode, assume running
cd frontend && pnpm run type-check # frontend type checking
cd frontend && pnpm run lint       # frontend linting (oxlint + eslint)
pnpm exec playwright test --project=chromium  

```

## Architecture

Adhere to the layering model: **Entities → Repository → Service → (Mapper) → Resource**.  
Use stereotype annotations like `@JaxResource` and `@Service`. They mark the layer AND the include **@Transactional** by default.

### Authorization

All access checks are performed in the **Resource layer** using `AuthorizationService`.
- Services and Repositories never perform authorization checks — they trust the caller.
- `AuthorizationService` (`org.chainlink.api.shared.auth.AuthorizationService`) is the single source of truth for "can user X access resource Y".
- Use `require*` methods (e.g. `requireCollectionAccess`) for guard clauses — they throw `AppAuthorizationException` (HTTP 403).
- Use `has*` methods (e.g. `hasCollectionAccess`) for conditional branching — they return boolean.
- Never inject `CollectionAccessRepo` or `CurrentUserService` into business services for authorization purposes.

## Conventions

- Use `@RequiredArgsConstructor` (Lombok) for constructor injection, or `requireNonNull()` manually
- JPA entities have private fields made accessible using lombok @Getter and @Setter, this may lead to lsp errors, you can ignore those
- Specify field lengths of @Coolumns in entities using constants from `DbConst`
- Serverside Architecture adheres to a 3 tiered architecture:
- **Persistence**: JPA Entites and Repositories (naming convetion: `ClassNameRepo`)
- **Services**: Services are responsible for business logic and orchestration of repositories. They should not contain any persistence logic. Naming convention: `ClassNameService`
- **Resources**: Resources are responsible for exposing the services to the outside world. They never return entites but rather DTOs ending in Json Naming convention: `ClassNameResource`
- Test naming: unit tests `ClassNameTest`, integration tests `ClassNameIT` or `*IntegrationTest`
- Test methods: descriptive camelCase starting with `should`
- Use `@TestSecurity` when testing persistence/services that depend on current user
- Most entities extend `AbstractEntity` which auto-sets `userErstellt` and `userMutiert` via `CurrentUserService`
- Use `@AllArgsConstructor` for entity classes to ensure all fields are initialized
- Always check your code by running pnpm run type-check, pnpm run lint and by compiling using maven
- Use zod schemas to validate form input

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
- Integration tests: `ClassNameITest`
- Test methods: descriptive camelCase starting with `should`

### Api
- The project lets quarkus generate the openapi.json file on its default endpoint.
- to generate the frontend code, use the `pnpm run generate-api` command.
