# Local Development

## Requirements

- JDK 1.8
- Maven 3.9+
- MySQL 5.7+ or 8.x
- Node.js 18+

## Backend

Start the backend from the repository root:

```powershell
mvn spring-boot:run
```

The backend reads infrastructure settings such as database connection values from `application.yml`, environment variables, or a local ignored config file.

Project-specific credentials are configured in the web console:

- GitLab project URL and token are required when creating a project.
- AI review requires selecting a model from the model configuration page.
- WeCom notification requires a project-level webhook URL when the notification switch is enabled.

## Frontend

```powershell
cd web-ui
npm install
npm run dev
```

## Configuration Notes

Do not commit real credentials. Keep local secrets in ignored files such as:

- `src/main/resources/application-local.yml`
- environment variables
- deployment platform secrets
- CI/CD secret stores

The repository only keeps safe example configuration.
