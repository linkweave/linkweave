# Use Case: Login with Google

## Overview

**Use Case ID:** UC-033
**Use Case Name:** Login with Google
**Primary Actor:** User
**Goal:** Authenticate using a Google account to access the application without creating separate credentials.
**Status:** Implemented

## Traceability

**Maps to:** FR-auth-google

---

## Preconditions

- The user has a Google account.
- The application's OIDC configuration is valid (client ID, secret, redirect URI).

## Main Success Scenario

1. User clicks the "Sign in with Google" button on the login page.
2. System redirects the user to Google's consent screen.
3. User grants permission to share their profile (email, given name, family name).
4. Google redirects back to the application callback (`/api/q/authorized`).
5. Quarkus OIDC validates the Google token and establishes an authenticated session.
6. System calls `GET /api/auth/oidc-login` which triggers `EnsureUserService.ensureUserExists()`.
7. If the user does not exist locally, the system auto-provisions a new `User` record using Google profile data (email, given name, family name, Google subject ID) with `AuthProvider.OIDC`.
8. System redirects the user to the application home page.

## Alternative Flows

### A1: User Already Exists Locally

**Trigger:** A `User` record with the Google email already exists (step 6).
**Flow:**

1. System skips auto-provisioning.
2. Use case continues with the redirect to the home page.

### A2: Google Authentication Fails

**Trigger:** Google returns an error or denies authentication.
**Flow:**

1. System redirects the user back to the login page.
2. System displays an error message.

### A3: Concurrent Auto-Provisioning

**Trigger:** Two requests attempt to create the same user simultaneously (step 7).
**Flow:**

1. One request succeeds, the other catches a `ConstraintViolationException`.
2. The failing request re-fetches the existing user record.
3. Use case continues normally.

## Postconditions

### Success Postconditions

- The user is authenticated with an active session.
- A local `User` record exists with `AuthProvider.OIDC`.
- The user is redirected to the application home page.

### Failure Postconditions

- No user record is created.
- The user remains on the login page.
- An error message is displayed.

## Business Rules

### BR-001: No Password for OIDC Users

Users created via Google login must not have a password set (`password = null`).

### BR-002: Principal Claim is Email

The OIDC token's `email` claim is used as the principal name for the local user record.

### BR-003: Profile Data from Google

The user's `vorname` (given name) and `nachname` (family name) are sourced from the Google token's `given_name` and `family_name` claims. If `given_name` is unavailable, the local part of the email is used as a fallback.
