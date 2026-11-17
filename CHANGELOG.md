# Changelog

## v6.7.39 (versionCode 694)

### Play Store release notes (ready to paste, ~500 char limit)

```
• Added: offline HSN/GST catalog massively expanded for Kirana, Electrical, Electronics, Auto Parts, Health/Supplements and General Retail — with fuzzy, misspelling-tolerant item matching and a "Did you mean?" picker.
• Fixed: Google Sign-In, which had stopped working after a Play Services update, now uses Credential Manager.
• Added: barcode scanning now works fully offline (no Play Services model download to wait on).
• Improved: profit calculation (P&L, Item/Sales Profitability, dashboard loss warnings) now shares one weighted-average-cost engine so the numbers always agree.
```

### Detailed changes

**Offline HSN/GST catalog & fuzzy matching**
- `HsnStoreCatalogs.kt` (new): exhaustive offline HSN/GST entries by Indian retail vertical — health/fitness/clinical/Ayurvedic supplements, Kirana/general provision, electrical (wires, switchgear, lighting, fans, power backup, conduits, tools), electronics/IT/networking/CCTV/office/mobile, automobile & two-wheeler spare parts (2W/4W/commercial/lubricants), and plumbing/hardware/kitchenware/stationery/general retail. Merged into `HsnCommonItems.entries` as the single source of truth for the parser, Excel importer and stock-item lookup.
- `HsnFuzzyMatcher.kt` (new): pure-JVM matcher over the full 22,306+ HSN/Chapter-99-SAC directory plus the new retail catalogs — inverted token index, O(1) phonetic consonant-skeleton index, phrase index, and Damerau-Levenshtein. Resolves arbitrary word order, misspellings, Indian orthographic variants, and noisy real-world SKU text (brands, vehicle models, pack weights, wattages, voltages, colors, flavours).
- `HsnDidYouMeanSection.kt` (new): surfaces "Did you mean this?" (one close match) or "Is it either one of these?" (multiple) in item creation instead of silently guessing.
- `RetailCatalogAndFuzzyHsnTest.kt` (new, 842 lines): verifies catalog/asset integrity, misspelling and word-order resilience, UI confidence tiers, automatic HSN/GST assignment in FinanceHelper, and a combinatorial permutation + latency + gibberish-rejection stress test.

**Sign-in & offline scanning**
- Google Sign-In rewritten onto `androidx.credentials` (Credential Manager) + Sign-In-with-Google; the legacy `com.google.android.gms.auth.api.signin` path had its backing implementation removed from current Play Services and was failing every sign-in with `ApiException 10` (`DEVELOPER_ERROR`) regardless of OAuth client configuration (`AuthManager.kt`).
- Barcode scanning switched to the bundled on-device ML Kit model instead of the `play-services-mlkit-barcode-scanning` variant, so it works immediately offline instead of depending on a Play Services model download that could silently never finish.

**Profit & reporting**
- `ProfitCalculator.kt` (new, 542 lines): single weighted-average-cost engine shared by the Profit & Loss report, Sales/Item Profitability, the dashboard loss warning, and the Balance Sheet's profit line, so they can no longer disagree. Sale/cost values exclude GST; cost is the weighted-average stock cost as of the sale date (opening stock + purchases to date, net of what already went out), not the latest purchase price; returns reverse both the original sale/purchase value and its cost; purchases not linked to a stock item are costed in the period they were booked.
- `InvoiceTemplatesExtra.kt` + `InvoiceTemplateSelectionScreen.kt`: added business-oriented invoice template mockups (e.g. `GST_CLASSIC`) to the template picker.

**Printing**
- `EasyPrint.kt` (new, 285 lines): one-tap printing that detects reachable printers by name instead of asking the user to choose a connection mode, IP address, and paper width before anything prints; remembers the first successful choice.
- `ThermalPrinterScreen.kt` and `ThermalPrinterHelper.kt` reworked alongside it (USB permission flow, expand/collapse sections, trimmed unused icons/imports).
- `ExportUtils.kt`, `PdfImages.kt`, `PdfNavigator.kt`: PDF/export rendering reworked to support the above.

**Finance Helper (Hinglish voice/text parser) — carried over from v6.7.38**
- `kroad`/`kroar` (misspellings/ASR variants of "crore") now recognized as 1,00,00,000, matching `karod`/`krod`/`kror`/`krore`.
- "cash mein"/"cash me"/"cash mai" stripped during normalization instead of leaking into the multi-item splitter and producing junk items (e.g. "Splendor Cash Mein" at ₹0).
- `SpeechRepair.kt` (new, 221 lines) + `SpeechRepairTest.kt`: n-best transcript repair layer (glued number words, "for"→4) shared by all three mic entry points.

**Tests**
- `GstFpTest.kt` updated to the paise API (`totalPaise`/`cgstPaise`); it had been using the pre-paise `GstInvoice(totalValue=…)` API and was breaking compilation of the entire test source set.
- `FinanceHelperParsingTest.kt`: 3 new regression tests for the `kroad` and "cash mein" fixes above.

