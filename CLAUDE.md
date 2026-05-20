# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Single Gradle/Spring Boot 3.5 module on **Java 24**, located in `sl/`:

```bash
# Build / test
./sl/gradlew -p sl build
./sl/gradlew -p sl test --tests "com.sashkolearn.SomeTest.someMethod"

# Run locally (needs redis + postgres up — see below)
./sl/gradlew -p sl bootRun
```

Full stack via Docker (requires `.env` from `.env.template` plus `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `POSTGRES_PASSWORD`, `TELEGRAM_BOT_TOKEN`, optionally `OBSIDIAN_API_TOKEN`):

```bash
docker-compose up --build                # everything (sl + redis + postgres)
docker-compose up redis postgres -d      # infra only for local bootRun
docker logs sl -f                        # logs
```

## Architecture

Single Spring Boot app (`sl`, port 8080). Telegram updates are translated into command handlers; long-running work (sync, RAG, quiz generation, deep analysis, PDF chapter extraction) is offloaded to a `TaskExecutor` (`aiExecutor`, configured in `AsyncConfig`) so the bot responds with a synchronous ack within milliseconds while heavy work runs in the background and posts results back to Telegram from the worker thread.

### Layering

```
com.sashkolearn
├── SlApplication                  # @SpringBootApplication + @EnableAsync
├── api/telegram/                  # TelegramChatBot (long-polling consumer), DTOs
├── config/                        # AsyncConfig, RedisConfig, NotesConfig,
│                                  # TelegramConfig, TelegramCommandsConfig, FileStorageConfig
├── domain/
│   ├── command/                   # CommandRouter + 7 CommandHandlers
│   ├── service/                   # orchestrators + business services
│   │   └── ai/PromptLoader
│   ├── entity/                    # JPA entities (notes, links, attachments, quizzes, quiz_questions, ai_notes)
│   ├── repository/                # Spring Data JPA repositories
│   └── model/                     # FullSyncResult, ChapterInfo, QuizQuestionView
├── infrastructure/redis/          # RedisService (session + dedup + claim-check)
└── util/VectorUtils
```

### Async pattern (replaces Kafka)

Each command handler returns a short ack string synchronously, then submits the actual work via `aiExecutor.execute(() -> { ... })`. Inside the lambda the handler calls services directly and uses an `@Lazy`-injected `TelegramChatBot` to push results / errors. Bot is `@Lazy` to break the circular dependency `TelegramChatBot → UserInteractionOrchestrator → CommandRouter → handlers → TelegramChatBot`.

`aiExecutor` is sized via `sl.async.core-pool-size` / `max-pool-size` / `queue-capacity` in `application.properties`.

### State

- **Redis** — Telegram session/conversation state, dedup of incoming `update_id`s (`setIfAbsent`), short-lived caches.
- **Postgres + pgvector** — managed by **Flyway** migrations in `sl/src/main/resources/db/migration/`. Hibernate runs in `validate` mode, so schema changes go through a new `V*.sql` migration, not entity-driven DDL. Embedding columns are updated via native queries (see `EmbeddingService`, `VectorUtils`).

### Orchestrators worth knowing

- `UserInteractionOrchestrator` — top-level router for text messages, callbacks, polls, file uploads. Delegates commands to `CommandRouter`, quiz callbacks (`quiz:select:<id>` / `quiz:new`) directly to `QuizCommandHandler.submitFetchAndStart` / `submitGenerateAndStart`, and `/read` document uploads to `ReadCommandHandler.submitFileRead`.
- `BookUploadFlowService` — handles PDF uploads: downloads from Telegram, stores metadata in Redis, submits chapter extraction to `aiExecutor`.
- `NoteSyncOrchestrator` — coordinates `NoteSyncService`, `ObsidianApiService`, `LinkService`, `AttachmentService`, `EmbeddingService` for the Obsidian notes pipeline.
- `QuizFlowService` — poll-based quiz UI (sending `SendPoll`, mapping poll IDs to questions, advancing on answers).

### External integrations

- **Telegram** via `telegrambots-springboot-longpolling-starter` (long polling, no webhook).
- **Spring AI**: OpenAI `text-embedding-3-small` (1536 dims) for embeddings; Anthropic Claude family for chat/vision. Model aliases live in `application.properties` as `ai.model.fast` (Haiku 4.5), `ai.model.standard` (Sonnet 4.6), `ai.model.deep` (Opus 4.7) — change there, not in code.
- **Obsidian Local REST API** at `https://127.0.0.1:27124` (or `host.docker.internal` from Docker) — reads notes from host's Obsidian vault. Path is bind-mounted read-only at `/var/sashko-learn/notes` (see `docker-compose.yaml`); host path `/Users/okravch/my/sl/notes` is hardcoded.
- **File storage** — uploaded PDFs land in the `sl_books` volume (`/var/sashko-learn/books`).

## Conventions

- Lombok is used throughout; annotation processing must be enabled in the IDE.
- Hibernate is in `validate` mode — adding/altering tables means a new Flyway migration file.
- Command handlers inject `@Lazy TelegramChatBot` to avoid the circular dependency on bot — the constructor uses manual `@Qualifier`/`@Lazy` (no `@RequiredArgsConstructor` on those).
- New long-running flow? Submit it via `aiExecutor`, not synchronously on the Telegram thread. Telegram acks must come back in milliseconds or the user sees a hung command.
