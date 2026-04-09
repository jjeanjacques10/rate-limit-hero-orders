# AGENTS.md

## Setup commands
- Install dependencies: `mvn clean install`
- Configure proxy variables if necessary: `export http_proxy=http://proxy.example.com:8080` and `export https_proxy=http://proxy.example.com:8080`

- Configure the development environment: `export JAVA_HOME=/path/to/java` and `export PATH=$JAVA_HOME/bin:$PATH`

## Code style
- Use camelCase for variable and method names, and PascalCase for class names.
- Follow the standard Java code conventions for formatting, including indentation, spacing, and line breaks.
- Use meaningful and descriptive names for variables, methods, and classes to enhance readability and maintainability

## Testing instructions
1. Always follow the Given, When, Then structure to ensure clarity and organization in tests.
2. Ensure that tests are independent, avoiding dependencies between them to facilitate isolated execution.
3. Keep tests readable and easy to understand, using descriptive names for tests and variables, facilitating maintenance and identification of code flaws.

## PR instructions
- Title format: [<project_name>] <Title>
- Description: Provide a clear and concise description of the changes made, including the purpose and any relevant details.