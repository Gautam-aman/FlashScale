#!/usr/bin/env bash
set -euo pipefail

ITEM_ID="${1:-}"

if [[ ! "$ITEM_ID" =~ ^[0-9]+$ ]]; then
  echo "Usage: $0 <flash-sale-item-id>"
  exit 1
fi

echo "All API instances and benchmark clients must be stopped."
read -r -p "Type STOPPED to continue: " CONFIRM

if [[ "$CONFIRM" != "STOPPED" ]]; then
  echo "Reconciliation cancelled."
  exit 1
fi

AVAILABLE=$(
  docker compose exec -T db \
    psql -qAt \
    -U flashscale \
    -d flashscale \
    -c "SELECT available_stock
        FROM flash_sale_items
        WHERE id = ${ITEM_ID};"
)

if [[ ! "$AVAILABLE" =~ ^[0-9]+$ ]]; then
  echo "Could not read valid PostgreSQL inventory."
  exit 1
fi

KEY="flashscale:stock:item:${ITEM_ID}"

docker compose exec -T redis redis-cli EVAL \
  "redis.call('DEL', KEYS[2], KEYS[3]);
   redis.call('SET', KEYS[1], ARGV[1]);
   return 1" \
  3 \
  "$KEY" \
  "$KEY:pending" \
  "$KEY:quantities" \
  "$AVAILABLE"

echo "Reconciled item $ITEM_ID to $AVAILABLE units."
