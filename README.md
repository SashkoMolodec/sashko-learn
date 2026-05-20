# Sashko-Learn

AI-powered personal learning tool: syncs Obsidian notes, generates quizzes, answers questions via RAG, and analyzes knowledge gaps — all via Telegram bot.

> For architecture details and dev commands see [CLAUDE.md](CLAUDE.md).

## Features

- **Notes sync** — indexes Obsidian vault (markdown + images via Claude Vision)
- **Quiz generation** — Haiku draft + Sonnet critique pipeline with prompt caching
- **RAG Q&A** — semantic search over notes + AI answer (`/ask`)
- **Deep analysis** — wikilink suggestions and knowledge gap analysis for active note (`/ai_analyze`)
- **Read** — summarize any URL or uploaded `.md`/`.txt` file (`/read`)
- **PDF books** — upload PDF, extract chapters

## Tech Stack

- **Java 24**, Spring Boot 3.5 (single module)
- **Redis** — session, dedup, claim-check cache
- **PostgreSQL + pgvector** — notes, embeddings, quizzes
- **Spring AI** — OpenAI (embeddings), Anthropic Claude (vision, chat, analysis)
- **Apache PDFBox** — PDF parsing
- **Telegram Bot API** — long polling
- **TaskExecutor** — async background work (no Kafka)

## Architecture

Single Spring Boot app on port 8080. Telegram updates are dispatched to command handlers which kick off long-running work on a shared `TaskExecutor` and post results back via the bot. See [CLAUDE.md](CLAUDE.md) for layering and orchestrators.

## Quick Start

```bash
cp .env.template .env
# fill in TELEGRAM_BOT_TOKEN, OPENAI_API_KEY, ANTHROPIC_API_KEY, POSTGRES_PASSWORD
podman-compose up postgres redis
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
docker-compose up redis postgres -d

# Run the app
./sl/gradlew -p sl bootRun
```

Logs: `docker logs sl -f`
