#!/bin/bash
set -e

echo "🚀 Starting ShortLink Platform Local Development Environment..."
echo ""

# Start all services
docker-compose up -d

echo ""
echo "⏳ Waiting for services to be healthy..."
sleep 10

# Check service status
echo ""
echo "📊 Service Status:"
docker-compose ps

echo ""
echo "✅ Services started! You can now:"
echo "  - Connect to PostgreSQL: localhost:5432 (admin/admin)"
echo "  - Connect to Redis: localhost:6379 (password: StrongRedisPass123!)"
echo "  - Connect to Kafka: localhost:9092"
echo "  - Connect to ClickHouse: localhost:8123 (default/default)"
echo ""
echo "📝 View logs: docker-compose logs -f [service-name]"
echo "🛑 Stop services: docker-compose down"
echo "🗑️  Stop and remove data: docker-compose down -v"
