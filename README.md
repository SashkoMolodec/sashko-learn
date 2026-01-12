# Sashko-Learn

Learning tool for technical programming books using AI-generated quizzes via Telegram bot.

## MVP Features

- Upload PDF books via Telegram
- Automatic chapter extraction using Apache PDFBox
- Chapter list display
- All data stored in Redis (lightweight MVP)

## Tech Stack

- **Java 24**, Spring Boot 3.5.7
- **Redis** - primary storage for MVP
- **RedPanda Kafka** - inter-service messaging
- **Apache PDFBox** - PDF parsing
- **Telegram Bot API**
- **Docker Compose**

## Architecture

Two microservices:
- **sl-main-agent** (port 8080) - Telegram bot interface, user interaction
- **sl-analyze-agent** (port 8081) - PDF processing, chapter extraction

Communication via Kafka topics:
- `extract-chapters-tasks` - Main → Analyze
- `extract-chapters-results` - Analyze → Main

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Telegram Bot Token (get from [@BotFather](https://t.me/BotFather))

### Setup

1. **Create `.env` file:**

```bash
cp .env.template .env
```

2. **Edit `.env` with your Telegram bot token:**

```env
TELEGRAM_BOT_TOKEN=your_bot_token_from_botfather
TELEGRAM_BOT_USERNAME=your_bot_username
```

3. **Start all services:**

```bash
docker-compose up --build
```

This will start:
- Redis (port 6379)
- RedPanda Kafka (port 9092)
- RedPanda Console (http://localhost:9094)
- sl-main-agent (port 8080)
- sl-analyze-agent (port 8081)

### Usage

1. Open Telegram and find your bot
2. Send `/start` to begin
3. Upload a PDF book
4. Wait for chapter analysis
5. View extracted chapters!

## Development

### Run services locally (without Docker)

**Terminal 1 - Start infrastructure:**
```bash
docker-compose up redis redpanda -d
```

**Terminal 2 - Run analyze-agent:**
```bash
cd sl-analyze-agent
./gradlew bootRun
```

**Terminal 3 - Run main-agent:**
```bash
cd sl-main-agent
export TELEGRAM_BOT_TOKEN=your_token
./gradlew bootRun
```

### Check logs

```bash
docker logs sl_main_agent -f
docker logs sl_analyze_agent -f
```

### Monitor Kafka messages

Open RedPanda Console: http://localhost:9094
- View topics: `extract-chapters-tasks`, `extract-chapters-results`
- Inspect messages

## Project Structure

```
sl/
├── docker-compose.yaml
├── .env.template
├── sl-main-agent/
│   ├── src/main/java/com/sashkolearn/mainagent/
│   │   ├── api/telegram/          # Telegram bot
│   │   ├── config/                # Spring configs
│   │   ├── domain/service/        # Business logic
│   │   ├── infrastructure/redis/  # Redis integration
│   │   └── messaging/             # Kafka DTOs, producers, consumers
│   ├── build.gradle
│   └── Dockerfile
└── sl-analyze-agent/
    ├── src/main/java/com/sashkolearn/analyzeagent/
    │   ├── domain/service/        # PDF processing logic
    │   ├── infrastructure/redis/  # Redis integration
    │   └── messaging/             # Kafka DTOs, producers, consumers
    ├── build.gradle
    └── Dockerfile
```

## Commands

- `/start` - Welcome message
- `/help` - Show help
- `/mybooks` - List uploaded books
- `/quiz` - Start quiz (coming in next phase)

## Future Enhancements

- PostgreSQL integration (after user approves chapters)
- Google Gemini AI for quiz generation
- Interactive quiz sessions
- Progress tracking
- Spaced repetition algorithm

## Troubleshooting

**Bot doesn't respond:**
- Check TELEGRAM_BOT_TOKEN in .env
- Check logs: `docker logs sl_main_agent`

**Chapters not extracted:**
- Check PDF is valid and not encrypted
- Check logs: `docker logs sl_analyze_agent`
- Monitor Kafka in RedPanda Console

**Services not starting:**
- Ensure ports 6379, 8080, 8081, 9092, 9094 are free
- Run `docker-compose down -v` and try again

## License

MIT
