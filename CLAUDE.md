# FashionVista Backend

REST API and business logic for FashionVista e-commerce. Java Spring Boot application.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Build | Maven (mvnw wrapper) |
| Database | MySQL / PostgreSQL (via JPA/Hibernate) |
| Auth | Spring Security + JWT |
| Email | Spring Mail / SMTP |
| Container | Docker + Docker Compose |
| API | REST (JSON) |

## Folder Structure

```
src/
  main/
    java/com/fashionvista/
      controller/   REST controllers (@RestController)
      service/      Business logic (@Service)
      repository/   JPA repositories
      entity/       JPA entities
      dto/          Data Transfer Objects
      config/       Spring config classes
      security/     JWT, Spring Security config
    resources/
      application.properties  Main config (no secrets)
      application-*.properties  Profile-specific configs
  test/             Unit and integration tests
pom.xml             Maven dependencies
Dockerfile          Container image definition
docker-compose.yml  Full stack local dev
docker-compose.local.yml  Local overrides
```

## Commands

```powershell
cd D:\FashionVista\FashionVista_Backend

# Run
./mvnw spring-boot:run                    # Dev server (:8080)
docker-compose -f docker-compose.local.yml up  # Full stack via Docker

# Build
./mvnw clean package                      # Build JAR → target/
./mvnw clean package -DskipTests          # Build without tests

# Test
./mvnw test                               # All tests
./mvnw test -Dtest=ClassName              # Specific test class

# Code quality
./mvnw checkstyle:check                   # Checkstyle (if configured)
```

## API Base URL

`http://localhost:8080/api/v1` (dev)

## Conventions

- Controller → Service → Repository layering (no business logic in controllers)
- DTOs for request/response bodies (never expose entity directly)
- `@Valid` on request body params for validation
- Return `ResponseEntity<?>` from controllers
- Exceptions go through `@ControllerAdvice` global handler
- No raw SQL unless absolutely necessary — use JPQL or Criteria API

## Known Context

- Forgot password uses SMTP email delivery (recent fix: SSL config)
- Auth uses in-memory approach (recent refactor across all 3 projects)
- Phase 2 features: cancel order, GHN shipping, wishlist — see `docs/` folder