### Notes for this build
- `versionCode` 692 → 694, `versionName` 6.7.37 → 6.7.39 (6.7.38 was superseded before release and folded into this build).
- No database schema changes.

## v6.7.38 (versionCode 693)

### Play Store release notes (ready to paste, ~500 char limit)

```
• Fixed: voice/text entries using "crore" spelled as "kroad" were ignored, showing ₹0 instead of the real amount.
• Fixed: cash sales (e.g. "...bechi cash mein...") no longer get incorrectly split into two separate junk items — cash transactions are now recorded as one clean entry under Cash-in-hand.
```

### Detailed changes

**Finance Helper (Hinglish voice/text parser)**
- `kroad`/`kroar` (common misspellings/ASR variants of "crore") are now recognized as 1,00,00,000, matching the existing `karod`/`krod`/`kror`/`krore` variants. Previously an amount like "2 kroad" was left as literal text, so no amount was ever extracted.
- "cash mein"/"cash me"/"cash mai" is now stripped during normalization instead of leaking into the sentence. Previously it survived into the multi-item splitter, which mistook the leftover number ("2 kroad") for a second item and absorbed "cash mein" into the first item's name (e.g. "Splendor Cash Mein" / "Kroad Ki", both at ₹0).
- Added 3 regression tests covering both fixes in `FinanceHelperParsingTest.kt`.

### Notes for this build
- `versionCode` 692 → 693, `versionName` 6.7.37 → 6.7.38.
- No database schema changes.

## v6.7.3 (versionCode 657)

### Notes for this build
- `versionCode` 656 → 657, `versionName` 6.7.2 → 6.7.3.
- No database schema changes.

## v6.7.2 (versionCode 656)

### Play Store release notes (ready to paste, ~500 char limit)

```
• Fixed: the finance assistant no longer mistakes your questions for transactions. Asking "is it legal to pay more than 10000 in cash?" now answers with the correct law instead of trying to create a payment entry.
• Tax answers are now grounded in the actual GST statute text with section/page citations, instead of made-up numbers.
• If the assistant isn't sure of an answer, it now says so and suggests confirming with your CA rather than guessing.
• The offline AI now downloads automatically when you open the Finance Assistant, so it works even without internet.
```

### Detailed changes

**Helafin chat — intent-aware, no more wrong vouchers**
- Added a deterministic intent classifier (`IntentClassifier.kt`) that runs before any voucher parsing. A knowledge/legal/help question is now always answered directly and can never be turned into a voucher (this fixes "asked a GST question → it drafted a payment to a nonsense party").
- Tax/law questions are answered by retrieving the relevant section from the bundled GST corpus and summarizing from those excerpts with a source/page citation (RAG), instead of free-form generation.
- Added an escalation ladder for the on-device model: empty/low-confidence answers, answers that cite a section not in the retrieved law, and confusion refusals all fall back to the cloud model, and finally to "please confirm with your CA" — the assistant no longer asserts a low-confidence answer as fact.
- Added a `verifyAnswer` second-opinion pass (DeepSeek) that checks the on-device answer and corrects it when it disagrees.
- Removed the old "NEVER ASK PERMISSION — always draft a voucher on any transaction" instruction from the cloud prompt, and removed invented tax-slab figures.
- Added the common cash-transaction income-tax rules (s.40A(3) ₹10,000 expense limit, s.269ST ₹2,00,000 receive limit, s.269SS/269T ₹20,000 loan/deposit limits) to the knowledge prompt.

**Offline AI — automatic download**
- The offline AI model now starts downloading automatically when the Finance Assistant screen is opened (previously the user had to tap "Install"). No change for users who already have the model installed.
- Migrated the cloud AI model from the deprecated `deepseek-chat` to `deepseek-v4-flash` (DeepSeek retired the old model names on 2026-07-24).

### Notes for this build
- `versionCode` 653 → 656, `versionName` 6.6.9 → 6.7.2.
- No database schema changes.
- Requires a release build signed with the existing upload key (see `AGENTS.md` for signing setup).

## v6.1.2 (versionCode 612)

### Detailed changes

