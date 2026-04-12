# AGENTS.md

## Setup commands
- Install dependencies: `mvn clean install`
- Preferred wrapper commands: `./mvnw.cmd clean install` (Windows) or `./mvnw clean install` (Linux/macOS)
- Start local dependencies before running consumers: from `local-environment/`, run `docker-compose up -d --build` (starts LocalStack SQS and Redis with password)
- Local SQS queues are created by `local-environment/localstack/init/ready.d/create-sqs-queues.sh` (`event-hero-orders-queue` and `event-hero-orders-queue-dlq`)
- Run with a Spring profile: `./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local-control` (also available: `distributed-semaphores`, `distributed-token-bucket`, `distributed-token-bucket4j`)
- Configure proxy variables if necessary: `export http_proxy=http://proxy.example.com:8080` and `export https_proxy=http://proxy.example.com:8080`

- Configure the development environment: `export JAVA_HOME=/path/to/java` and `export PATH=$JAVA_HOME/bin:$PATH`

## Code style
- Use camelCase for variable and method names, and PascalCase for class names.
- This codebase is Kotlin-first (`src/main/kotlin` and `src/test/kotlin`); follow Kotlin + Spring Boot conventions for formatting and null-safety.
- Use meaningful and descriptive names for variables, methods, and classes to enhance readability and maintainability
- Keep SQS consumer behavior aligned with profile-specific classes in `src/main/kotlin/com/myheroacademia/heroorders/consumer` (`OrderConsumerLocalControl`, `OrderConsumerDistributedSemaphores`, `OrderConsumerDistributedTokenBucket`, `OrderConsumerDistributedTokenBucket4j`).

## Testing instructions
1. Always follow the Given, When, Then structure to ensure clarity and organization in tests.
2. Ensure that tests are independent, avoiding dependencies between them to facilitate isolated execution.
3. Keep tests readable and easy to understand, using descriptive names for tests and variables, facilitating maintenance and identification of code flaws.
4. Run tests with Maven wrapper: `./mvnw.cmd test` (Windows) or `./mvnw test` (Linux/macOS).
5. Follow existing test patterns in `src/test/kotlin/com/myheroacademia/heroorders/service/RedisDistributedTokenBucketTest.kt` (JUnit 5 + Mockito Kotlin, Given/When/Then naming).

## PR instructions
- Title format: [<project_name>] <Title>
- Description: Provide a clear and concise description of the changes made, including the purpose and any relevant details.