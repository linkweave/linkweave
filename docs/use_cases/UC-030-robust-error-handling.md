# Use Case: Robust Error Handling

## Overview

**Use Case ID:** UC-030
**Use Case Name:** Robust Error Handling
**Primary Actor:** User
**Goal:** Provide clear and distinguishable feedback for different types of errors (login/authentication vs. backend/network issues).
**Status:** Open

## Traceability

**Maps to:** FR-040, NFR-008

---

## Preconditions

- The user is interacting with the application.
- The system is online or transitioning between states.

## Main Success Scenario

1. User performs an action (e.g., clicks a button, submits a form) that triggers an asynchronous API call to the backend.
2. The system initiates the network request.
3. The network request completes successfully.
4. The system updates the UI to reflect the successful outcome.

## Alternative Flows

### A1: Login/Authentication Error

**Trigger:** The API call fails due to invalid credentials or an expired session.
**Flow:**

1. The system detects an authentication-related error (HTTP 401 Unauthorized or 403 Forbidden).
2. The system distinguishes this as a **Login Error**.
3. The system displays a specific "Login failed" or "Invalid credentials" notification to the user.
4. If the error is due to an expired session, the system automatically redirects the user to the login page.

### A2: Backend/Network Problem

**Trigger:** The API call fails due to server-side issues or connectivity problems.
**Flow:**

1. The system detects a general error (HTTP 500+ Internal Server Error) or a network-level failure (e.g., timeout, DNS failure, connection refused).
2. The system distinguishes this as a **Backend Problem**.
3. The system displays a generic but clear "Backend problem" notification (e.g., "A backend problem occurred. Please try again later.") instead of a specific login error.
4. The system remains on the current page to allow the user to retry the action.

### A3: Validation Error

**Trigger:** The API call fails due to invalid user input (HTTP 400 Bad Request).
**Flow:**

1. The system identifies specific validation violations.
2. The system displays targeted error messages near the relevant input fields.
3. Use case ends.

## Postconditions

### Success Postconditions

- The user receives appropriate feedback based on the failure type.
- The UI state remains consistent with the failure (e.g., redirect to login for 401, remain on page for 500).

### Failure Postconditions

- No confusing or misleading error messages are displayed (e.g., showing a login error for a server timeout).
- The system remains in a usable state where the user understands the nature of the problem.

## Business Rules

### BR-001: Distinguishability

The system must clearly distinguish between authentication failures (401/403) and general server failures (500+) in the UI notification messages.

### BR-002: Notification Persistence

Login errors should be persistent or require manual dismissal to ensure the user notices the session change. General backend problems can auto-dismiss after a longer period (e.g., 5-10s).
