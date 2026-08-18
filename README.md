# Weblogs — Backend API

A production-ready blogging platform backend built with **Spring Boot 4.1**, **PostgreSQL 16**, and **Redis 7**. It provides a full REST API for authentication, post management, comments, media uploads, and real-time analytics.

---

## Features

| Area | Details |
|---|---|
| **Auth** | JWT access tokens + HttpOnly refresh-token cookies, Google & GitHub OAuth2, email verification, password reset |
| **Posts** | Full CRUD with Markdown content, slug-based URLs, full-text search (PostgreSQL `tsvector`), category & tag filtering, trending/featured lists, RSS feed, XML sitemap |
| **Comments** | Threaded comment system with nested replies |
| **Media** | Cloudinary image uploads with rate limiting |
| **Caching** | Redis-backed response caching for public posts, trending, and featured lists with automatic TTL management |
| **Analytics** | Redis-buffered view counting with scheduled batch-flush to PostgreSQL |
| **Admin** | Protected admin endpoints for user/post management and platform statistics |
| **Security** | Spring Security, CORS, request-rate limiting, RBAC (`USER` / `ADMIN` roles) |
| **Migrations** | Flyway for schema versioning |

---

## Tech Stack

- **Runtime**: Java 17
- **Framework**: Spring Boot 4.1
- **Database**: PostgreSQL 16
- **Cache / View counts**: Redis 7
- **Auth**: JJWT 0.12, Spring Security, OAuth2 Client
- **Migrations**: Flyway
- **Build**: Maven (wrapper included — no local Maven install needed)
- **Media**: Cloudinary

---

## Project Structure

```
src/
├── main/java/com/weblogs/blog/
│   ├── auth/          # JWT, OAuth2, registration, password reset
│   ├── post/          # Post CRUD, search, publishing workflow
│   ├── comment/       # Threaded comments
│   ├── category/      # Category management
│   ├── tag/           # Tag management (auto-create on post save)
│   ├── like/          # Post likes
│   ├── user/          # User profiles
│   ├── media/         # Cloudinary upload
│   ├── admin/         # Admin panel endpoints
│   ├── cache/         # Redis cache service
│   ├── seo/           # RSS + sitemap
│   └── common/        # Shared DTOs, error handling
└── main/resources/
    ├── application.yml        # Base config
    ├── application-local.yml  # Local dev overrides
    └── db/migration/          # Flyway SQL migrations
```

---

## Running Locally

### Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 17+ |
| Docker & Docker Compose | Latest |
| Maven | Not required — Maven Wrapper (`mvnw`) is included |

---

### Option A — Docker (recommended, full stack)

This starts PostgreSQL, Redis, the Spring Boot API, **and** the Next.js frontend in one command.

> **Before you start**, make sure the frontend repo is checked out at `../weblog` relative to this repo (or update `context:` in `docker-compose.yml`).

**1. Copy and fill in the environment file**

```bash
cp .env.example .env
```

Open `.env` and set the required values:

| Variable | Description |
|---|---|
| `JWT_SECRET` | Generate with `openssl rand -base64 64` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | From [Google Cloud Console](https://console.cloud.google.com) |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | From [GitHub Developer Settings](https://github.com/settings/developers) |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials (use [Mailtrap](https://mailtrap.io) for dev) |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | From [Cloudinary dashboard](https://cloudinary.com/console) |
| `FRONTEND_URL` | `http://localhost:3000` for local dev |

> `DB_URL`, `REDIS_HOST`, and `REDIS_PORT` are **automatically overridden** by `docker-compose.yml` to point to the Docker network services — you don't need to change them.

**2. Build and start everything**

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Frontend | http://localhost:3000 |
| PostgreSQL | `localhost:5432` (db: `blog`, user: `postgres`) |
| Redis | `localhost:6379` |

**3. Stop**

```bash
docker compose down          # keep volumes
docker compose down -v       # also wipe database and Redis data
```

**Run only the infrastructure (if you want to run the API manually)**

```bash
docker compose up postgres redis
```

---

### Option B — Manual (run on the host)

**1. Start PostgreSQL and Redis via Docker**

```bash
docker compose up postgres redis
```

**2. Copy and fill in the environment file**

```bash
cp .env.example .env
# Edit .env with your values (see table above)
```

**3. Start the Spring Boot application**

```bash
./run-local.sh
```

This script loads `.env` into the shell and starts the app with the `local` Spring profile. The first run will apply all Flyway migrations automatically.

**To also run tests:**

```bash
./run-local.sh test
```

The API will be available at **http://localhost:8080**.

---

## Environment Variables Reference

See [`.env.example`](.env.example) for all variables and their descriptions.

---

## API Overview

Base path: `/api/v1`

| Resource | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/verify-email`, `/auth/forgot-password`, `/auth/reset-password` |
| OAuth | `GET /oauth2/authorize/{provider}`, `GET /oauth/callback` |
| Posts | `GET /posts`, `GET /posts/{slug}`, `POST /posts`, `PUT /posts/{id}`, `DELETE /posts/{id}`, `PATCH /posts/{id}/publish` |
| Comments | `GET /posts/{id}/comments`, `POST /posts/{id}/comments` |
| Likes | `POST /posts/{id}/like`, `DELETE /posts/{id}/like` |
| Tags | `GET /tags` |
| Categories | `GET /categories` |
| Media | `POST /media/upload` |
| Admin | `GET /admin/users`, `GET /admin/posts`, `GET /admin/stats` |
| SEO | `GET /sitemap.xml`, `GET /rss.xml` |

---

## Development Notes

- **Flyway** runs migrations automatically on startup. Migration files live in `src/main/resources/db/migration/`.
- The `local` Spring profile (`application-local.yml`) disables `secure` on cookies so they work over plain HTTP.
- Redis is optional for development — the cache service degrades gracefully if Redis is unavailable.
- OAuth2 callback URL must be registered in Google/GitHub as `http://localhost:8080/login/oauth2/code/{provider}`.
