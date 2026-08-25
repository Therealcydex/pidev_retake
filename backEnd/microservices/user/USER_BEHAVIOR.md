# User Microservice — Behavior

## Overview
Authentication and user management microservice. Handles signup and login for the SkillUp platform. Runs on port **8024** and is exposed via the API Gateway under `/users/**`.

## Roles
Defined in `entity/Role.java`:
- `ADMIN`
- `TRAINER`
- `TRAINEE` (default on signup)
- `COMPANY`

## Entity — `User` (`app_user` table)
| Field    | Type   | Notes                             |
|----------|--------|-----------------------------------|
| id       | Long   | Auto-generated primary key        |
| username | String | Unique                            |
| email    | String | Unique                            |
| password | String | BCrypt-hashed, never returned     |
| role     | Role   | Stored as `ENUM STRING`           |

Table is named `app_user` to avoid MySQL reserved-word conflict.

## Endpoints

### `POST /auth/signup`
Registers a new user with role `TRAINEE`.

**Request body** (`SignupRequest`):
```json
{
  "username": "string",
  "email": "string",
  "password": "string"
}
```

**Behavior:**
1. Rejects with `409 CONFLICT — "Username already taken"` if username exists.
2. Rejects with `409 CONFLICT — "Email already registered"` if email exists.
3. Encodes password with BCrypt.
4. Persists user with role `TRAINEE`.
5. Returns `201 CREATED` with `UserResponse`.

### `POST /auth/login`
Authenticates by username + password.

**Request body** (`LoginRequest`):
```json
{
  "username": "string",
  "password": "string"
}
```

**Behavior:**
1. Looks up user by username.
2. Returns `401 UNAUTHORIZED — "Invalid credentials"` if user not found or password mismatch.
3. On success returns `200 OK` with `UserResponse`.

## Response DTO — `UserResponse`
```json
{
  "id": 1,
  "username": "string",
  "email": "string",
  "role": "TRAINEE"
}
```
Password is never included in responses.

## Security
- `SecurityConfig` disables CSRF and permits all requests (auth handled at controller/service level).
- Password hashing: `BCryptPasswordEncoder`.
- No JWT issuance in the current code — login simply returns the user profile.

## Repository Methods (`UserRepository`)
- `findByUsername(String)`
- `findByEmail(String)`
- `existsByUsername(String)`
- `existsByEmail(String)`

## Error Summary
| Case                        | Status | Message                    |
|-----------------------------|--------|----------------------------|
| Duplicate username on signup| 409    | Username already taken     |
| Duplicate email on signup   | 409    | Email already registered   |
| Unknown username on login   | 401    | Invalid credentials        |
| Wrong password on login     | 401    | Invalid credentials        |
