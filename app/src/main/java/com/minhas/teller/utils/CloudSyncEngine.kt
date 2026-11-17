package com.minhas.teller.utils

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SyncResult(
    val success: Boolean,
    val message: String,
    val pushed: Int = 0,
    val pulled: Int = 0
)

/**
 * Cloud Backup & Restore engine over Firebase Realtime Database.
 *
 * Data is partitioned under `users/{firebaseUid}/data/{entity}/{entityUid}`.
 * Each record carries `uid` (stable cross-device key), `updatedAt` (epoch millis) and
 * `isDeleted` (soft-delete tombstone). Deletions propagate as tombstones so deleted companies
 * and vouchers never resurrect from older cloud snapshots.
 *
 * Provides distinct operations:
 * - [backup]: Explicitly uploads active and soft-deleted data from phone to cloud. Phone is master.
 * - [restore]: Explicitly downloads non-deleted cloud data to phone with safety checks.
 * - [sync]: Backward-compatible two-way sync for periodic workers.
 */
class CloudSyncEngine(private val context: Context) {
    private val tag = "CloudSyncEngine"
    private val db = AppDatabase.getDatabase(context)
    // Fresh formatter per use: SimpleDateFormat is not thread-safe.
    private val sdf: SimpleDateFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val prefs = context.getSharedPreferences("cloud_sync_prefs", Context.MODE_PRIVATE)

    private fun rootRef(uid: String): DatabaseReference =
        FirebaseDatabase.getInstance().getReference("users/$uid/data")

    /**
     * Uploads the phone's current data as the master copy to the cloud.
     * Deletions on the phone are properly saved as tombstones on the cloud.
     */
    suspend fun backup(): SyncResult = withContext(Dispatchers.IO) {
        val uid = SessionManager.currentUserId.value
            ?: return@withContext SyncResult(false, "Not signed in to Google account")
        val start = System.currentTimeMillis()
        try {
            backfillUids()
            val pushed = push(uid, forceAll = false)
            prefs.edit().putLong("last_backup_$uid", start).apply()
            prefs.edit().putLong("last_sync_$uid", start).apply()
            val result = SyncResult(true, "Cloud backup completed ($pushed records updated)", pushed = pushed)
            Log.i(tag, "Backup complete: ${result.message}")
            result
        } catch (e: Exception) {
            Log.e(tag, "Backup failed", e)
            SyncResult(false, "Cloud backup failed: ${e.message}")
        }
    }

    /**
     * Downloads cloud data to this phone.
     * Strictly filters out deleted companies, deleted vouchers, and deleted items so they never resurrect.
     */
    suspend fun restore(): SyncResult = withContext(Dispatchers.IO) {
        val uid = SessionManager.currentUserId.value
            ?: return@withContext SyncResult(false, "Not signed in to Google account")
        val start = System.currentTimeMillis()
        try {
            val pulled = pull(uid)
            prefs.edit().putLong("last_restore_$uid", start).apply()
            prefs.edit().putLong("last_sync_$uid", start).apply()
            val result = SyncResult(true, "Cloud restore completed ($pulled records processed)", pulled = pulled)
            Log.i(tag, "Restore complete: ${result.message}")
            result
        } catch (e: Exception) {
            Log.e(tag, "Restore failed", e)
            SyncResult(false, "Cloud restore failed: ${e.message}")
        }
    }

