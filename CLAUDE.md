# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Two independent Gradle/Spring Boot 3.5 services on **Java 24**. Each has its own wrapper — always `cd` into the service dir or use `-p`:

```bash
# Build / test a single service
./gradlew -p sl-main-agent build
./gradlew -p sl-analyze-agent build

# Single test
./gradlew -p sl-analyze-agent test --tests "com.sashkolearn.analyzeagent.SomeTest.someMethod"

# Run locally (needs redis + redpanda + postgres up — see below)
./gradlew -p sl-main-agent bootRun
./gradlew -p sl-analyze-agent bootRun
```

Full stack via Docker (requires `.env` from `.env.template` plus `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `POSTGRES_PASSWORD`, optionally `OBSIDIAN_API_TOKEN`):

```bash
docker-compose up --build                       # everything
docker-compose up redis redpanda postgres -d    # infra only for local bootRun
docker logs sl_main_agent -f                    # logs
```

RedPanda Console (Kafka inspector): http://localhost:9094 — useful when debugging message flow.

## Architecture

Two-service request/response system glued together by Kafka. The README still describes the original PDF-chapter MVP, but the codebase has grown well past it; treat the README as out of date for scope.

### Services

- **sl-main-agent** (port 8080) — Telegram bot front end. Holds no domain knowledge; it translates Telegram updates into Kafka task messages, awaits result messages, and writes user/session state to Redis.
- **sl-analyze-agent** (port 8081) — All heavy lifting: PDF parsing (PDFBox), Obsidian notes sync, embedding + RAG (pgvector), quiz generation, AI image/note analysis (Spring AI: OpenAI for embeddings, Anthropic Claude for vision/chat). Owns Postgres.

### Kafka request/response convention

For every capability there is a paired `*_task` (main → analyze) and `*_result` (analyze → main) topic. Each side has a mirrored `messaging/producer` + `messaging/consumer` package with parallel DTO classes. Type routing is done via Spring Kafka **JSON type mappings** in each service's `application.properties` — both sides must list the same logical key (e.g. `extract_chapters_task`) mapped to their own DTO class. **When adding a new task type, you must update the type-mapping strings in both `sl-main-agent/.../application.properties` and `sl-analyze-agent/.../application.properties`**, or messages will fail to deserialize.

Currently wired flows (task/result pairs): `extract_chapters`, `sync_notes`, `ask_question`, `analyze_note`, `find_notes`, `analyze_ai`, `quiz_generate`, `quiz_search`, `quiz_get`, `read`. Consumers use `max.poll.records=1` and a 30-min `max.poll.interval.ms` because analysis work is slow.

### State

- **Redis** — Telegram session/conversation state and short-lived caches in both services (`infrastructure/redis/RedisService`).
- **Postgres + pgvector** — analyze-agent only; managed by **Flyway** migrations in `sl-analyze-agent/src/main/resources/db/migration/`. Hibernate runs in `validate` mode, so schema changes go through a new `V*.sql` migration, not entity-driven DDL. Entities live in `domain/entity/` (notes, links, attachments, quizzes, quiz_questions, ai_notes). Embedding columns are updated via native queries (see `EmbeddingService`, `VectorUtils`).

### Layering inside each service

`api/` (main-agent only — Telegram bot) → `domain/service/` (orchestrators + business logic) → `messaging/` (Kafka producers/consumers + DTOs) → `infrastructure/` (Redis) + `domain/repository/` (Spring Data JPA, analyze-agent only).

Notable orchestrators worth knowing before changing flows:
- `mainagent.domain.service.UserInteractionOrchestrator` — top-level Telegram update router.
- `mainagent.domain.service.{BookUploadFlowService,QuizFlowService,SessionManagementService}` — per-feature state machines.
- `analyzeagent.domain.service.NoteSyncOrchestrator` — coordinates `NoteSyncService`, `ObsidianApiService`, `LinkService`, `AttachmentService`, `EmbeddingService` for the notes pipeline.

### External integrations

- **Telegram** via `telegrambots-springboot-longpolling-starter` (long polling, no webhook).
- **Spring AI**: OpenAI `text-embedding-3-small` (1536 dims) for embeddings; Anthropic `claude-sonnet-4-6` for vision/chat. Configured in `sl-analyze-agent/application.properties`.
- **Obsidian Local REST API** at `https://127.0.0.1:27124` — analyze-agent reads notes from the host's Obsidian vault. Path is also bind-mounted read-only at `/var/sashko-learn/notes` (see `docker-compose.yaml`); the host path `/Users/okravch/my/sl/notes` is hardcoded there.
- **File storage** — uploaded PDFs land in the `sl_books` volume (`/var/sashko-learn/books`), written by main-agent and read by analyze-agent.

## Conventions

- DTO classes on the two sides of a Kafka topic are **deliberately duplicated** (one per service package). Don't try to extract them into a shared module — the JSON type-mapping config relies on package-qualified class names per service.
- Lombok is used throughout; annotation processing must be enabled in the IDE.
- Hibernate is in `validate` mode — adding/altering tables means a new Flyway migration file.
