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

ex: `-Dspring.profiles.active=local-control` ou `-Dspring.profiles.active=distributed-semaphores` ou `-Dspring.profiles.active=distributed-token-bucket`

| Profile                    | Description                                                                                                                         |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| local-control              | Define as configurações para usar um controle local do consumo no SQS. Não recomendado para arquiteturas distribuídas.              |
| distributed-semaphores     | Define as configurações para usar semáforos distribuídos no controle do consumo no SQS. Recomendado para arquiteturas distribuídas. |
| distributed-token-bucket   | Define as configurações para usar token bucket distribuído no controle de taxa de consumo no SQS. Recomendado para rate limiting.   |


## Configurações do Profile local-control

<details>
<summary>Veja as configurações do profile <b>local-control</b></summary>

## Objetivo
Limitar o processamento de mensagens SQS a **3 mensagens por vez** por consumidor, garantindo um controle mais rígido sobre o fluxo de mensagens a nível container.

## Configurações Implementadas

### 1. OrderConsumerLocalControl.kt
```kotlin
@SqsListener(
    value = ["event-hero-orders-queue"],
    maxConcurrentMessages = "3",
    maxMessagesPerPoll = "3"
)
fun consumeMessage(message: Message<HeroOrderRequest>, acknowledgement: Acknowledgement) {
    try {
        orderService.processOrder(message.payload)
        acknowledgement.acknowledge() // ACK manual apenas após sucesso
    } catch (e: Exception) {
        // Mensagem volta para a fila automaticamente
    }
}
```

**Parâmetros:**
- `maxConcurrentMessages = "3"`: Número máximo de mensagens processadas simultaneamente
- `maxMessagesPerPoll = "3"`: Número máximo de mensagens buscadas por polling
- **Acknowledgement Manual**: Apenas mensagens processadas com sucesso são removidas da fila

### 2. application-local-control.yml

```yaml
spring:
  cloud:
    aws:
      sqs:
        listener:
          max-concurrent-messages: 3
          max-messages-per-poll: 3
          poll-timeout: 10
          acknowledgement-mode: manual
```

**Configuração de acknowledgement manual** para controle preciso sobre quando as mensagens são removidas da fila SQS.

### Vantagens

✅ **Controle Preciso**: Mensagem só é removida da fila após processamento bem-sucedido  
✅ **Retry Automático**: Falhas fazem a mensagem voltar para a fila automaticamente  
✅ **Sem Perda de Dados**: Garante que nenhuma mensagem seja perdida em caso de erro  

</details>

## Configurações do Profile distributed-semaphores

<details>
<summary>Veja as configurações do profile <b>distributed-semaphores</b></summary>

## Objetivo

Limitar o processamento de mensagens SQS a **10 mensagens por vez** independente do número de containers, utilizando semáforos distribuídos para coordenar o consumo entre múltiplas instâncias.

## Configurações Implementadas

### 1. RedisDistributedSemaphore.kt
Serviço que gerencia o semáforo distribuído usando Redis:

**Características:**
- **MAX_PERMITS = 10**: Máximo de 10 mensagens processadas simultaneamente em todo o cluster
- **Sorted Set do Redis**: Armazena os permits ativos com timestamp
- **TTL de 60 segundos**: Cleanup automático de permits órfãos
- **Race Condition Protection**: Verifica se o limite não foi excedido após adquirir

### 2. OrderConsumerDistributedSemaphores.kt
Consumer que utiliza o semáforo distribuído para controlar o processamento:

```kotlin
@SqsListener(value = ["event-hero-orders-queue"])
fun consumeMessage(message: Message<HeroOrderRequest>, acknowledgement: Acknowledgement) {
    var permitId: String? = null
    
    try {
        permitId = distributedSemaphore.tryAcquire()
        
        if (permitId == null) {
            // Sem permit disponível - mensagem volta para a fila
            return
        }
        
        orderService.processOrder(message.payload)
        acknowledgement.acknowledge() // ACK manual apenas após sucesso
        
    } catch (e: Exception) {
        // Erro no processamento - mensagem volta para a fila
    } finally {
        permitId?.let { distributedSemaphore.release(it) }
    }
}
```

