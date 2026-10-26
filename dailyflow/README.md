# DailyFlow — To-Do & Daily Productivity Manager

Full-stack web app for managing daily tasks, work, exam preparation and goals. All UI text is in English and the interface has **light and dark themes** (toggle in the top bar; your choice is remembered, default follows your OS setting).

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5.6, Spring Web, Spring Data JPA/Hibernate, Spring Security, Bean Validation, JWT (jjwt), springdoc OpenAPI |
| Database | MySQL 8+, Flyway migrations |
| Frontend | Angular 21 (standalone components, signals, Router, Reactive Forms, HttpClient, guards, interceptor), SCSS design system, AJAX via HttpClient |
| Tests | JUnit 5, Mockito, Spring Boot Test + MockMvc (H2 in MySQL mode) |
| Deploy | Docker Compose (MySQL + backend + nginx-served Angular) |

## Project structure
```
dailyflow/
├── backend/                  Spring Boot (Maven)
│   └── src/main/java/com/dailyflow/
│       ├── config/           Security, CORS, OpenAPI, clock, dev-only seeder
│       ├── domain/           JPA entities + enums
│       ├── dto/  mapper/     API contracts (entities are never exposed)
│       ├── repository/       Spring Data repositories, Specifications
│       ├── service/          Business logic, recurrence, dashboard, reports, audit, reminders
│       ├── security/         JWT service + filter
│       ├── web/              Thin REST controllers + GlobalExceptionHandler
│       └── resources/db/migration/V1__init_schema.sql
├── frontend/                 Angular 21 app (+ Dockerfile, nginx.conf)
├── docker-compose.yml
├── .env.example
└── README.md
```

## Run locally (development)

**Prerequisites:** JDK 21, Maven 3.9+, Node 22+, MySQL 8+.

1. **Create the database** (once):
   ```sql
   CREATE DATABASE dailyflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'dailyflow'@'localhost' IDENTIFIED BY 'choose-a-password';
   GRANT ALL PRIVILEGES ON dailyflow.* TO 'dailyflow'@'localhost';
   ```
   Flyway creates all tables automatically on first start.
2. **Start the backend** (new terminal):
   ```bash
   cd backend
   export DB_PASSWORD='choose-a-password'
   export JWT_SECRET="$(openssl rand -base64 48)"
   export SPRING_PROFILES_ACTIVE=dev      # optional: creates demo user demo@dailyflow.local / Demo12345
   mvn spring-boot:run
   ```
   API: http://localhost:8080 · Swagger UI: http://localhost:8080/swagger-ui.html · Health: `/actuator/health`
3. **Start the frontend** (another terminal):
   ```bash
   cd frontend
   npm install
   npm start
   ```
   Open http://localhost:4200 and register an account (every new account gets the 9 predefined categories).

Windows PowerShell: use `$env:DB_PASSWORD='...'` instead of `export`.

## Run with Docker Compose
```bash
cp .env.example .env        # then set MYSQL_ROOT_PASSWORD, DB_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up --build
```
Open http://localhost:8081 (nginx serves the Angular app and proxies `/api` to the backend, so no CORS setup is needed).
Data lives in the `dailyflow-db` Docker volume.

## Configuration (environment variables)
| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection (password has no default) | localhost / `dailyflow` |
| `JWT_SECRET` | **Required**, ≥ 32 chars | – |
| `JWT_EXPIRATION_MINUTES` | Access-token lifetime | 60 |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origin(s) | `http://localhost:4200` |
| `APP_TIMEZONE` | Zone that defines "today" (e.g. `Asia/Kolkata`) | `UTC` |
| `SPRINGDOC_ENABLED` | Swagger UI / OpenAPI docs | `true` (set `false` in production) |
| `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`, `MAIL_FROM` | Optional email reminders | disabled |

