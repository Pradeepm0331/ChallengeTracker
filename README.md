# 100-Day Challenge Tracker
React (Vite) + Spring Boot 3 (Java 17) + JPA. H2 file DB by default, PostgreSQL via `application.properties`.

## Run
    cd backend  && mvn spring-boot:run        # http://localhost:8080
    cd frontend && npm install && npm run dev # http://localhost:5173 (proxies /api)

## Data model
- `users` (username, bcrypt hash, session token)
- `challenge` (user, name, startDate) — creating one auto-generates 100 `progress_history` rows
- `daily_task` (challenge, dayNumber, title, completed, createdAt, completedAt)
- `progress_history` (challenge, dayNumber, date, planned, completed, updatedAt) — permanent planned-vs-completed record per day, recalculated on every task change

## API (Bearer token)
POST /api/auth/register|login · GET/POST /api/challenges · GET /api/challenges/{id}/days · GET /api/challenges/{id}/days/{n}
POST /api/challenges/{id}/days/{n}/tasks · PATCH /api/tasks/{taskId} · GET /api/challenges/{id}/analytics
