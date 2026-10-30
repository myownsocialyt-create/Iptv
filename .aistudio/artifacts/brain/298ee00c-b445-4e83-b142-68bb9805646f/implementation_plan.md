# Dual-Channel Notification System (In-App Notification Center & Background Mobile Push)

This plan introduces a complete two-channel notification system powered by **Firebase Cloud Messaging (FCM) and Cloud Firestore**, combining an interactive **In-App Notification Center** (accessible from the home top-bar bell icon with an unread badge) and **Background Mobile Push Notifications** (delivered to the phone's status bar even when the app is closed) using a custom monochrome **TV Play + Signal Waves** status bar icon.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **What You Need to Send / Do Next:**
> - **No manual files required to start:** You can simply click **Proceed** on this plan. We can provision Firebase directly in AI Studio, or if you already have a specific Firebase project you want to use, you can share your **Firebase Project ID** (e.g. `my-iptv-app-123`) or `google-services.json`.
> - **Two Ways to Send Notifications:** Once built, you will be able to send notifications either directly from the **Firebase Console** (Firestore `notifications` collection / FCM Composer) **or** right from inside the app using a built-in **Admin Broadcast Composer** (unlocked via a secret Admin PIN in the Notification Center).

- **Confirmed Backend & Delivery**: **Firebase Cloud Messaging (FCM) + Cloud Firestore** for real-time in-app synchronization and background system tray push notifications when the app is closed.
- **Confirmed Message Payload & Content**: Each notification supports **Title**, **Message body text**, **Formatted Timestamp** (e.g., *"Just now"*, *"2h ago"*, or date/time), and an optional **Banner Image URL** (rendered as a rich rounded media banner inside the app and an expandable `BigPictureStyle` image in the Android status bar notification).
- **Confirmed Status Bar Icon Design**: Custom monochrome white-on-transparent vector drawable (**Modern TV Play screen with signal waves**) strictly following Android status bar silhouette guidelines so it never renders as a blank square.

---

### 1. Overview & Core Concept

- **What It Does**:
  1. **In-App Notification Center (Bell Icon UI)**: Activates the notification bell icon in the Home top bar with a live **Unread Count Badge**. Tapping the bell opens a sleek dark-themed Notification Center modal/sheet displaying all broadcast messages with title, message text, relative timestamp, and full-width banner image previews, plus a *"Mark all as read"* action and an optional PIN-protected **Admin Broadcast Composer**.
  2. **Background Mobile Push Notifications (When App is Closed)**: Delivers high-priority Android system notifications to the user's lock screen and notification tray even when the app is not open, complete with expandable banner images (`BigPictureStyle`) and deep-link tap handling that opens the app directly to the Notification Center.
- **Target Audience / Persona**: IPTV viewers who need instant updates on new live events, sports matches, channel additions, or maintenance alerts—and the app administrator who wants a frictionless way to broadcast rich visual updates.
- **Key Value**: Ensures 100% message reach whether the user is actively browsing channels inside the app or has their phone locked with the app closed.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **First Launch / Permission Flow**: On Android 13+ (API 33+), the app gracefully requests the runtime notification permission (`POST_NOTIFICATIONS`) and registers the `hypnotix_broadcasts` high-importance notification channel.
  2. **Receiving a Background Push Notification (App Closed)**:
     - A system notification arrives in the Android status bar displaying the **Modern TV Play + Signal Waves** monochrome vector icon, notification title, message summary, and expandable banner image.
     - Tapping the system notification launches the app and automatically opens the **In-App Notification Center** highlighting the message.
  3. **Browsing the In-App Notification Center (Bell Icon)**:
     - When new notifications exist, the top-bar Bell icon displays a glowing cyan/red unread badge counter.
     - Tapping the Bell icon opens the **Notification Center** sheet with smooth spring animation.
     - Users can scroll through notification cards, view full 16:9 rounded banner images, see unread indicator dots and relative timestamps, or tap *"Mark All Read"*.
  4. **Sending a Notification (Admin Flow)**:
     - You can add a document in Firebase Firestore (`notifications` collection) or send via Firebase Messaging, **or** long-press the Notification Center header / tap the Admin icon, enter the Admin PIN, enter **Title**, **Message**, and optional **Banner Image URL**, and tap **Send Broadcast** to push to all users immediately.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Premium Dark Cinema aesthetic matching the existing IPTV interface (`#07090E` deep obsidian background, `#111622` elevated card surfaces).
  - *Color Palette & Mood*:
    - `--background`: `#07090E` (Deep Night Obsidian)
    - `--surface-card`: `#131926` with `1.dp` subtle border (`#1E293B`)
    - `--primary-accent`: `#00E5FF` (Electric Cyan for active badges, signal wave accents, and unread indicators)
    - `--badge-alert`: `#FF3B5C` (Vibrant Crimson for unread count pill on the top-bar bell icon)
    - `--text-primary`: `#F8FAFC` and `--text-secondary`: `#94A3B8`
  - *Typography & Hierarchy*: Bold `18.sp` modal header title, `15.sp` semi-bold notification card titles, `13.sp` readable body copy with generous line height, and `11.sp` muted timestamps.
  - *Component Styling & Layout*:
    - **Top-Bar Bell Button**: `38.dp` circular surface button with an overlaid `16.dp` unread badge pill in the top-right corner.
    - **Notification Cards**: `16.dp` rounded corners, `14.dp` internal padding, unread cyan accent bar on the left edge, optional `160.dp`-height rounded banner image with shimmer/crossfade loading, and clean timestamp footer.
    - **Monochrome Status Bar Icon**: `24.dp x 24.dp` pure white (`#FFFFFF`) vector silhouette on a transparent canvas depicting a sleek rounded TV monitor with a central play triangle and radiating broadcast signal arcs at the top-right.

- **Interactive Feedback & Motion**:
  - Unread badge scales in with a spring animation when new notifications arrive.
  - Notification cards animate smoothly on open, and tapping a card marks it as read while expanding long message text or previewing the banner image.
  - Empty state displays a custom illustration of the TV Play + Signal Waves icon with a friendly message (*"No notifications yet — stay tuned for live channel & event updates!"*).

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual FCM Push + Firestore Sync with WorkManager Background Delivery**
  - *Chosen Approach*: Combine Firebase Cloud Messaging (topic `"all_broadcasts"`) with a Cloud Firestore `notifications` collection and a lightweight periodic/push-triggered background sync worker.
  - *Why*: Pure FCM notification messages sent from the Firebase Console only show in the system tray if the app is in the background and do not automatically populate an in-app notification history list. By pairing FCM with Firestore (and a background sync check), every broadcast both triggers an immediate mobile status bar alert when the app is closed **and** persists in the In-App Notification Center history when the user taps the bell icon.
  - *Alternatives Considered*: FCM-only (rejected because users who miss or dismiss the system tray notification wouldn't see it inside the app's bell icon history).

- **Decision 2: Dedicated Monochrome Vector Status Bar Icon**
  - *Chosen Approach*: Create a dedicated white-only vector drawable (`#FFFFFF` paths on transparent background) featuring a TV screen, play symbol, and signal waves, registered both in `NotificationCompat.Builder.setSmallIcon()` and `com.google.firebase.messaging.default_notification_icon` in the manifest.
  - *Why*: Android's status bar ignores RGB color channels in notification small icons and renders non-transparent pixels as solid white. Using the full-color launcher logo causes Android to draw an unrecognizable solid white square. A purpose-built silhouette vector guarantees crisp rendering across all Android OEM status bars and lock screens.

- **Decision 3: Built-In Admin Broadcast Composer Inside the App**
  - *Chosen Approach*: Include a PIN-protected Admin Broadcast dialog right inside the Notification Center (in addition to Firebase Console support).
  - *Why*: Allows you to send instant notifications (Title, Message, Banner Image URL) straight from your phone in seconds without needing to open a computer or log into the Firebase Console every time.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                   FIREBASE CLOUD BACKEND (FCM + FIRESTORE)                │
│   ┌───────────────────────────────┐   ┌───────────────────────────────┐   │
│   │ Firestore: /notifications     │   │ FCM Topic: "all_broadcasts"   │   │
│   │ { title, message, imageUrl,   │   │ Push Payload (Data + Alert)   │   │
│   │   timestamp, priority }       │   │ Triggers when app is closed   │   │
│   └───────────────┬───────────────┘   └───────────────┬───────────────┘   │
└───────────────────┼───────────────────────────────────┼───────────────────┘
                    │                                   │
                    ▼                                   ▼
┌───────────────────────────────────┐   ┌───────────────────────────────────┐
│   NotificationRepository & Sync   │   │   FcmBroadcastService & Worker    │
│ • Real-time & REST Firestore sync │   │ • Receives background push        │
│ • Local read/unread state store   │   │ • Downloads Banner Bitmap (Coil)  │
│ • Admin Broadcast publish helper  │   │ • Posts BigPicture System Notif   │
└───────────────────┬───────────────┘   └───────────────┬───────────────────┘
                    │                                   │
                    ▼                                   ▼
┌───────────────────────────────────┐   ┌───────────────────────────────────┐
│     Home Top Bar (Bell Icon)      │   │   Android System Status Bar       │
│ • Unread count badge overlay      │   │ • Monochrome TV+Waves Small Icon  │
│ • Opens NotificationCenterDialog  │   │ • Expandable Banner Image         │
│ • Lists Title, Body, Time, Banner │   │ • Tap Intent -> Opens Bell Dialog │
└───────────────────────────────────┘   └───────────────────────────────────┘
```

- **Data Model & State**:
  - `AppNotification`:
    - `id: String` — Unique notification identifier
    - `title: String` — Headline of the announcement
    - `message: String` — Full body text
    - `imageUrl: String` — Optional HTTP/HTTPS URL for the 16:9 banner image
    - `timestamp: Long` — Epoch milliseconds for sorting and relative time formatting (*"5m ago"*, *"Yesterday"*)
  - `LocalNotificationPrefs`:
    - Tracks `readNotificationIds: Set<String>` and `notifiedSystemIds: Set<String>` in `SharedPreferences` so the app accurately displays the unread badge count on the bell icon and never fires duplicate system tray notifications for already-seen broadcasts.

- **Interactive Component & State Mapping**:
  - **Top-Bar Bell Icon (`RenderHomeTopBar`)**: Observes `unreadCount` state; renders red/cyan badge when `unreadCount > 0`; clicking sets `showNotificationCenter = true`.
  - **Notification Center Modal (`NotificationCenterDialog`)**:
    - Displays header with unread badge, *"Mark all read"* button, and Admin Broadcast trigger.
    - Renders scrollable list of `AppNotification` items with title, message, formatted timestamp, and async banner image preview.
    - Clicking a notification marks its `id` as read and updates the badge counter immediately.
  - **Admin Broadcast Sheet**: Validates non-empty Title & Message, optional Banner Image URL, and Admin PIN before writing the new notification document to Firestore so all connected devices receive it in real time and in the background.
  - **System Notification Builder (`SystemNotificationHelper`)**: Uses the monochrome **Modern TV Play + Signal Waves** icon, downloads the `imageUrl` bitmap off the main thread when present, applies `NotificationCompat.BigPictureStyle()`, and attaches a `PendingIntent` with extra `"open_notifications" = true` so tapping the mobile notification opens the app straight to the Notification Center.