    /**
     * Two-way synchronization for background tasks. Pushes local changes first, then pulls updates.
     */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val uid = SessionManager.currentUserId.value
            ?: return@withContext SyncResult(false, "Not signed in")
        val start = System.currentTimeMillis()
        try {
            backfillUids()
            val pushed = push(uid, forceAll = false)
            val pulled = pull(uid)
            prefs.edit().putLong("last_sync_$uid", start).apply()
            prefs.edit().putLong("last_backup_$uid", start).apply()
            val result = SyncResult(true, "Synced: $pushed uploaded, $pulled restored", pushed, pulled)
            Log.i(tag, "Sync complete: ${result.message}")
            result
        } catch (e: Exception) {
            Log.e(tag, "Sync failed", e)
            SyncResult(false, "Sync failed: ${e.message}")
        }
    }

    fun lastBackupTime(): Long? {
        val uid = SessionManager.currentUserId.value ?: return null
        val time = prefs.getLong("last_backup_$uid", 0L)
        return if (time > 0) time else lastSyncTime()
    }

    fun lastRestoreTime(): Long? {
        val uid = SessionManager.currentUserId.value ?: return null
        return prefs.getLong("last_restore_$uid", 0L).takeIf { it > 0 }
    }

    fun lastSyncTime(): Long? {
        val uid = SessionManager.currentUserId.value ?: return null
        return prefs.getLong("last_sync_$uid", 0L).takeIf { it > 0 }
    }

    /** Assign stable uids to pre-existing rows (migrated before this feature) and persist them. */
    private suspend fun backfillUids() {
        db.companyDao().getAllCompaniesForSync().filter { it.uid == null }.forEach {
            db.companyDao().update(it.copy(uid = UUID.randomUUID().toString()))
        }
        db.companyDao().getAllCompaniesForSync().forEach { c ->
            db.ledgerDao().getAllLedgersForSync(c.id).filter { it.uid == null }.forEach {
                db.ledgerDao().update(it.copy(uid = UUID.randomUUID().toString()))
            }
            db.unitDao().getAllUnitsForSync(c.id).filter { it.uid == null }.forEach {
                db.unitDao().update(it.copy(uid = UUID.randomUUID().toString()))
            }
            db.stockItemDao().getAllStockItemsForSync(c.id).filter { it.uid == null }.forEach {
                db.stockItemDao().update(it.copy(uid = UUID.randomUUID().toString()))
            }
            db.voucherDao().getAllVouchersWithAllEntriesForSync(c.id).map { it.voucher }
                .filter { it.uid == null }.forEach {
                    db.voucherDao().update(it.copy(uid = UUID.randomUUID().toString()))
                }
        }
    }

    // ---- Push (Upload Phone -> Cloud) --------------------------------------------------------

    private suspend fun push(uid: String, forceAll: Boolean = false): Int {
        val since = if (forceAll) 0L else prefs.getLong("last_sync_$uid", 0L)
        var count = 0
        val companies = db.companyDao().getAllCompaniesForSync().filter { it.uid != null }

        // id -> uid maps so child records can reference parents by uid.
        val ledgerUidById = HashMap<Int, String?>()
        val unitUidById = HashMap<Int, String?>()
        val stockUidById = HashMap<Int, String?>()

        for (c in companies) {
            val key = c.uid!!
            if (c.isDeleted) {
                // Actually remove from cloud to prevent wasted space and eliminate ghost resurrections
                rootRef(uid).child("companies").child(key).removeValue().await()
                count++
            } else if (forceAll || c.updatedAt > since) {
                rootRef(uid).child("companies").child(key).setValue(companyMap(c)).await()
                count++
            }
        }
        for (c in companies) {
            val companyUid = c.uid!!
            for (l in db.ledgerDao().getAllLedgersForSync(c.id)) {
                val key = l.uid ?: continue
                ledgerUidById[l.id] = key
                if (l.isDeleted || c.isDeleted) {
                    rootRef(uid).child("ledgers").child(key).removeValue().await()
                    count++
                } else if (forceAll || l.updatedAt > since) {
                    rootRef(uid).child("ledgers").child(key).setValue(ledgerMap(l, companyUid)).await()
                    count++
                }
            }
        }
        for (c in companies) {
            val companyUid = c.uid!!
            for (u in db.unitDao().getAllUnitsForSync(c.id)) {
                val key = u.uid ?: continue
                unitUidById[u.id] = key
                if (u.isDeleted || c.isDeleted) {
                    rootRef(uid).child("units").child(key).removeValue().await()
                    count++
                } else if (forceAll || u.updatedAt > since) {
                    rootRef(uid).child("units").child(key).setValue(unitMap(u, companyUid)).await()
                    count++
                }
            }
        }
        for (c in companies) {
            val companyUid = c.uid!!
            for (s in db.stockItemDao().getAllStockItemsForSync(c.id)) {
                val key = s.uid ?: continue
                stockUidById[s.id] = key
                if (s.isDeleted || c.isDeleted) {
                    rootRef(uid).child("stock_items").child(key).removeValue().await()
                    count++
                } else if (forceAll || s.updatedAt > since) {
                    val unitUid = s.unitId.let { unitUidById[it] ?: db.unitDao().getUnitById(it)?.uid }
                    rootRef(uid).child("stock_items").child(key).setValue(stockMap(s, companyUid, unitUid)).await()
                    count++
                }
            }
        }
        for (c in companies) {
            val companyUid = c.uid!!
            for (vwe in db.voucherDao().getAllVouchersWithAllEntriesForSync(c.id)) {
                val v = vwe.voucher
                val key = v.uid ?: continue
                if (v.isDeleted || c.isDeleted) {
                    rootRef(uid).child("vouchers").child(key).removeValue().await()
                    count++
                } else if (forceAll || v.updatedAt > since) {
                    // Returns point at their original bill by local id; sync the bill's uid instead.
                    val returnOfUid = v.returnOfVoucherId?.let { db.voucherDao().getVoucherById(it)?.uid }
                    rootRef(uid).child("vouchers").child(key)
                        .setValue(voucherMap(v, companyUid, vwe.voucherEntries, vwe.inventoryEntries, ledgerUidById, stockUidById) + ("returnOfUid" to returnOfUid)).await()
                    count++
                }
            }
        }
        return count
    }

    // ---- Pull (Download Cloud -> Phone) ------------------------------------------------------

    private suspend fun pull(uid: String): Int {
        var count = 0
        val root = rootRef(uid)

        // 1. Companies
        val companyUidToId = HashMap<String, Int>()
        for (snap in root.child("companies").get().await().children) {
            val c = companyFrom(snap) ?: continue
            val local = db.companyDao().getCompanyByUid(c.uid!!)
            if (c.isDeleted) {
                // Cloud says company was deleted: soft-delete locally if it exists, but NEVER resurrect it!
                if (local != null && !local.isDeleted) {
                    db.companyDao().softDeleteCompanyCascade(local.id, c.updatedAt)
                    count++
                }
                continue
            }

            if (local == null) {
                companyUidToId[c.uid!!] = db.companyDao().insert(c.copy(id = 0)).toInt()
                count++
            } else if (c.updatedAt > local.updatedAt) {
                db.companyDao().update(c.copy(id = local.id))
                companyUidToId[c.uid!!] = local.id
                count++
            } else {
                if (!local.isDeleted) {
                    companyUidToId[c.uid!!] = local.id
                }
            }
        }

        // 2. Ledgers
        val ledgerUidToId = HashMap<String, Int>()
        for (snap in root.child("ledgers").get().await().children) {
            val l = ledgerFrom(snap) ?: continue
            val companyId = companyUidToId[snap.s("companyUid")] ?: continue // Skip if company is deleted or missing
            val local = db.ledgerDao().getLedgerByUid(l.uid!!)
            if (l.isDeleted) {
                if (local != null && !local.isDeleted) {
                    db.ledgerDao().update(local.copy(isDeleted = true, updatedAt = l.updatedAt))
                }
                continue
            }

            if (local == null) {
                ledgerUidToId[l.uid!!] = db.ledgerDao().insert(l.copy(id = 0, companyId = companyId)).toInt()
                count++
            } else if (l.updatedAt > local.updatedAt) {
                db.ledgerDao().update(l.copy(id = local.id, companyId = companyId))
                ledgerUidToId[l.uid!!] = local.id
                count++
            } else {
                if (!local.isDeleted) ledgerUidToId[l.uid!!] = local.id
            }
        }

        // 3. Units
        val unitUidToId = HashMap<String, Int>()
        for (snap in root.child("units").get().await().children) {
            val u = unitFrom(snap) ?: continue
            val companyId = companyUidToId[snap.s("companyUid")] ?: continue // Skip if company is deleted
            val local = db.unitDao().getUnitByUid(u.uid!!)
            if (u.isDeleted) {
                if (local != null && !local.isDeleted) {
                    db.unitDao().update(local.copy(isDeleted = true, updatedAt = u.updatedAt))
                }
                continue
            }

            if (local == null) {
                unitUidToId[u.uid!!] = db.unitDao().insert(u.copy(id = 0, companyId = companyId)).toInt()
                count++
            } else if (u.updatedAt > local.updatedAt) {
                db.unitDao().update(u.copy(id = local.id, companyId = companyId))
                unitUidToId[u.uid!!] = local.id
                count++
            } else {
                if (!local.isDeleted) unitUidToId[u.uid!!] = local.id
            }
        }

        // 4. Stock items
        val stockUidToId = HashMap<String, Int>()
        for (snap in root.child("stock_items").get().await().children) {
            val s = stockFrom(snap) ?: continue
            val companyId = companyUidToId[snap.s("companyUid")] ?: continue // Skip if company is deleted
            val unitId = unitUidToId[snap.s("unitUid")] ?: 0
            val local = db.stockItemDao().getStockItemByUid(s.uid!!)
            if (s.isDeleted) {
                if (local != null && !local.isDeleted) {
                    db.stockItemDao().update(local.copy(isDeleted = true, updatedAt = s.updatedAt))
                }
                continue
            }

            if (local == null) {
                db.stockItemDao().insert(s.copy(id = 0, companyId = companyId, unitId = unitId))
                val inserted = db.stockItemDao().getStockItemByUid(s.uid!!)
                if (inserted != null) stockUidToId[s.uid!!] = inserted.id
                count++
            } else if (s.updatedAt > local.updatedAt) {
                db.stockItemDao().update(s.copy(id = local.id, companyId = companyId, unitId = unitId))
                stockUidToId[s.uid!!] = local.id
                count++
            } else {
                if (!local.isDeleted) stockUidToId[s.uid!!] = local.id
            }
        }

        // 5. Vouchers (atomic)
        for (snap in root.child("vouchers").get().await().children) {
            val parsed = voucherFrom(snap, companyUidToId, ledgerUidToId, stockUidToId) ?: continue
            if (parsed.voucher.isDeleted) {
                // If cloud says voucher is deleted, tombstone it locally if exists, but NEVER resurrect!
                val local = parsed.voucher.uid?.let { db.voucherDao().getVoucherByUid(it) }
                if (local != null && !local.isDeleted) {
                    db.withTransaction {
                        db.voucherEntryDao().deleteVoucherEntriesForVoucher(local.id)
                        db.inventoryEntryDao().deleteInventoryEntriesForVoucher(local.id)
                        db.voucherDao().update(local.copy(isDeleted = true, updatedAt = parsed.voucher.updatedAt))
                    }
                }
                continue
            }

            // If company was deleted or missing, skip the voucher completely
            if (parsed.voucher.companyId <= 0) continue

            if (applyVoucher(parsed)) count++
        }

        return count
    }

    private suspend fun applyVoucher(p: ParsedVoucher): Boolean {
        val local = p.voucher.uid?.let { db.voucherDao().getVoucherByUid(it) }
        val returnOfId = p.returnOfUid?.let { db.voucherDao().getVoucherByUid(it)?.id }
        if (local == null) {
            if (p.voucher.isDeleted) return false // Nothing to delete locally
            return db.withTransaction {
                val id = db.voucherDao().insert(p.voucher.copy(id = 0, returnOfVoucherId = returnOfId)).toInt()
                db.inventoryEntryDao().insertAll(p.inventory.map { it.copy(id = 0, voucherId = id) })
                p.entries.forEach { db.voucherEntryDao().insert(it.copy(id = 0, voucherId = id)) }
                id > 0
            }
        }
        if (p.voucher.updatedAt <= local.updatedAt) return false
        return db.withTransaction {
            db.voucherEntryDao().deleteVoucherEntriesForVoucher(local.id)
            db.inventoryEntryDao().deleteInventoryEntriesForVoucher(local.id)
            if (p.voucher.isDeleted) {
                db.voucherDao().update(local.copy(isDeleted = true, updatedAt = p.voucher.updatedAt))
            } else {
                db.voucherDao().update(p.voucher.copy(id = local.id, returnOfVoucherId = returnOfId ?: local.returnOfVoucherId))
                db.inventoryEntryDao().insertAll(p.inventory.map { it.copy(id = 0, voucherId = local.id) })
                p.entries.forEach { db.voucherEntryDao().insert(it.copy(id = 0, voucherId = local.id)) }
            }
            true
        }
    }

    // ---- Serialization (push) ----------------------------------------------------------------

    private fun companyMap(c: Company) = hashMapOf<String, Any?>(
        "uid" to c.uid, "name" to c.name, "mailingName" to c.mailingName, "address" to c.address,
        "state" to c.state, "country" to c.country, "pincode" to c.pincode, "gstin" to c.gstin,
        "phoneNumber" to c.phoneNumber, "email" to c.email, "backupEnabled" to c.backupEnabled,
        "currencySymbol" to c.currencySymbol, "financialYearFrom" to c.financialYearFrom,
        "stampType" to c.stampType, "stampColor" to c.stampColor, "logoUri" to c.logoUri,
        "signatureUri" to c.signatureUri, "stampDesignName" to c.stampDesignName, "upiId" to c.upiId,
        "updatedAt" to c.updatedAt, "isDeleted" to c.isDeleted
    )

    private fun ledgerMap(l: Ledger, companyUid: String) = hashMapOf<String, Any?>(
        "uid" to l.uid, "name" to l.name, "alias" to l.alias, "group" to l.group,
        "openingBalance" to l.openingBalance, "maintainBillWise" to l.maintainBillWise,
        "phoneNumber" to l.phoneNumber, "gstin" to l.gstin, "stateCode" to l.stateCode,
        "companyUid" to companyUid, "updatedAt" to l.updatedAt, "isDeleted" to l.isDeleted
    )

    private fun unitMap(u: Unit, companyUid: String) = hashMapOf<String, Any?>(
        "uid" to u.uid, "name" to u.name, "formalName" to u.formalName,
        "companyUid" to companyUid, "updatedAt" to u.updatedAt, "isDeleted" to u.isDeleted
    )

    private fun stockMap(s: StockItem, companyUid: String, unitUid: String?) = hashMapOf<String, Any?>(
        "uid" to s.uid, "name" to s.name, "alias" to s.alias, "unitUid" to unitUid,
        "openingStock" to s.openingStock, "rate" to s.rate, "companyUid" to companyUid,
        "hsnCode" to s.hsnCode, "gstRate" to s.gstRate, "cgstRate" to s.cgstRate,
        "sgstRate" to s.sgstRate, "igstRate" to s.igstRate, "hsnAutoDetected" to s.hsnAutoDetected,
        "updatedAt" to s.updatedAt, "isDeleted" to s.isDeleted
    )

    private fun voucherMap(
        v: Voucher,
        companyUid: String,
        entries: List<VoucherEntry>,
        items: List<InventoryEntry>,
        ledgerUidById: Map<Int, String?>,
        stockUidById: Map<Int, String?>
    ): HashMap<String, Any?> {
        val entriesArr = entries.mapNotNull { e ->
            val lUid = ledgerUidById[e.ledgerId] ?: return@mapNotNull null
            hashMapOf("ledgerUid" to lUid, "amount" to e.amount, "isDebit" to e.isDebit)
        }
        val itemsArr = items.mapNotNull { i ->
            val sUid = stockUidById[i.stockItemId] ?: return@mapNotNull null
            hashMapOf(
                "stockItemUid" to sUid, "quantity" to i.quantity, "rate" to i.rate, "amount" to i.amount,
                "hsnCode" to i.hsnCode, "gstRate" to i.gstRate,
                "cgstRate" to i.cgstRate, "sgstRate" to i.sgstRate, "igstRate" to i.igstRate,
                "taxableAmount" to i.taxableAmount, "taxAmount" to i.taxAmount, "totalAmount" to i.totalAmount
            )
        }
        return hashMapOf(
            "uid" to v.uid, "type" to v.type, "date" to sdf.format(v.date),
            "voucherNumber" to v.voucherNumber, "narration" to v.narration,
            "customField" to v.customField, "chargeInterest" to v.chargeInterest,
            "interestRate" to v.interestRate, "interestDays" to v.interestDays,
            "refDisplayName" to v.refDisplayName, "isFinanced" to v.isFinanced,
            "placeOfSupply" to v.placeOfSupply, "supplyType" to v.supplyType,
            "reverseCharge" to v.reverseCharge, "companyUid" to companyUid,
            "entries" to entriesArr, "items" to itemsArr,
            "updatedAt" to v.updatedAt, "isDeleted" to v.isDeleted
        )
    }

    // ---- Deserialization (pull) --------------------------------------------------------------

    private fun companyFrom(snap: DataSnapshot): Company? {
        val uid = snap.s("uid") ?: return null
        return Company(
            id = 0, name = snap.s("name") ?: "", mailingName = snap.s("mailingName"),
            address = snap.s("address"), state = snap.s("state"), country = snap.s("country"),
            pincode = snap.s("pincode"), gstin = snap.s("gstin"), phoneNumber = snap.s("phoneNumber"),
            email = snap.s("email"), backupEnabled = snap.b("backupEnabled"),
            currencySymbol = snap.s("currencySymbol") ?: "₹",
            financialYearFrom = snap.l("financialYearFrom"),
            stampType = snap.i("stampType"), stampColor = snap.i("stampColor"),
            logoUri = snap.s("logoUri"), signatureUri = snap.s("signatureUri"),
            stampDesignName = snap.s("stampDesignName"), upiId = snap.s("upiId"),
            uid = uid, updatedAt = snap.l("updatedAt"), isDeleted = snap.b("isDeleted")
        )
    }

    private fun ledgerFrom(snap: DataSnapshot): Ledger? {
        val uid = snap.s("uid") ?: return null
        return Ledger(
            id = 0, name = snap.s("name") ?: "", alias = snap.s("alias"),
            group = snap.s("group") ?: "Sundry Debtors", openingBalance = snap.l("openingBalance"),
            maintainBillWise = snap.b("maintainBillWise"), phoneNumber = snap.s("phoneNumber"),
            companyId = 0, gstin = snap.s("gstin"), stateCode = snap.s("stateCode"),
            uid = uid, updatedAt = snap.l("updatedAt"), isDeleted = snap.b("isDeleted")
        )
    }

    private fun unitFrom(snap: DataSnapshot): Unit? {
        val uid = snap.s("uid") ?: return null
        return Unit(
            id = 0, name = snap.s("name") ?: "", formalName = snap.s("formalName"),
            companyId = 0, uid = uid, updatedAt = snap.l("updatedAt"), isDeleted = snap.b("isDeleted")
        )
    }

    private fun stockFrom(snap: DataSnapshot): StockItem? {
        val uid = snap.s("uid") ?: return null
        return StockItem(
            id = 0, name = snap.s("name") ?: "", alias = snap.s("alias"), unitId = 0,
            openingStock = snap.d("openingStock"), rate = snap.d("rate"), companyId = 0,
            hsnCode = snap.s("hsnCode"), gstRate = snap.d("gstRate"), cgstRate = snap.d("cgstRate"),
            sgstRate = snap.d("sgstRate"), igstRate = snap.d("igstRate"),
            hsnAutoDetected = snap.b("hsnAutoDetected"),
            uid = uid, updatedAt = snap.l("updatedAt"), isDeleted = snap.b("isDeleted")
        )
    }

    private data class ParsedVoucher(
        val voucher: Voucher,
        val entries: List<VoucherEntry>,
        val inventory: List<InventoryEntry>,
        val returnOfUid: String? = null
    )

    private fun voucherFrom(
        snap: DataSnapshot,
        companyUidToId: Map<String, Int>,
        ledgerUidToId: Map<String, Int>,
        stockUidToId: Map<String, Int>
    ): ParsedVoucher? {
        val uid = snap.s("uid") ?: return null
        val dateStr = snap.s("date") ?: return null
        val date = try { sdf.parse(dateStr) ?: Date() } catch (_: Exception) { Date() }
        val companyId = companyUidToId[snap.s("companyUid")] ?: 0

        val entries = mutableListOf<VoucherEntry>()
        for (e in snap.child("entries").children) {
            val ledgerId = ledgerUidToId[e.s("ledgerUid")] ?: continue
            entries.add(VoucherEntry(voucherId = 0, ledgerId = ledgerId, amount = e.l("amount"), isDebit = e.b("isDebit")))
        }
        val items = mutableListOf<InventoryEntry>()
        for (i in snap.child("items").children) {
            val stockItemId = stockUidToId[i.s("stockItemUid")] ?: continue
            items.add(InventoryEntry(
                voucherId = 0, stockItemId = stockItemId, quantity = i.d("quantity"), rate = i.d("rate"),
                amount = i.l("amount"), hsnCode = i.s("hsnCode"), gstRate = i.d("gstRate"),
                cgstRate = i.d("cgstRate"), sgstRate = i.d("sgstRate"), igstRate = i.d("igstRate"),
                taxableAmount = i.l("taxableAmount"), taxAmount = i.l("taxAmount"), totalAmount = i.l("totalAmount")
            ))
        }

        val voucher = Voucher(
            id = 0, type = snap.s("type") ?: "Sales", date = date,
            voucherNumber = snap.s("voucherNumber") ?: "",
            narration = snap.s("narration"), companyId = companyId,
            customField = snap.s("customField"), chargeInterest = snap.b("chargeInterest"),
            interestRate = snap.d("interestRate"), interestDays = snap.i("interestDays"),
            refDisplayName = snap.s("refDisplayName"), isFinanced = snap.b("isFinanced"),
            placeOfSupply = snap.s("placeOfSupply"), supplyType = snap.s("supplyType"),
            reverseCharge = snap.b("reverseCharge"),
            uid = uid, updatedAt = snap.l("updatedAt"), isDeleted = snap.b("isDeleted")
        )
        return ParsedVoucher(voucher, entries, items, snap.s("returnOfUid"))
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private fun DataSnapshot.s(key: String): String? = child(key).getValue(String::class.java)
    private fun DataSnapshot.d(key: String): Double = (child(key).getValue(Any::class.java) as? Number)?.toDouble() ?: 0.0
    private fun DataSnapshot.l(key: String): Long = (child(key).getValue(Any::class.java) as? Number)?.toLong() ?: 0L
    private fun DataSnapshot.i(key: String): Int = (child(key).getValue(Any::class.java) as? Number)?.toInt() ?: 0
    private fun DataSnapshot.b(key: String): Boolean = child(key).getValue(Boolean::class.java) ?: false
}
