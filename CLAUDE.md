# AWD-Training — CLAUDE.md

## Project Overview
**SkillUp / Job Aboard** — a full-stack microservices training platform built at ESPRIT School of Engineering (30-hour academic project). It provides job listings, course/formation management, quizzes, forums, certificates, and events.

## Architecture
- **Backend:** Java 17, Spring Boot (3.2.5 – 4.0.2), Spring Cloud microservices
- **Frontend:** Angular 18 (TypeScript 5.5), served on port 4200
- **Service Discovery:** Spring Cloud Netflix Eureka (port 8761)
- **API Gateway:** Spring Cloud Gateway (port 9090) — all frontend requests go through here
- **Database:** MySQL, single shared DB `pidb`

## Microservices & Ports

| Service     | Port | Location |
|-------------|------|----------|
| Eureka      | 8761 | `backEnd/eureka/` |
| Gateway     | 9090 | `backEnd/gateway/` |
| User        | 8024 | `backEnd/microservices/user/` |
| Forum       | 8023 | `backEnd/microservices/forum/` |
| Quiz        | 8025 | `backEnd/microservices/Quiz/` |
| Course      | 8086 | `backEnd/microservices/Course/` |
| Formation   | 8084 | `backEnd/microservices/Formation/` |
| Certificate | 8022 | `backEnd/microservices/certificat/` |
| Event       | 8082 | `backEnd/microservices/eventGestion/` |
| Job Offer   | 8083 | `backEnd/microservices/job-offer/` |

## Build & Run Commands

### Backend (from `backEnd/`)
```bash
./mvnw clean install        # Build all microservices
./mvnw spring-boot:run      # Run a specific microservice (cd into its directory first)
```

### Frontend (from `skillup_front/`)
```bash
npm start        # Dev server → http://localhost:4200
npm run build    # Production build → dist/skillup-front/
npm test         # Unit tests via Karma + Jasmine
```

## Recommended Startup Order
1. MySQL (`pidb` database — created automatically on first run)
2. Eureka Server (`backEnd/eureka/`)
3. API Gateway (`backEnd/gateway/`)
4. Individual microservices (any order)
5. Angular frontend (`skillup_front/`)

## Database Configuration (all microservices)
```
URL:      jdbc:mysql://localhost:3306/pidb?createDatabaseIfNotExist=true
Username: root
Password: (empty)
DDL auto: update
```

## Security
- **JWT** authentication (io.jsonwebtoken jjwt 0.11.5)
- **Roles:** `ADMIN`, `TRAINEE`
- Token stored in browser `localStorage`
- CORS configured on the gateway for `http://localhost:4200`

## Key Frontend Files
- Services: `skillup_front/src/app/auth.service.ts`, `quiz.service.ts`, `forum.service.ts`
- Routing: `skillup_front/src/app/app-routing.module.ts`
- Main module: `skillup_front/src/app/app.module.ts`

## API Routes (via Gateway at :9090)
| Path prefix       | Target service |
|-------------------|----------------|
| `/users/**`       | User (8024)    |
| `/forum/**`       | Forum (8023)   |
| `/quiz/**`        | Quiz (8025)    |
| `/courses/**`     | Course (8086)  |
| `/formations/**`  | Formation (8084)|
| `/certificats/**` | Certificate (8022)|
| `/eventGestions/**`| Event (8082)  |
| `/job-offers/**`  | Job Offer (8083)|

## Notes
- All services share the same `pidb` MySQL database (training simplification)
- Config Server is disabled in all services
- Kafka/RabbitMQ and Keycloak are mentioned in docs but not implemented
- User table is named `app_user` to avoid MySQL reserved word conflict
