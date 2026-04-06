# Task-136 Offline Operations Suite

This repository contains a Kotlin Multiplatform app for workplace nutrition and resource management. **Android Views is the primary delivered product UI path**, with shared domain/data logic in the `:shared` module.

## Architecture

- **Primary UI**: Android Views (Fragments + XML layouts) in `composeApp/src/androidMain/`
- **Shared logic**: KMP shared module (`shared/`) containing Room entities/DAOs, RBAC/ABAC, ViewModels, workflows, and business logic
- **Compose layer**: Exists as a supplementary/reference path, not the primary product delivery
- **DI**: Koin
- **Persistence**: Room with SQLCipher encryption at rest
- **Offline-only**: All business logic is local; no network dependencies

## Prerequisites

- JDK 17+
- Android SDK (set `ANDROID_HOME` or `sdk.dir` in `local.properties`)
- Android platform tools (`adb`) for install/run
- Docker (optional, for containerized build/test path)

## Project Modules

- `:shared` - KMP shared logic (Room entities/DAO, RBAC/ABAC, ViewModels, workflows, tests)
- `:composeApp` - Android app module (Activity, Fragments, layouts, manifest)

## Bootstrap and Startup

1. Build shared tests/artifacts:
```bash
./gradlew :shared:testDebugUnitTest
```

2. Build Android debug app:
```bash
./gradlew :composeApp:assembleDebug
```

3. Install on device/emulator:
```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

4. Launch app entry point:
- Activity: `com.eaglepoint.task136.MainActivity`
- Manifest: `composeApp/src/androidMain/AndroidManifest.xml`

## Test Commands

```bash
./run_tests.sh
# or directly:
./gradlew :shared:testDebugUnitTest
```

## Demo/Bootstrap Accounts

In debug builds, demo users are seeded at first launch from `LocalAuthService`:

- `admin` / `Admin1234!` (Admin role - full access including Admin panel)
- `supervisor` / `Super1234!` (Supervisor role)
- `operator` / `Oper12345!` (Operator role)
- `viewer` / `Viewer1234!` (Viewer role - read-only)
- `companion` / `Companion1!` (Companion role, delegated to operator)

## Android Views Product Flows

### Authentication
- Login with password policy (10+ chars, requires number)
- 5 failed attempts triggers 15-minute lockout
- 30-minute idle session expiry, 8-hour absolute limit
- Optional device binding (max 2 devices per user unless admin reset)

### Dashboard
- Resource list with RecyclerView + DiffUtil
- Navigation to Calendar, Cart, Learning, and Admin (Admin role only)
- Session stats (resources, cart, invoices, refunds)

### Admin Panel (Admin role only)
Accessible from Dashboard for Admin users:
- **Resource Management**: Add/delete resources with name, category, units, price, allergens
- **Business Rules Display**: Price range validation, allergen requirements, auth policies
- **Device Binding Reset**: Reset device bindings for any user

### Calendar / Booking
- Resource-driven slot suggestions (up to 3 available within 14 days, 10-min buffer)
- Meeting submission with conflict detection against existing bookings
- Form version resolved via canary evaluation (role/device-group gating)

### Meeting Detail
- Attendee management (ABAC-gated: Supervisor/Admin can see attendees)
- Approval/denial workflow (Supervisor/Admin only)
- Check-in within +/-10 minute window
- Auto no-show tracking after 10 minutes
- Attachment management (add/remove)
- Agenda editing

### Cart / Ordering
- Catalog-backed item addition from DAO-persisted resources
- Split/merge cart items
- Checkout generates persisted invoice
- Invoice detail access with persistence-backed loading
- Notes and tags display

### Invoice Detail
- Loaded from Room persistence (not volatile in-memory state)
- Subtotal, tax (Admin-only visibility), total display
- Targeted refund by specific invoice ID (not "latest in memory")

### Dynamic Form Engine / Canary Rollout
- `CanaryEvaluator` bound in production Koin DI
- Meeting request workflow uses versioned form engine
- Form version varies by role and device group via canary config
- Form version metadata persisted with meeting record

### Offline Governance / Rule-Hit Analytics
- Validation failures (price violations) logged to `GovernanceDao`
- Allergen block events logged as rule hits
- Refund denial events logged with reason
- `RuleHitObserver` monitors open rule hits for anomaly detection
- `ReconciliationService` handles daily closure and weekly settlement

## Security Controls

- **RBAC**: Role-based permissions (Admin, Supervisor, Operator, Viewer, Companion)
- **ABAC**: Attribute-based policies for attendee lists, invoice tax fields, refund issuance
- **Object-level authorization**: All order/invoice loads require actor context; Admin/Supervisor get elevated access
- **Encrypted at rest**: SQLCipher database encryption
- **Masked logs**: Sensitive fields redacted from log output
- **Device binding**: Max 2 devices per user, admin-resettable

## Static Verification Playbook

1. **Security**: Verify no `loadOrderById(orderId)` single-param exists; all loads require role+actorId
2. **Admin flow**: Verify `AdminFragment` exists and is reachable via `navigateToAdmin()` in Dashboard
3. **Canary integration**: Verify `CanaryEvaluator` is in `sharedCoreModule` DI; `MeetingWorkflowViewModel.submitMeeting` uses `resolveFormVersion`
4. **Invoice persistence**: Verify `loadInvoiceById` loads from `InvoiceDao`; `refundInvoice` targets specific ID
5. **Governance analytics**: Verify `GovernanceAnalytics` is called from `OrderWorkflowViewModel` and `OrderFinanceViewModel`
6. **Tests**: Run `./run_tests.sh` - covers authorization, admin nav, canary, invoice persistence, governance
