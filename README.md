# Code Review Application

A Spring Boot application that provides automated code reviews using AI models and integrates with Confluence for knowledge management.

## Features

- **Automated Code Reviews**: Analyze GitHub repositories and provide detailed code reviews
- **Confluence Integration**: Connect to Confluence to retrieve coding guidelines and standards
- **Embedding-based Similarity Search**: Find relevant code guidelines for specific code snippets
- **Project or File Review**: Support for reviewing individual files or entire projects
- **Interactive Web Interface**: User-friendly interface for submitting and viewing code reviews

## Technologies

- Java 21
- Spring Boot 3.x
- Spring AI
- Flyway for database migrations
- pgvector for vector similarity search
- Docker support with docker-compose

## Getting Started

### Prerequisites

- Java 21 or later
- Maven
- PostgreSQL with pgvector extension
- Confluence instance (for guidelines integration)
- GitHub account (for repository access)

### Running the Application

1. Clone the repository
   ```bash
   git clone https://github.com/yourusername/code-review.git
   cd code-review
   ```

2. Configure application properties in `src/main/resources/application.properties` or environment variables

3. Set up the database
   ```bash
   docker-compose up -d
   ```

4. Build and run the application
   ```bash
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```

5. Access the application at `http://localhost:8080`

## Configuration

Configure the following properties in `application.properties` or as environment variables:

```properties
# GitHub Configuration
github.token=your_github_token

# Confluence Configuration
confluence.base-url=https://your-confluence-instance.atlassian.net
confluence.username=your_username
confluence.api-token=your_api_token

# AI Model Configuration
openai.api-key=your_openai_api_key
```

## Architecture

The application follows a layered architecture:

- **Controllers**: Handle HTTP requests and responses
- **Services**: Implement business logic
- **Repositories**: Interface with the database
- **Models**: Represent domain objects
- **Config**: Configuration classes for external services

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
