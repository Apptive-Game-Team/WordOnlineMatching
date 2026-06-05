# Repository Guidelines

## Project Structure & Module Organization

This repository is `WordOnlineMatching`, the lobby and matchmaking server for Word Online. Source code lives under `src/main/java/com/wordonline/matching` and `src/main/kotlin/com/wordonline/matching`; both Java and Kotlin are active. Feature packages include `auth`, `matching`, `session`, `server`, `deck`, `magic`, `quest`, `adventure`, `data`, `decoration`, `deploy`, and `global`. Tests are in `src/test/java`. Runtime configuration and SQL helpers are in `src/main/resources`, while API notes are in `docs/api`.

Related repositories:
- `Apptive-Game-Team/WordOnlineServer`: actual game session server.
- `Apptive-Game-Team/WordOnlineClient`: Unity client consuming these APIs.
- `Apptive-Game-Team/AccountServer`: account/member service used by `AccountClient`.
- `Apptive-Game-Team/WordOnlineDatabase`: database migration source of truth.

## Build, Test, and Development Commands

- `./gradlew compileJava compileKotlin`: compile Java and Kotlin sources.
- `./gradlew test`: run JUnit 5 tests, including WebFlux and Reactor tests.
- `./gradlew bootRun`: run the Spring Boot app locally.
- `./gradlew bootJar`: create the deployable Spring Boot jar.

Local runs require environment variables from `application.yml`: `PORT`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `ACCOUNT_SERVER_URL`, `JWT_PRIVATE_KEY`, and `JWT_PUBLIC_KEY`. For schema questions, inspect `../database/migration` first; do not rely on this repo's `src/main/resources/schema.sql` as authoritative.

## Coding Style & Naming Conventions

Use package-by-feature organization. Java uses Lombok where already established; Kotlin is used for newer matching/deploy code. Keep controllers thin, business logic in services, and database access in repositories. Prefer reactive types (`Mono`, `Flux`) in Java and coroutine interop in Kotlin. Use 4-space indentation and descriptive DTO names such as `MatchedInfoDto` or `DeployStatusResponse`.

## Testing Guidelines

Tests use JUnit 5, Spring Boot Test, Spring Security Test, and Reactor Test. Name tests by behavior; existing tests include Korean display-style method names. Add focused tests for controller response shapes, service branching, and repository queries when behavior changes. Run `./gradlew test` before handoff.

## Commit & Pull Request Guidelines

Recent history uses short conventional-style prefixes such as `feat:`, `fix:`, and `refactor(...)`, often with linked issue numbers like `(#36)`. Keep commits scoped to one concern. PRs should describe behavior changes, link the GitHub issue, mention affected endpoints, and note any required database migration or client/game-server/account-server coordination.

Workflow for tracked work in this repo:
- Create a GitHub issue before implementation when the user asks for end-to-end delivery.
- Create and work on a dedicated branch per issue.
- Use branch names in the form `<issue-label>/<issue-num>` such as `feature/123` or `fix/39`.
- After implementation and verification, open a PR linked to the issue.

## Security & Configuration Tips

Do not commit secrets or local `.env` values. JWT keys, database credentials, Redis settings, and account server URLs must remain environment-driven. Database schema changes belong in `WordOnlineDatabase`; treat lobby-server `schema.sql` as stale/local reference material unless verified against the database repo.