## Features
- **Auth:** register, login, logout, BCrypt hashes, stateless JWT, per-user data isolation (other users' ids return 404), CORS limited to the configured origin.
- **Tasks:** create/read/update/delete, details, complete/reopen, move between TODO / IN_PROGRESS / COMPLETED, validation (required title, due ≥ start), delete confirmation, unsaved-changes confirmation, double-submit protection, instant UI refresh.
- **Dashboard:** total, due today, completed, to do, in progress, overdue, completion %, upcoming deadlines, 7-day chart, recently created/updated, recent activity, quick add. All numbers are computed from the database.
- **Categories:** 9 predefined per user + create / rename / recolor / delete your own (a category that still has tasks cannot be deleted).
- **Planning:** Today, Tomorrow, This week, month calendar, overdue and completed pages, filters by category/priority/status.
- **Search & lists:** title/description search, category/priority/status/due-range filters, sorting, server-side pagination, clear filters.
- **Recurring tasks:** daily, weekly, custom N-day interval, optional end date; skip, reschedule or reset a single occurrence.
- **Reminders:** in-app toasts for every change, overdue badges, a bell with overdue and due-soon tasks (refreshes every minute), optional browser notifications, optional email reminders when SMTP is configured.
- **Reports:** 7/30/90-day productivity, streak, breakdown by category and priority.
- **Audit trail:** created / updated / completed / reopened / deleted / login events with short before-after summaries. Passwords and tokens are never stored or logged. Activity rows keep a task-title snapshot, so history survives task deletion and is removed only if the account is deleted.
- **UI:** responsive sidebar + mobile menu, light/dark theme, accessible labels, skip link, keyboard-friendly dialogs, loading skeletons, empty states.

## Design notes
- **Token strategy:** one access token (default 60 min) kept in `sessionStorage`; no refresh tokens. On expiry the API returns 401 and the user signs in again. Logout clears the token client-side and records an audit event; because tokens are stateless, a token stolen before logout stays valid until it expires — keep the lifetime short.
- **Recurrence storage:** a recurring task is one row in `tasks` (its due date is the first occurrence, `recurrence_interval` is the day step). Occurrences are *computed* for the requested window, never pre-generated. Only exceptions (completed / skipped / rescheduled) are stored in `task_occurrences` with a unique key `(task_id, occurrence_date)`, so retrying a request can never create duplicates.
- **Dashboard rules:** "completed %" = completed ÷ all one-off tasks; recurring series count once; overdue = due date passed and not completed (an occurrence is overdue individually).
- **Notifications:** in-app notifications are transient toasts plus the computed reminder list; there is no separate notifications table.

## API overview (all under `/api`, JWT bearer except register/login; full docs at `/swagger-ui.html`)
| Area | Endpoints |
|---|---|
| Auth/profile | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `PUT /users/me`, `PUT /users/me/password` |
| Tasks | `GET /tasks` (q, categoryId, priority, status, dueFrom, dueTo, overdue, page, size, sort, direction), `POST /tasks`, `GET/PUT/DELETE /tasks/{id}`, `PATCH /tasks/{id}/status`, `POST /tasks/{id}/complete`, `POST /tasks/{id}/reopen` |
| Occurrences | `POST /tasks/{id}/occurrences/{date}/complete`, `.../skip`, `.../reschedule`, `DELETE /tasks/{id}/occurrences/{date}` |
| Categories | `GET/POST /categories`, `PUT/DELETE /categories/{id}` |
| Insights | `GET /dashboard`, `GET /calendar?from&to` (max 100 days), `GET /reports/productivity?days=`, `GET /reminders`, `GET /activity` |

Errors share one shape: `{ status, error, message, fieldErrors? }`.

## Tests
```bash
cd backend && mvn test
```
Covers task CRUD and validation, ownership isolation (user B cannot read/modify user A's data), authentication, complete/reopen, search and filtering, recurrence rules, and the global exception handler.

## Troubleshooting
| Symptom | Fix |
|---|---|
| Backend fails with `Could not resolve placeholder 'JWT_SECRET'` / `DB_PASSWORD` | Export both variables before starting. |
| `Access denied for user` / `Communications link failure` | Check the MySQL user/password, that MySQL is running, and `DB_URL`. |
| Browser shows CORS errors | Set `CORS_ALLOWED_ORIGINS` to the exact origin of the frontend (scheme + host + port). |
| Everything logs me out after an hour | That is the token lifetime; raise `JWT_EXPIRATION_MINUTES`. |
| "Today" is off by hours | Set `APP_TIMEZONE` (e.g. `Asia/Kolkata`). |
| Port 8080 / 4200 busy | `PORT=9090 mvn spring-boot:run`; `npm start -- --port 4300` (and update CORS). |
| Docker: `Set JWT_SECRET in .env` | Fill the required values in `.env`. |

## Deployment checklist
Use HTTPS (terminate TLS at a reverse proxy/load balancer), a strong unique `JWT_SECRET`, enable TLS on the MySQL connection in `DB_URL`, `SPRINGDOC_ENABLED=false`, do not use the `dev` profile, back up the MySQL volume.

## Known limitations
- Reminders are only delivered while the page is open (in-app/browser) or while the backend runs with SMTP configured (email). Nothing is delivered when the app/server is not running.
- No password-reset by email, no refresh tokens, no server-side token revocation (see token strategy).
- The month calendar and Today views read a bounded date window (≤ 100 days) from the server.
