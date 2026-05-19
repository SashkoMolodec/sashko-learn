# Sashko-Learn

AI-powered personal learning tool: syncs Obsidian notes, generates quizzes, answers questions via RAG, and analyzes knowledge gaps — all via Telegram bot.

> For architecture details and dev commands see [CLAUDE.md](CLAUDE.md).

## Features

- **Notes sync** — indexes Obsidian vault (markdown + images via Claude Vision)
- **Quiz generation** — AI-generated quizzes from your notes with dedup across sessions
- **RAG Q&A** — semantic search over notes + AI answer (`/ask`)
- **Deep analysis** — wikilink suggestions and knowledge gap analysis for active note (`/ai_analyze`)
- **Read** — summarize any URL or uploaded `.md`/`.txt` file (`/read`)
- **PDF books** — upload PDF, extract chapters

## Tech Stack

- **Java 24**, Spring Boot 3.5
- **Redis** — session and cache
- **RedPanda Kafka** — async inter-service messaging
- **PostgreSQL + pgvector** — notes, embeddings, quizzes
- **Spring AI** — OpenAI (embeddings), Anthropic Claude (vision, chat, analysis)
- **Apache PDFBox** — PDF parsing
- **Telegram Bot API** — long polling

## Architecture

Two microservices communicating via Kafka request/response pairs:

- **sl-main-agent** (port 8080) — Telegram bot, routes commands, manages session state in Redis
- **sl-analyze-agent** (port 8081) — all AI/ML work: notes sync, embeddings, RAG, quiz generation, PDF processing

## Quick Start

```bash
cp .env.template .env
# fill in TELEGRAM_BOT_TOKEN, OPENAI_API_KEY, ANTHROPIC_API_KEY, POSTGRES_PASSWORD
docker-compose up --build
```

## Bot Commands

| Command | Description |
|---|---|
| `/sync` | Sync Obsidian notes vault |
| `/find <query>` | Semantic search over notes |
| `/ask <question>` | RAG-based Q&A |
| `/quiz <topic> [-key <note>]` | Generate or load a quiz |
| `/analyze` | Quick note analysis |
| `/ai_analyze` | Deep analysis with link suggestions |
| `/read <url>` | Summarize a URL (or attach `.md`/`.txt`) |

## Local Development

```bash
# Start infrastructure only
docker-compose up redis redpanda postgres -d

# Run each service
./gradlew -p sl-analyze-agent bootRun
./gradlew -p sl-main-agent bootRun
```

Logs: `docker logs sl_main_agent -f` / `docker logs sl_analyze_agent -f`

Kafka inspector: http://localhost:9094
