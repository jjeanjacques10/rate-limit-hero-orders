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
