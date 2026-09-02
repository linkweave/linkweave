# Use Case: Disable AI Tag Suggestions for a Collection

## Overview

**Use Case ID:** UC-112
**Use Case Name:** Disable AI Tag Suggestions for a Collection
**Primary Actor:** Collection Member
**Secondary Actors:** Collection Owner, Operator
**Goal:** Let anyone with access to a collection turn AI tag suggestions off for that collection alone, so a collection whose contents should not be sent to a model — or whose owner simply does not want machine suggestions — can opt out without the operator disabling the feature for everyone.
**Status:** Implemented

## Traceability

**Maps to:** FR-105
**Related:** UC-097 (Auto-Tag Bookmark with Local LLM — the feature being opted out of); FR-096 (the operator's global kill switch, which this does not replace); UC-045 / FR-051 (rule-based suggestions, which are unaffected); UC-054 BR-114/BR-115 (the per-collection `screenshotEnabled` toggle this mirrors); UC-108 (degradation — supplies the `DISABLED` status this reuses); UC-023 (shared collections)

---

## Background — why a per-collection switch

FR-096 already lets an Operator disable LLM auto-tagging, but only for the whole
installation. That is the wrong granularity for the two situations users actually
hit:

1. **Mixed sensitivity.** A user keeps a "Work — client X" collection alongside
   "Weekend reading". With `linkweave.autotag.provider=openai` (FR-097) the title,
   URL and description of every bookmark being typed are sent to a hosted API. The
   local-only guarantee (UC-097 BR-080) holds for `ollama` and not for a hosted
   provider, so the decision belongs to whoever owns the collection's contents —
   today they can only accept it for everything or lose the feature everywhere.
2. **Noise.** In a collection with a large, generic tag vocabulary the model's
   picks are rarely useful, and the section is a distraction in a dialog the user
   passes through many times a day.

The screenshot feature already solved the same shape of problem with a
per-collection flag (`screenshotEnabled`, UC-054 BR-114/BR-115). This use case
follows that precedent rather than inventing a second pattern, with one
deliberate difference: screenshots default **off** and are opted into, while AI
tagging is already live for everyone and so defaults **on** and is opted out of
(BR-112-2).

## Preconditions

- The actor has access to the collection (UC-023), in any role.
- LLM auto-tagging is enabled for the installation (`linkweave.autotag.llm.enabled=true`, FR-096).

## Main Success Scenario

1. Member opens the collection's settings.
2. System displays the collection's settings, including an "AI tag suggestions" switch showing the current state and which provider and model it would use.
3. Member turns the switch off.
4. System saves the change immediately.
5. System stores the setting on the collection and confirms the change.
6. Member opens the bookmark dialog in that collection.
7. System shows the Suggested tags section with rule-based suggestions only; the AI group is absent entirely, and no model call has been made — not on open, not while typing (BR-112-3, BR-112-7).
8. Member adds and edits bookmarks as normal; tags are picked manually or accepted from rules.

## Alternative Flows

### A1: Feature Disabled Installation-Wide

**Trigger:** The Operator has set `linkweave.autotag.llm.enabled=false` (step 2).
**Flow:**

1. System shows the switch as unavailable, stating that AI tagging is turned off for this installation.
2. The collection setting is neither required nor consulted — the global flag already decides (BR-112-1).
3. Use case ends.

### A2: Another Member Changes It

**Trigger:** A different member of a shared collection changes the switch (step 3).
**Flow:**

1. System saves the change, which applies to every member — the setting is a property of the collection, not a per-member preference (BR-112-5).
2. Members with the collection open see the change on their next bookmark dialog.
3. Use case ends.

### A3: Turned Off While a Suggestion Is in Flight

**Trigger:** A suggestion request for this collection is already running when the setting is saved (step 5).
**Flow:**

1. System saves the setting; the running request is not tracked or cancelled.
2. The next suggestion request for the collection is refused, and any result the in-flight request produces is discarded by the client when the dialog is next opened.
3. Use case continues at step 6.

### A4: Suggestions Requested Through the API

**Trigger:** A caller requests suggestions for the collection directly, bypassing the dialog (step 7) — the CLI, the browser extension, or a script.
**Flow:**

1. System refuses the same way it refuses in the dialog: no model call, and a response saying the feature is off for this collection (BR-112-4).
2. Use case ends.

### A5: Turned Back On

**Trigger:** A member re-enables the switch.
**Flow:**

1. System stores the setting.
2. The next bookmark dialog opened in that collection warms up the model and offers suggestions as before. Nothing needs to be rebuilt or re-indexed — suggestions were never persisted (UC-097).
3. Use case ends.

### A6: Collection Is Deleted or Transferred

**Trigger:** Ownership of the collection changes, or the collection is removed.
**Flow:**

1. The setting travels with the collection; the new Owner inherits the state the previous one chose.
2. Use case ends.

## Postconditions

### Success Postconditions

- The collection carries an explicit AI-tagging state, visible to every member and editable by the Owner.
- No warm-up, suggestion, or model-pull request is made on behalf of a collection that has the feature off — verified at the API, not only hidden in the UI.
- Rule-based suggestions, manual tagging, import, and every other collection feature behave identically to before.
- No bookmark text from an opted-out collection reaches a hosted provider.

### Failure Postconditions

- The setting is unchanged and the previous state remains in force; the Owner is told the change was not saved.
- The feature never fails "open": if the setting cannot be read, the collection is treated as opted out rather than silently sending its contents to a model (BR-112-6).

## Business Rules

### BR-112-1: The Global Flag Wins

The operator's installation-wide flag (FR-096) and the per-collection setting are
in series, not in parallel: suggestions run only when both allow it. A collection
may never re-enable a feature the operator has turned off — the operator's reasons
(no Ollama container, no memory budget, a rollback) are facts about the host that
a collection setting cannot change.

### BR-112-2: Default On

The setting defaults to **enabled** on new and existing collections. AI tagging is
already active for every collection today, and a migration that defaulted to off
would silently remove a working feature from everyone who has it. This is the
opposite default from `screenshotEnabled` (UC-054 BR-115), which was new and
costly and therefore opt-in; here the user is opting *out*.

### BR-112-3: Off Means No Model Call

When the setting is off, the system performs no model work for that collection:
no warm-up, no suggestion request, no model pull triggered on its behalf. It is
not enough to hide the suggestions in the UI — the point of the setting is that
the collection's bookmark text is never sent anywhere, so the request must not be
made in the first place.

### BR-112-4: Enforced Server-Side

The check lives on the server, on every endpoint that can reach the model, so the
CLI, the browser extension, and any direct API caller are bound by it. A
client-side check alone would make the setting a display preference rather than a
guarantee.

### BR-112-5: Collection-Wide, Changeable by Any Member

The setting is a property of the collection and applies to every member of a
shared collection: it describes what may be done with the collection's contents,
which is not a matter of individual preference, so there is no per-member
variant.

Any member with access may change it, deliberately unlike the screenshot toggle
(UC-054 BR-114, owner-or-admin). Turning the feature *off* is a withdrawal of
consent that anyone whose bookmarks are in the collection should be able to make
without waiting for an owner, and the cost of a wrong choice is small and
instantly reversible — no data is lost, and the next toggle restores it. It
therefore does not travel on the collection-update endpoint, which stays
owner-or-admin because it also carries the name and the fetch allowlist.

### BR-112-6: Fail Closed

Any uncertainty about the setting — an unreadable collection, an unresolvable
identifier — resolves to "disabled". Every other failure mode in the auto-tagging
area is best-effort and fails open (UC-097 BR-077, UC-108); this one must not,
because failing open here means sending data the owner asked to be withheld.

### BR-112-7: Off Means Invisible

"Turned off for this collection" and "the model is unavailable" (UC-108 BR-108-5)
must not look the same. A degraded model says so, because it is expected back. A
switched-off one says nothing at all: the AI group is absent from the Suggested
tags section entirely — no heading, no placeholder, no explanation. There is
nothing to retry and nothing to wait for, and a permanent notice about an absent
feature is clutter in a dialog the user passes through many times a day. Where
the whole section would then be empty, the section itself does not render.

## Acceptance Criteria

1. With the setting off, opening the bookmark dialog in that collection produces zero requests to the suggestion and warm-up endpoints.
2. With the setting off, a direct API call to both suggestion endpoints returns the disabled state and makes no model call, for a collection the caller otherwise has access to.
3. With the setting on and the global flag off, no model call is made — the global flag wins.
4. Any member with access to the collection can change the setting, and the change applies to every member.
5. Existing collections are enabled after migration; no user experiences a change in behaviour until an Owner chooses one.
6. Rule-based suggestions (FR-051) are present and unchanged in an opted-out collection, and the AI group is absent from the dialog rather than empty.

## Notes / Implementation Considerations

- **Shape.** A non-null `boolean aiTaggingEnabled` on `Collection` defaulting to
  `true`, mirroring `screenshotEnabled`: a Flyway migration adding the column with
  `DEFAULT 1`, the field on `CollectionUpdateJson` / `CollectionInfoJson`, the zod
  schema in `frontend/src/schemas/collection.ts`, and a switch in
  `CollectionSettingsModal`.
- **Where the check goes.** `BookmarkAutoTagResource` already resolves the
  collection and calls `AuthorizationService`; the gate sits beside it, in the
  resource, so both suggestion endpoints and warm-up are covered by construction.
  Note that the resource is deliberately non-transactional (UC-108 BR-108-3) — the
  lookup must not reintroduce a connection held across the model call.
- **Suppressing warm-up matters.** The dialog calls warm-up on every open. If only
  the suggest endpoints were gated, an opted-out collection would still be loading
  the model on the host, which contradicts BR-112-3 even though no bookmark text
  would leave.
- **One authoritative signal for the client.** The warm-up response carries
  `enabled`, so the dialog learns on open whether to render the AI group at all.
  Deriving it from the collection store instead would be faster by one round trip
  but would need the store's collection to be the dialog's collection, which is
  not guaranteed; the AI group therefore stays hidden until warm-up answers,
  which is also the state that cannot flash the wrong thing.
- **Defence in depth.** The suggest endpoints still return
  `SuggestionStatusJson.DISABLED` (UC-108) for an opted-out collection, so a
  caller that skips warm-up — the CLI, the extension, a script — is refused on the
  endpoint it actually calls.
