#!/bin/bash
# cleanup-and-restart.sh - Clean mongo and kafka volumes and restart docker-compose

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/.."

echo "Stopping docker-compose services..."
cd "$COMPOSE_DIR"
docker-compose down

echo "Removing volumes..."
docker volume rm $(docker volume ls -q | grep -E "events.*mongo|events.*kafka" || true) 2>/dev/null || true

echo "Starting docker-compose services..."
docker-compose up -d

echo "Waiting for services to be ready..."
# Wait for MongoDB
until docker-compose exec -T mongodb mongosh --eval "db.adminCommand('ping')" > /dev/null 2>&1; do
  echo "Waiting for MongoDB..."
  sleep 2
done

# Wait for Kafka
until docker-compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1; do
  echo "Waiting for Kafka..."
  sleep 2
done

# Wait for events service
until curl -s http://localhost:4055/health > /dev/null 2>&1; do
  echo "Waiting for events service..."
  sleep 2
done

echo "All services ready!"
