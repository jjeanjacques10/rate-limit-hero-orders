#!/bin/bash
set -e

echo "🚀 Criando filas SQS no LocalStack..."

# Fila padrão
awslocal sqs create-queue \
  --queue-name event-hero-orders-queue

# Fila de dead-letter (DLQ)
awslocal sqs create-queue \
  --queue-name event-hero-orders-queue-dlq

echo "✅ Filas SQS criadas com sucesso!"

echo "📋 Listando filas:"
awslocal sqs list-queues