**Daily Tasks — employee app redesign + data handling**
- Employee app (Teller Tasks v1.1) rebuilt as a proper to-do app: the code stays saved permanently, with a Settings screen (gear icon) to change it. Each task opens on its own screen with Done / Not Done buttons and an optional remarks line (shown when Not Done), or a value entry for number/amount/text tasks. Removed the single-screen "submit everything" button. Added a real app icon.
- Daily Tasks tile now shows on all four dashboard themes (Default, Cyber Finance, Bento, Crystal), not just the default.
- Free-tier data model: Firebase now holds only today's report per employee. Past days are archived permanently on the manager's device and deleted from Firebase automatically. Manager can multi-select dates in the Reports tab and delete them. A scheduled Cloud Function (pruneOldTaskReports) caps Firebase at 30 days as a safety net.
- Manager Reports view now shows each task's optional remark.
- Long-press an employee in the Daily Tasks list to remove them (deletes their checklist + all reports, local and cloud).
- Push notifications: when the manager adds a task, the employee's phone shows a notification even if the Teller Tasks app is closed (Firebase Cloud Messaging + a new notifyTaskAdded Cloud Function; the employee app subscribes to a per-code topic).
- Manager can schedule a checklist for the next 10/15/20/30 days (or "Always") from the Tasks tab; the employee sees the tasks each morning until the end date, then they stop automatically.
- Tasks tab shows each task's live today status (Done / Not done / value / Not filled) inline, no need to open Reports.
- Manager can tap a Done/Not done in a report to override it (e.g. untick work they don't accept); the change is saved for that day.
- Employee detail screen now has a hero card summarising today's progress ("X of Y tasks done", progress bar, status, attendance) and shows the date; the Daily Tasks list shows the date too.
- Clear labelled "Share the Tasks app with an employee" button replaces the bare share icon.

## v6.1.0 (versionCode 610)

### Play Store release notes (ready to paste, ~500 char limit)

```
• New: Daily Tasks - assign daily checklists to staff (any tasks you want - cleaning, sales counts, renewals, whatever your shop needs)
• Staff install a free companion "Teller Tasks" app, enter a code, and submit their day
• Review each employee's submitted tasks, add comments, and give Final Approval - saved to history
• Share the Teller Tasks app straight from the Daily Tasks screen, same as Live Stock
```

### Detailed changes

**Daily Tasks (new feature)**
- New "Daily Tasks" dashboard tile. Manager adds an employee, gets a unique 6-digit code, and builds a fully custom checklist per employee - tick tasks, number counts, money amounts, or free text, in any combination and order.
- New standalone "Teller Tasks" companion app (shared as an APK straight from the Daily Tasks screen, exactly like the existing Live Stock app share). Employee enters their code, sees their tasks, fills in arrival/leaving time and their entries, and submits for the day.
- Manager reviews each employee's submitted report, can add comments, then taps Final Approval to lock the day into that employee's history. "Send back" reopens a day for the employee to edit.
- Synced live via Firebase, keyed by employee code - same pending-then-approve pattern as Live Stock.

## v5.0.9 (versionCode 509)

### Play Store release notes (ready to paste, ~500 char limit)

```
• Redesigned the Ledger Statement screen with cleaner cards and clear Total Receivables/Payables
• New "Detailed View" toggle - see items sold/bought and notes on each transaction, or keep it simple
• Bigger, bolder text across voucher and ledger screens - easier to read at a glance
• Smarter ledger selection - Payment, Receipt, Sale and Purchase screens now only show relevant accounts (no more tax ledgers cluttering the list)
• Tightened up spacing and layout across item entry and ledger screens
• Behind-the-scenes cleanup for better stability
```

### Detailed changes

**Ledger Statement screen — redesigned**
- Replaced the old theme-branched table view with one unified card-based layout (light green/yellow), independent of the app's theme setting.
- Added Total Receivables / Total Payables summary pills at the top, with a floating shadow style matching the voucher screen's date/voucher-no pills.
- Transaction rows are now cards with a receipt icon, in/out arrow, party name, voucher type, date, and amount.
- New **Detailed View** toggle:
  - Off (default): compact cards - just the transaction basics.
  - On: cards also show the items traded (for Sales/Purchase) and the voucher's note/narration, and grow taller to fit.
- All variable-length text (ledger names, item lists, amounts, totals) now auto-shrinks to fit its box instead of getting cut off or overflowing, even for very large numbers.
- Reworked spacing: card padding, column alignment, and header labels all tightened and re-balanced.
- Date shown on each card is now larger and bolder (was very light/hard to read).

**Sale voucher — item entry**
- "WHAT IS THE ITEM?" section heading is now bigger.
- Delete icon on an item row only appears once a 2nd item has been added — a single item can't be removed anyway, so it no longer wastes space.
- Removed a large empty gap between the item row and "+ ADD ANOTHER ITEM" (was caused by an oversized invisible tap target on the delete icon).
- Item selection box widened; quantity box narrowed; amount box enlarged with bigger, auto-shrinking text so large amounts still fit.
- Subtotal/Grand Total card moved up to close the freed-up space.

**Ledger selection — Payment/Receipt/Purchase**
- Fixed: irrelevant system ledgers (CGST/SGST/IGST and other "Duties & Taxes" accounts) were showing up when picking who you paid or received money from. That picker now only shows real counterparties.
- Purchase supplier picker still allows Cash/Bank (matches the on-screen help text for "paid cash immediately"), but no longer shows tax or stock control ledgers.
- Sales and Journal ledger pickers were already scoped correctly and were left unchanged.

**Cleanup / stability**
- Removed an unused cloud AI dependency (Google's Gemini SDK) and a live API key that were sitting in the project but never actually called - the AI assistant has always run fully on-device (MediaPipe/Gemma), so this had no effect on behavior, just removed dead weight and a stray credential.
- Corrected a microphone-permission dialog that inaccurately claimed audio could be sent to a cloud API - it's on-device only, and the disclosure now says so.

### Notes for this build
- `versionCode` 508 → 509, `versionName` 5.0.8 → 5.0.9.
- No database schema changes.
- Requires a release build signed with the existing upload key (see `AGENTS.md` for signing setup).
