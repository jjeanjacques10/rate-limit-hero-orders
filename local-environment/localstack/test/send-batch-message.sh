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
  "Hagakure"
  "Mirio"
  "Tamaki"
  "Nejire"
  "Hawks"
  "Endeavor"
  "Shigaraki"
  "Dabi"
  "Toga"
  "Kurogiri"
  "Spinner"
  "Mr. Compress"
  "Twice"
  "Present Mic"
  "Midnight"
)

ENDPOINT_URL="http://localhost:4566"
QUEUE_URL="http://localhost:4566/000000000000/event-hero-orders-queue"

# Número total de heróis no array
total_heroes=${#heroes[@]}

echo "Enviando 80 mensagens com heróis escolhidos ALEATORIAMENTE..."

for i in {1..80}; do
  # Escolhe índice aleatório entre 0 e (total_heroes-1)
  random_index=$((RANDOM % total_heroes))

  # Pega o nome do herói
  hero_name="${heroes[random_index]}"

  # Garante que nunca vai ser vazio/nulo (defesa extra)
  if [ -z "$hero_name" ]; then
    hero_name="Unknown Hero"
  fi

  priority=$((RANDOM % 5 + 1))  # Prioridade aleatória entre 1 e 5

  # Envia mensagem em background para maior velocidade
  aws sqs send-message \
    --endpoint-url="$ENDPOINT_URL" \
    --queue-url="$QUEUE_URL" \
    --message-body "{
      \"heroId\": \"$i\",
      \"heroName\": \"$hero_name\",
      \"priority\": $priority
    }" \
    --region us-east-1 \
    --no-sign-request &>/dev/null &

  # Mostra progresso (mas sem travar o envio)
  printf "[$i/80] Enviado: %-12s (Priority: %d)\r" "$hero_name" "$priority"
done

# Aguarda todos os envios em background terminarem
wait

echo -e "\n\nTodas as 80 mensagens foram enviadas com sucesso!"