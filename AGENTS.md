# AGENTS.md - Teller Android App Codebase Guide

## Project Overview
**Teller** is a comprehensive Android accounting & inventory management app (Package: `com.minhas.teller`). It enables multi-company management, financial reporting, inventory tracking, and data export with offline-first design. Currently at version 6.7.3 (versionCode 657).

**Key Markets**: India (GSTIN, HSN support), supports multiple themes and dark mode.

---

## Architecture & Data Flow

### Multi-Layer Architecture
```
UI Layer (Compose Screens) 
  → ViewModels (State management)
  → Repositories (Business logic)
  → DAOs + Room Database (Persistence)
```

### Critical Architectural Decision: Multi-Company Session
- **SessionManager** (`SessionManager.kt`): Global state holding `currentCompanyId` as `StateFlow<Int?>`
- All screens access company context via `SessionManager.currentCompanyId.collectAsState()`
- Company selection triggers full app reboot (splash screen → company list)
- Database is **company-segmented**: all queries filter by `companyId`
- **All new features MUST filter data by active company** - this is non-negotiable

### Navigation Architecture
- **Sealed class-based navigation** (`navigation/Screen.kt`) with type-safe routes
- Bottom nav only on: Dashboard, Daybook, Reports
- Dynamic background gradients based on current screen (see `MainActivity.kt` lines 305-359)
- Theme context propagates through Compose hierarchy

---

## Database Schema & Migration Strategy

### Room Database (AppDatabase.kt)
- **Version 18** with 13 core entities:
  - Ledger, Voucher, VoucherEntry (accounting)
  - StockItem, Unit, InventoryEntry (inventory)
  - Company, AccountGroup, BillAllocation (master data)
  - AuditLog, TaxCategory, Godown, ChatMessageEntity, HsnEntry
- Schema exported to `app/schemas/` for version control
- **Active migrations**: M7→M8 through M17→M18 (handle carefully - data loss risks)

### Critical Pattern: Bill-wise Allocation
- `BillAllocation` tracks outstanding payments by bill reference
- Fields: `voucherId`, `refVoucherId`, `refName`, `dueDate`
- Used across Reports (Pending Payments, Outstanding Summary)

---

## Tech Stack & Key Dependencies

### Core Android & Compose
- **Kotlin 17** (Java 17 target), Compose 1.5.14, Material3
- **Lifecycle**: ViewModel, Room 2.8.4, WorkManager 2.10.0

### Advanced Features (Critical External Integrations)
1. **Offline AI Assistant**: MediaPipe LLM (Gemma 3 1B) - 3GB model download on demand by ChatViewModel - runs fully on-device, no cloud AI calls
2. **OCR/Document Scanning**: ML Kit Text Recognition + Google Document Scanner (16.0.0)
3. **Google Drive Backup**: WorkManager + play-services-auth (21.0.0) - scheduled daily, runs when charging + network connected
4. **Export Engine**:
   - PDF: iText 5.5.10
   - Excel: Apache POI 5.3.0 (watch out for large datasets!)
5. **Licensing & Monetization**: Google Play Billing 7.0.0 + Play Integrity 1.3.0
6. **Firebase**: Real-time database (version 32.8.0 BOM)

> **Cloud + on-device AI**: The app uses BOTH on-device AI (MediaPipe/Gemma for offline chat
> normalization, ML Kit OCR) and cloud AI — `DeepSeekClient` (`api.deepseek.com`) for finance
> queries and `GeminiReceiptScanner` (Google Gemini) for receipt scanning, keyed via
> `BuildConfig.GEMINI_API_KEY` / `DEEPSEEK_API_KEY`. Because cloud AI receives financial data
> (ledger names, amounts, receipt images), the Play Store listing must carry a "User Data /
> third-party AI" disclosure & consent flow. (An earlier Gemini *remote SDK* dependency was
> removed; current cloud calls use the app's own HTTP clients, not that SDK.)

