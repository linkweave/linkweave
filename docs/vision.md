# Vision

The **Chainlink** Bookmark manager is a self-hosted web application that helps teams or individuals manage bookmarks in a clear and simple way.

The system focuses on **observable behavior**, **clear rules**, and **traceability** from requirements to code and
tests. 

The goal is not only to provide nice user experience for managing bookmarks, but also to be **well specified**.

## Business Goals

* Enable users to manage bookmarks
* Enable users to organize bookmarks using tags and folders
* Each user starts with a single 'collection' where his bookmarks / folders / tags are stored for the collection
* Allow users to share collections
* Ensure users can see and change only bookmarks they are allowed to access
* Keep specifications, implementation, and tests consistent over time
* Application will be self-hosted

## Key Principles

* **Specifications are the source of truth**
  System behavior is defined in structured specifications, not only in code.

* **System Use Cases define behavior**
  Each system use cases describe observable system behavior with main and alternative flows.

* **Traceability by design**
  Requirements, use cases, code, and tests are linked using stable identifiers.

* **AI as an assistant**
  AI is used to generate and update code and tests from specifications in small, controlled steps.

## Scope

In scope:

* Authentication
* Users as owner of collections
* Collections
* Bookmarks
* Folders
* Tags
* Basic auditing 

Out of scope:

* User management

## Target Users

* Individuals wanting to manage bookmakrs

## Quality Goals

* Correctness over completeness
* Clear and testable behavior
* Simple architecture that supports change
* Nice Developer Experience
* Continuous Integration: Chrome E2E tests must run in the pipeline for every change


## Techstack

* vue.js with pinia
* Quarkus
* SQLite for persistence
