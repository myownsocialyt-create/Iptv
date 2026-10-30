# Security Specification — Dual-Channel Notification System

## 1. Data Invariants
1. **Authentication Gate**: All reads and writes to `/notifications/{notificationId}` require an authenticated user (`request.auth != null`).
2. **Ownership & Audience Isolation**:
   - A notification document must have `userId == request.auth.uid` on creation.
   - `audience` must be strictly `'all'` (broadcast notification) or `'private'` (owner-only notification).
   - A user may only `get` or `list` notifications where `resource.data.userId == request.auth.uid` or `resource.data.audience == 'all'`.
   - Only the creator (`resource.data.userId == request.auth.uid`) may `update` or `delete` a notification.
3. **Immutable Metadata**: `userId` and `createdAt` cannot be modified after creation.
4. **Strict Schema & Size Bounds**:
   - `notificationId`: matches `^[a-zA-Z0-9_\-]+$`, max 128 chars.
   - `title`: string, 1..120 chars.
   - `message`: string, 1..1000 chars.
   - `imageUrl`: optional string (or null), max 1000 chars.
   - `createdAt`, `updatedAt`: server `timestamp <= request.time`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("notifications").where("audience", "==", "all").get()` -> REJECTED.
2. **Unauthenticated Write**: `unauthDb.collection("notifications").doc("n1").set(...)` -> REJECTED.
3. **Identity Spoofing on Create**: Alice creates notification with `userId: "bob_456"` -> REJECTED.
4. **Cross-User Private Read**: Alice reads Bob's private notification (`audience: "private"`, `userId: "bob_456"`) -> REJECTED.
5. **Unfiltered List Query**: Alice runs `collection("notifications").get()` without `where` clause when private docs exist -> REJECTED.
6. **Shadow Field Injection on Create**: Payload includes `isAdmin: true` ghost key -> REJECTED by `keys().hasOnly(...)`.
7. **Shadow Field Injection on Update**: Update payload modifies `isVerified: true` -> REJECTED by `affectedKeys().hasOnly(...)`.
8. **Immutable Field Mutation (`userId`)**: Alice updates `userId` to `"bob_456"` -> REJECTED.
9. **Immutable Field Mutation (`createdAt`)**: Alice updates `createdAt` to a new timestamp -> REJECTED.
10. **Value Poisoning (Oversized Title)**: `title` with 500 characters (> 120 max) -> REJECTED by `isValidNotification()`.
11. **Invalid Enum Value**: `audience: "everyone"` (not in `['all', 'private']`) -> REJECTED.
12. **Future Timestamp Spoofing**: `createdAt` set to `request.time + 1 hour` -> REJECTED by `createdAt <= request.time`.