**Comportamento:**
- Tenta adquirir um permit antes de processar
- Se não conseguir permit, **NÃO faz ACK** → mensagem volta para a fila
- Se conseguir processar com sucesso, **faz ACK manual** → mensagem é removida da fila
- Em caso de erro no processamento, **NÃO faz ACK** → mensagem volta para a fila
- Sempre libera o permit no bloco finally

### 3. application-distributed-semaphores.yml
```yaml
spring:
  cloud:
    aws:
      sqs:
        listener:
          max-concurrent-messages: 20
          max-messages-per-poll: 10
          poll-timeout: 10
          acknowledgement-mode: manual
```

**Parâmetros:**
- `max-concurrent-messages = 20`: Permite até 20 threads de processamento por container
- `max-messages-per-poll = 10`: Busca até 10 mensagens por polling
- `acknowledgement-mode = manual`: Controle manual de quando fazer ACK das mensagens
- O semáforo do Redis garante que apenas 10 mensagens sejam processadas em todo o cluster

### 4. Redis Configuration
Redis configurado com autenticação via password no docker-compose.yml e application.yml

### Como Funciona

1. **Container A** busca 10 mensagens da fila SQS
2. Tenta processar todas, mas consegue apenas 6 permits do semáforo
3. 4 mensagens falham e voltam para a fila (retry)
4. **Container B** busca 10 mensagens
5. Consegue apenas 4 permits (completando os 10 do cluster)
6. 6 mensagens falham e voltam para a fila
7. Quando Container A ou B terminam o processamento, liberam permits
8. Novas mensagens podem então ser processadas

### Vantagens

✅ **Controle Global**: Limite de 10 mensagens independente do número de containers  
✅ **Escalabilidade**: Adicione quantos containers quiser sem perder o controle  
✅ **Fault Tolerance**: TTL automático limpa permits de containers que falharam  
✅ **Retry Automático**: Mensagens sem permit ou com erro voltam para a fila automaticamente  
✅ **Sem Perda de Dados**: ACK manual garante que apenas mensagens processadas com sucesso sejam removidas  

</details>

## Configurações do Profile distributed-token-bucket

<details>
<summary>Veja as configurações do profile <b>distributed-token-bucket</b></summary>

## Objetivo

Implementar **rate limiting** usando o algoritmo **Token Bucket** distribuído com Redis, permitindo controlar a taxa de processamento de mensagens com suporte a **burst traffic** enquanto mantém uma taxa média estável.

## Configurações Implementadas

### 1. RedisDistributedTokenBucket.kt
Serviço que implementa o algoritmo Token Bucket distribuído usando Redis:

**Características:**
- **MAX_TOKENS = 10**: Capacidade máxima do bucket (permite burst de até 10 mensagens)
- **REFILL_RATE = 2**: Taxa de reabastecimento (2 tokens por segundo)
- **Auto Refill**: Tokens são automaticamente adicionados baseado no tempo decorrido
- **Atomic Operations**: Usa operações atômicas do Redis para evitar race conditions
- **TTL de 300 segundos**: Cleanup automático das chaves do Redis

**Funcionamento:**

```kotlin
fun tryConsume(tokensNeeded: Int = 1): Boolean {
    refillTokens() // Reabastece tokens baseado no tempo
    
    val currentTokens = getCurrentTokens()
    if (currentTokens >= tokensNeeded) {
        // Consome o token atomicamente
        redisTemplate.opsForValue().decrement(TOKEN_BUCKET_KEY, tokensNeeded.toLong())
        return true
    }
    return false
}
```

### 2. OrderConsumerDistributedTokenBucket.kt

Consumer que utiliza o token bucket para controlar o rate limiting:

