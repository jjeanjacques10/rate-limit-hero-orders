#!/bin/bash
set -e

# Array com nomes de heróis de My Hero Academia
heroes=(
  "Deku"
  "All Might"
  "Bakugo"
  "Todoroki"
  "Uraraka"
  "Iida"
  "Kirishima"
  "Mina"
  "Denki"
  "Tokoyami"
  "Tsuyu"
  "Momo"
  "Jiro"
  "Sero"
  "Koda"
  "Aoyama"
  "Mineta"
  "Ojiro"
  "Shoji"
  "Sato"
)

ENDPOINT_URL="http://localhost:4566"
QUEUE_URL="http://localhost:4566/000000000000/event-hero-orders-queue"

echo "Enviando 20 mensagens em batch de alta velocidade..."

# Loop para enviar 20 mensagens
for i in {1..20}; do
  hero_index=$((i - 1))
  hero_name="${heroes[$hero_index]}"
  priority=$((RANDOM % 5 + 1))  # Prioridade aleatória entre 1 e 5

  # Envia mensagem em background para alta velocidade
  aws sqs send-message \
    --endpoint-url=$ENDPOINT_URL \
    --queue-url=$QUEUE_URL \
    --message-body "{
      \"heroId\": \"$i\",
      \"heroName\": \"$hero_name\",
      \"priority\": $priority
    }" \
    --region us-east-1 \
    --no-sign-request &

  echo "[$i/20] Mensagem enviada: $hero_name (ID: $i, Priority: $priority)"
done

# Aguarda todos os processos em background finalizarem
wait

echo "Todas as 20 mensagens foram enviadas com sucesso!"
