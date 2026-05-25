#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "❌  .env не знайдено: $ENV_FILE"
  echo "    Скопіюй .env.template → .env і заповни значення"
  exit 1
fi

# ── Git pull ───────────────────────────────────────────────────────────────────
echo "📥  Оновлюємо код з GitHub..."
git pull --rebase --autostash origin main

# ── Docker ────────────────────────────────────────────────────────────────────
echo ""
echo "🔨  Збираємо образ і запускаємо..."
docker compose --env-file "$ENV_FILE" up -d --build

echo ""
echo "✅  Готово!"
echo "    Логи:   docker logs sl -f"
echo "    Стоп:   docker compose down"