```kotlin
@SqsListener(
    value = ["event-hero-orders-queue"],
    maxConcurrentMessages = "3",
    maxMessagesPerPoll = "3",
    acknowledgementMode = "MANUAL"
)
fun consumeMessage(message: Message<HeroOrderRequest>, acknowledgement: Acknowledgement) {
    try {
        if (!tokenBucket.tryConsume()) {
            // Sem token disponível - mensagem volta para a fila
            log.warn("No token available - message will return to queue")
            return
        }
        
        orderService.processOrder(message.payload)
        acknowledgement.acknowledge() // ACK manual apenas após sucesso
        
    } catch (e: Exception) {
        // Erro no processamento - mensagem volta para a fila
        log.error("Processing error - message will return to queue", e)
    }
}
```

**Comportamento:**
- Tenta consumir um token antes de processar
- Se não houver token disponível, **NÃO faz ACK** → mensagem volta para a fila
- Se conseguir processar com sucesso, **faz ACK manual** → mensagem é removida da fila
- Em caso de erro no processamento, **NÃO faz ACK** → mensagem volta para a fila
- Tokens são reabastecidos automaticamente a cada segundo (2 tokens/s)

### 3. application-distributed-token-bucket.yml

```yaml
spring:
  cloud:
    aws:
      sqs:
        listener:
          max-concurrent-messages: 5
          max-messages-per-poll: 5
          poll-timeout: 10
```

**Parâmetros:**
- `max-concurrent-messages = 5`: Permite até 5 threads de processamento por container
- `max-messages-per-poll = 5`: Busca até 5 mensagens por polling
- `acknowledgement-mode = manual`: Controle manual de quando fazer ACK das mensagens
- Token bucket garante taxa máxima de 2 mensagens/segundo com burst de até 10

### Como Funciona - Exemplo de Cenário

**Cenário 1: Tráfego em Burst**
1. Sistema está ocioso - bucket tem 10 tokens cheios
2. Chegam 10 mensagens de uma vez
3. Todas as 10 são processadas imediatamente (burst permitido)
4. Bucket fica vazio (0 tokens)
5. Próximas mensagens precisam esperar o refill

**Cenário 2: Taxa Sustentada**
1. Bucket está vazio (0 tokens)
2. A cada 0.5 segundos, 1 novo token é adicionado (2 tokens/segundo)
3. Mensagens são processadas a uma taxa máxima de 2/segundo
4. Sistema mantém throughput estável e controlado

**Cenário 3: Múltiplos Containers**
1. Container A e B compartilham o mesmo bucket no Redis
2. Container A consome 5 tokens
3. Container B pode consumir no máximo os 5 tokens restantes
4. Ambos competem pelos tokens de forma distribuída
5. Taxa global é mantida independente do número de containers

### Vantagens do Token Bucket

✅ **Rate Limiting Eficiente**: Controla a taxa de processamento de forma precisa  
✅ **Suporte a Burst**: Permite picos de tráfego até o limite do bucket  
✅ **Distribuído**: Funciona corretamente com múltiplos containers  
✅ **Smooth Traffic**: Evita sobrecarga do sistema com throughput controlado  
✅ **Auto Recovery**: Tokens são reabastecidos automaticamente ao longo do tempo  
✅ **Retry Automático**: Mensagens sem token voltam para a fila automaticamente  
✅ **Sem Perda de Dados**: ACK manual garante que apenas mensagens processadas com sucesso sejam removidas

</details>

### Diferença entre Semaphore vs Token Bucket

| Aspecto              | Distributed Semaphore                     | Distributed Token Bucket                    |
|----------------------|-------------------------------------------|---------------------------------------------|
| **Objetivo**         | Limitar concorrência                      | Limitar taxa (rate limiting)                |
| **Controle**         | Número de processamentos simultâneos      | Taxa de processamento ao longo do tempo     |
| **Burst**            | Limitado ao número de permits             | Suporta burst até o tamanho do bucket       |
| **Refill**           | Não há refill automático                  | Refill automático baseado no tempo          |
| **Uso Ideal**        | Limitar carga em recursos compartilhados  | Controlar throughput e evitar sobrecarga    |