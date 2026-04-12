# Use Case: Register Account

## Overview

**Use Case ID:** UC-034
**Use Case Name:** Register Account
**Primary Actor:** User
**Goal:** Create a new account with email and password credentials to access the application.
**Status:** Implemented

## Traceability

**Maps to:** FR-auth-register

---

## Preconditions

- The user does not already have an account in the system.

## Main Success Scenario

1. User navigates to the registration page.
2. User enters their first name, last name, email, and password.
3. User confirms the password by entering it again.
4. System validates that both password fields match.
5. User submits the registration form.
6. System validates the input (all fields present, password minimum 8 characters, email format).
7. System checks that no user with the given email already exists.
8. System hashes the password using bcrypt.
9. System creates a new `User` record with `AuthProvider.FORM`, the hashed password, and the `USER` role.
10. System displays a success notification.
11. System redirects the user to the login page.

## Alternative Flows

### A1: Email Already Registered

**Trigger:** A user with the given email already exists (step 7).
**Flow:**

1. System returns HTTP 409 (Conflict).
2. Frontend displays an "email already registered" error message.

### A2: Passwords Do Not Match

**Trigger:** The password and confirm password fields differ (step 4).
**Flow:**

1. Frontend displays a validation error on the confirm password field.
2. The submit button is disabled until the fields match.

### A3: Validation Failure

**Trigger:** Required fields are missing or password is too short (step 6).
**Flow:**

1. System returns a validation error.
2. Frontend displays appropriate error messages.

### A4: Concurrent Registration

**Trigger:** Two requests attempt to register the same email simultaneously (step 7).
**Flow:**

1. One request succeeds, the other catches a `ConstraintViolationException`.
2. The failing request maps the exception to the same "email already registered" error.
3. Use case continues with A1.

### A5: Rate Limiting

**Trigger:** Too many registration attempts from the same IP.
**Flow:**

1. System rate-limits the endpoint (5 requests per minute).
2. System returns HTTP 429 (Too Many Requests).

## Postconditions

### Success Postconditions

- A new `User` record exists with `AuthProvider.FORM`.
- The user's password is stored as a bcrypt hash.
- The user has the `USER` role.
- The user is redirected to the login page.

### Failure Postconditions

- No user record is created.
- The user remains on the registration page.
- An appropriate error message is displayed.

## Business Rules

### BR-001: Password Minimum Length

The password must be at least 8 characters and at most 128 characters.

### BR-002: Password Hashing

Passwords must be hashed using bcrypt before storage.

### BR-003: Unique Email

Each email address can only be registered once. The email serves as the unique identifier and username for form-based authentication.

### BR-004: Default Role

Newly registered users receive the `USER` role.
