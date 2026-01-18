#!/bin/bash
set -e

aws sqs send-message \
  --endpoint-url=http://localhost:4566 \
  --queue-url=http://localhost:4566/000000000000/event-hero-orders-queue \
  --message-body '{
      "heroId": "1",
      "heroName": "Deku",
      "priority": 1
  }' \
  --region us-east-1 \
  --no-sign-request