> **Helafin chat — intent routing & grounding** (`ChatViewModel`, `IntentClassifier`, `AnswerValidator`):
> Every message is first classified into `QUESTION / REPORT / TRANSACTION / CHART / AMBIGUOUS` by
> `utils/IntentClassifier.kt` (conservative — questions outrank transaction verbs, so a query like
> "is paying cash above 10000 illegal?" is answered, never drafted as a voucher). Knowledge/legal
> questions are answered via `GstKnowledgeBase` RAG (summarise *only* from cited statute excerpts)
> with an escalation ladder: empty/low-confidence/phantom-section answers fall back to
> `DeepSeekClient.smartChat`/`verifyAnswer`, and finally to "confirm with your CA". The handwritten
> voucher regexes in `FinanceHelper` are now a gated fast-path, not the primary intent engine.

---

## Project-Specific Conventions & Patterns

### Screen File Structure
ALL screens follow this pattern in `ui/screens/`:
```kotlin
@Composable
fun [ScreenName]Screen(navController: NavHostController, varargs: params...) {
    val viewModel: [ScreenName]ViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    // UI layout
}
```

### UI Components Pattern
- Reusable components in `ui/components/` (GlassBottomNavBar, etc.)
- Theme context in `ui/theme/` - multiple themes supported: DEFAULT, MODERN_LIGHT, CYBER_FINANCE, BENTO, CRYSTAL
- Glassmorphism (semi-transparent frosted effect) consistent across screens

### Repository Pattern
- One repository per entity (LedgerRepository, VoucherRepository, etc.)
- Repositories use DAOs and handle:
  - Company-level filtering
  - Calculations (balances, totals)
  - Cross-entity logic
  - Error handling

### Preferences & Shared State
- **ThemeManager**: Current theme persistence + activity restart for theme changes
- **BackupPrefs**: Google Drive backup enablement state
- **LicenseUtils**: Trial/subscription tracking (FORCE_FULL_LICENSE toggle in Debug)
- **TerminologyManager**: App terminology (company name vs customer, etc.)

---

### 16KB Page Size Support
- **Linker Flags**: `-Wl,-z,max-page-size=16384` applied in `app/build.gradle` and `mlc4j/build.gradle`.
- **Packaging**: `useLegacyPackaging = false` in all modules to allow uncompressed, 16kb-aligned native libs.
- **Gradle Properties**: `android.useLegacyPackaging=false`. (Note: `android.bundle.enableUncompressedNativeLibs` is deprecated as it is now default true).
- **Pre-built Libs**: Check alignment of `.so` files in `jniLibs` using `check_align.py` if adding new ones.

## Critical Developer Workflows

### Building & Running
```bash
# Debug build (enables FORCE_FULL_LICENSE = true, all features unlocked)
./gradlew assembleDebug

# Release build (ProGuard minification enabled, requires local.properties signing config)
./gradlew assembleRelease

# Install to device
./gradlew installDebug
```

### Database Migrations
**When modifying Room entities:**
1. Update entity class in `data/entities/`
2. Increment `@Database(..., version = X+1)`
3. Add migration: `val MIGRATION_X_Y = object : Migration(X, X+1) { ... }`
4. Register in `Room.databaseBuilder()` call (in companion object)
5. Test with `adb logcat | grep Room` to catch migration errors
6. Always backup before releasing with schema changes!

### Testing MediaPipe LLM Download
- Model downloads on first Chat screen access
- Check `ChatViewModel` for download progress tracking
- 3GB file → requires wifi + device storage
- If stuck, clear app data and retry

### Permission Request Flow
- MainActivity.kt handles runtime permissions (lines 122-176)
- Covers: RECORD_AUDIO (Chat), INTERNET, NETWORK_STATE, POST_NOTIFICATIONS (Android 13+)
- First launch checks all permissions; continued grace period even if denied

---

## Integration Points & External APIs

### Google APIs
| API | Purpose | Version | Notes |
|-----|---------|---------|-------|
| Google Drive API | Daily automatic backups | Handled via play-services-auth 21.0.0 | Requires OAuth credentials in Google Cloud Console |
| ML Kit OCR | Document text extraction | 19.0.1 (unbundled) | 16KB page size issue resolved with special Linker flags |
| Document Scanner | Receipt/invoice scanning | 16.0.0 | Custom activity override for large screen support |
| Play Billing | In-app subscriptions | 7.0.0 | Manages trial → paid conversion |
| Play Integrity | Anti-fraud attestation | 1.3.0 | Validates app legitimacy |

