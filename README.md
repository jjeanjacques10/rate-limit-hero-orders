# My Hero Academia - Solicitações de Super-Heróis

Este repositório contém o código e os recursos do projeto "My Hero Academia - Solicitações de Super-Heróis".

Um aplicativo de super-heróis para receber solicitações de serviços de heróis.

## Recursos

- Integração com fila SQS para processamento de solicitações
- Limitação de taxa de consumo com exemplos de consumo local e distribuído
- Configurações flexíveis via profiles Spring

## Tecnologias Utilizadas

- Spring Boot 3
- Java 2.1
- AWS SQS

## Profiles

- **spring.profiles.active:** Define o profile ativo para configurar o comportamento do consumidor SQS.

ex: `-Dspring.profiles.active=local-control` ou `-Dspring.profiles.active=distributed-semaphores`

| Profile                | Description                                                                                                                         |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| local-control          | Define as configurações para usar um controle local do consumo no SQS. Não recomendado para arquiteturas distribuídas.              |
| distributed-semaphores | Define as configurações para usar semáforos distribuídos no controle do consumo no SQS. Recomendado para arquiteturas distribuídas. |


## Configurações do Profile local-control

<details>
<summary>Veja as configurações do profile <b>local-control</b></summary>

## Objetivo
Limitar o processamento de mensagens SQS a **3 mensagens por vez** por consumidor, garantindo um controle mais rígido sobre o fluxo de mensagens a nível container.

## Configurações Implementadas

### 1. OrderConsumer.kt
```kotlin
@SqsListener(
    value = ["event-hero-orders-queue"],
    maxConcurrentMessages = "3",
    maxMessagesPerPoll = "3"
)
```

**Parâmetros:**
- `maxConcurrentMessages = "3"`: Número máximo de mensagens processadas simultaneamente
- `maxMessagesPerPoll = "3"`: Número máximo de mensagens buscadas por polling

### 3. application.yml
```yaml
spring:
  cloud:
    aws:
      sqs:
        listener:
          max-concurrent-messages: 3
          max-messages-per-poll: 3
          poll-timeout: 10
```

Configuração de propriedades globais para o comportamento do listener SQS.

</details>

## Configurações do Profile distributed-semaphores

<details>
<summary>Veja as configurações do profile <b>distributed-semaphores</b></summary>

## Objetivo

Limitar o processamento de mensagens SQS a **10 mensagens por vez** independente do número de containers, utilizando semáforos distribuídos para coordenar o consumo entre múltiplas instâncias.

## Configurações Implementadas

Uso do Redis para gerenciar o semafóro distribuído.


