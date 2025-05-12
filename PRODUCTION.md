# Production Deployment Guide

This document provides guidance for deploying the Code Review application in a production environment.

## Environment Variables

The application is configured to use environment variables for sensitive configuration. You should set the following variables in your production environment:

### Database Configuration
- `SPRING_DATASOURCE_URL` - JDBC URL for PostgreSQL database
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password

### API Keys and Secrets
- `OPENAI_API_KEY` - Your OpenAI API key
- `CONFLUENCE_BASE_URL` - Your Confluence instance URL
- `CONFLUENCE_USERNAME` - Confluence username (email)
- `CONFLUENCE_API_TOKEN` - Confluence API token
- `CONFLUENCE_SPACE_KEY` - Confluence space key
- `GITHUB_ACCESS_TOKEN` - GitHub access token with repo scope

### Application Settings
- `SERVER_PORT` - The port the application will run on (default: 8080)
- `SPRING_PROFILES_ACTIVE` - Set to "prod" for production settings

## Docker Deployment

For Docker-based deployments, you can set environment variables in your docker-compose.yml file:

```yaml
version: '3.8'
services:
  app:
    image: your-registry/code-review:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/code_review
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=secure_password
      - OPENAI_API_KEY=your-openai-api-key
      - CONFLUENCE_BASE_URL=https://your-instance.atlassian.net/
      - CONFLUENCE_USERNAME=your-email@example.com
      - CONFLUENCE_API_TOKEN=your-api-token
      - CONFLUENCE_SPACE_KEY=SPACE
      - GITHUB_ACCESS_TOKEN=your-github-token
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - db
    networks:
      - app-network

  db:
    image: ankane/pgvector:latest
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=secure_password
      - POSTGRES_DB=code_review
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - app-network

volumes:
  postgres-data:

networks:
  app-network:
```

## Kubernetes Deployment

For Kubernetes deployments, create a Secret for sensitive data:

```bash
kubectl create secret generic code-review-secrets \
  --from-literal=spring.datasource.password=secure_password \
  --from-literal=openai.api-key=your-openai-api-key \
  --from-literal=confluence.api-token=your-api-token \
  --from-literal=github.access-token=your-github-token
```

Then reference these secrets in your deployment YAML file.

## Security Considerations

- Always use environment variables or secrets management for storing sensitive information
- Ensure your database is properly secured with strong passwords
- Restrict network access to the application and database
- Set up proper logging and monitoring
- Configure SSL for secure HTTPS connections