### Backup Architecture
- **Daily WorkManager task** (BackupWorker.kt scheduled every 24h)
- Destination: `getExternalFilesDir(null)/DailyBackups/`
- Pre-backup: WAL checkpoint to merge write-ahead logs
- Sharing: Via Android share sheet (WhatsApp, email, Drive)

### Reporting Engine
- **Daybook** → all transactions (searchable by date)
- **Financial statements** → calculated across voucher entries with company-wide aggregation
- **Stock reports** → real-time quantity + value tracking
- **Outstanding reports** → bill-wise aggregation using BillAllocation entity
- All reports exportable to PDF (iText) or Excel (POI)

---

## Known Limitations & Gotchas

1. **ProGuard Minification (Release builds)**:
   - Enabled with `shrinkResources true`
   - Verify keep rules in `proguard-rules.pro`
   - Test release APK thoroughly

2. **Excel Export Large Datasets**:
   - POI can be memory-intensive for 1000+ rows
   - Consider pagination or chunked export for large datasets

3. **ML Kit Text Recognition**:
   - Android 13+ linker flag required: `-Wl,-z,max-page-size=16384`
   - Configured in build.gradle CMake arguments

4. **MediaPipe LLM Model**:
   - 3GB download happens silently on first Chat access
   - No offline model bundling (by design - size constraints)
   - User should see download progress UI in ChatViewModel

5. **Database Backup & Restore**:
   - Room uses SQLite WAL (Write-Ahead Log)
   - Backup must checkpoint WAL before export or data loss occurs
   - BackupUtils.kt handles this internally

6. **Company Deletion**:
   - Cascading deletes required across all tables (implement in repository)
   - Audit trail (AuditLog) should preserve deleted company transactions

---

## Common Tasks for AI Agents

### Adding a New Report
1. Create entity + DAO if needed in `data/`
2. Add repository method for calculation
3. Create `[ReportName]ViewModel` with StateFlow<UiState>
4. Add Screen composable in `ui/screens/`
5. Register route in `Screen.kt` sealed class
6. Add navigation in `MainActivity.kt` NavHost
7. Update `getScreenTitle()` function
8. Add to Reports menu screen

### Adding a New Export Format
1. Create utility in `utils/ExportUtils.kt`
2. Call from screen's export handler
3. Test with sample data
4. Add to share sheet options
5. Log export by action (audit trail)

### Adding Multi-Tenant Support to Existing Feature
1. Add `companyId` parameter to all database queries
2. Update DAO with filtered queries
3. Ensure Repository filters by `SessionManager.currentCompanyId`
4. Test with multiple companies

---

## File Reference Map

| Path | Purpose |
|------|---------|
| `app/build.gradle` | All dependencies, signing config, API key build fields |
| `app/src/main/java/com/minhas/teller/MainActivity.kt` | App entry, navigation, theme setup |
| `data/AppDatabase.kt` | Room DB definition + all migrations |
| `data/entities/` | Ledger, Voucher, StockItem, etc. |
| `data/dao/` | Queries for each entity |
| `data/repositories/` | Business logic layer |
| `ui/screens/` | All 40+ Compose screen implementations |
| `ui/viewmodels/` | State management per screen |
| `ui/theme/` | Composable gradients, theme variants |
| `utils/SessionManager.kt` | Current company state |
| `utils/BackupUtils.kt` | Backup/restore + WAL checkpointing |
| `utils/ExportUtils.kt` | PDF/Excel export logic |
| `utils/ThemeManager.kt` | Theme persistence |
| `navigation/Screen.kt` | All route definitions |
| `workers/BackupWorker.kt` | WorkManager daily backup task |
| `workers/MLCLLMWorker.kt` | MediaPipe LLM support |
| `services/` | Empty (business logic in repos/workers) |

---

## Debugging Tips

- **Theme not persisting**: Check `ThemeManager.getCurrentTheme()` disk read logic
- **ML Kit crashes**: Verify `ndkVersion 27.0.12077973` in build.gradle
- **Backup fails silently**: Check `BackupWorker` logs for network/storage permissions
- **Report math wrong**: Verify all queries filter by `companyId` and transaction date ranges
- **Chat model missing**: Check LogCat for MediaPipe download progress in ChatViewModel
- **ProGuard issues**: Add keep rules in `proguard-rules.pro` for serialization libraries

