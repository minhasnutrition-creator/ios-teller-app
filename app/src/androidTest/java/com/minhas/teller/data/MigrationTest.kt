package com.minhas.teller.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Validates that every migration from version 7 to version 28 produces a schema
 * that matches what Room expects for a fresh install at version 28.
 *
 * A mismatch here would mean your migration path silently diverges from the
 * entity declarations — exactly the kind of drift that, combined with the old
 * destructive-migration-on-downgrade, could silently wipe financial data.
 *
 * The test also inserts a small set of real-looking rows at version 7 and
 * asserts they survive the full migration chain, so data‑loss regressions in
 * future migrations are caught immediately.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    companion object {
        private const val TEST_DB_NAME = "migration_test_db"
        /** Oldest exported schema version — the starting point for the migration test. */
        private const val START_VERSION = 7
        /** Current database version the app ships with. */
        private const val END_VERSION = 28

        /** Every migration the app registers, in order. */
        private val ALL_MIGRATIONS = arrayOf(
            AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_19, AppDatabase.MIGRATION_19_20,
            AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22,
            AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24,
            AppDatabase.MIGRATION_24_25, AppDatabase.MIGRATION_25_26,
            AppDatabase.MIGRATION_26_27, AppDatabase.MIGRATION_27_28
        )
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    // ── Schema equality: migrated DB == fresh DB ──────────────────────────
    @Test
    @Throws(IOException::class)
    fun migrateAll_from7_to28_matchesExpectedSchema() {
        // Create at version 7, then run every migration up to version 28.
        val db = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            END_VERSION,
            true, // validateDroppedTables
            *ALL_MIGRATIONS
        )
        // If we reach here, the schema is consistent. Clean up.
        db.close()
    }

    // ── Data integrity: rows written at version 7 survive every migration ──
    @Test
    @Throws(IOException::class)
    fun migrateAll_from7_to28_preservesData() {
        // 1. Create the database at version 7 and seed some sample data.
        val dbV7 = helper.createDatabase(TEST_DB_NAME, START_VERSION).apply {
            // Seed an account group so ledger group-column references are valid.
            execSQL("""
                INSERT INTO account_groups (id, name, companyId)
                VALUES (1, 'Sundry Debtors', 1)
            """)

            // Seed a company — required by FK constraints on vouchers/ledgers.
            execSQL("""
                INSERT INTO companies (id, name, financialYearStart, financialYearEnd, backupEnabled)
                VALUES (1, 'Test Co', '2025-04-01', '2026-03-31', 0)
            """)

            // Seed a ledger (party).  group-column must match a valid group name.
            execSQL("""
                INSERT INTO ledgers (id, name, `group`, companyId, openingBalance, openingBalanceType, closingBalance, isDefault, isPartyLedger)
                VALUES (1, 'Test Customer', 'Sundry Debtors', 1, 0.0, 'Dr', 0.0, 0, 1)
            """)

            // Seed a unit (required by stock items).
            execSQL("""
                INSERT INTO units (id, shortName, longName, companyId)
                VALUES (1, 'PCS', 'Pieces', 1)
            """)

            // Seed a voucher with a meaningful amount.
            execSQL("""
                INSERT INTO vouchers (id, type, voucherNumber, date, narration, companyId, chargeInterest, interestRate, interestDays)
                VALUES (1, 'Sales', 'INV-001', 1700000000000, 'Test invoice', 1, 0, 0.0, 0)
            """)

            // Seed a voucher entry linked to the ledger.
            execSQL("""
                INSERT INTO voucher_entries (id, voucherId, ledgerId, amount, isDebit)
                VALUES (1, 1, 1, 5000.0, 1)
            """)

            // Seed a voucher orphaned by the companyId=0 edit bug, plus its entry.
            // MIGRATION_27_28 must re-home it onto company 1 via the ledger.
            execSQL("""
                INSERT INTO vouchers (id, type, voucherNumber, date, narration, companyId, chargeInterest, interestRate, interestDays)
                VALUES (2, 'Sales', 'INV-002', 1700000000000, 'Orphaned invoice', 0, 0, 0.0, 0)
            """)
            execSQL("""
                INSERT INTO voucher_entries (id, voucherId, ledgerId, amount, isDebit)
                VALUES (2, 2, 1, 7500.0, 1)
            """)

            close()
        }

        // 2. Run every migration to bring the database up to version 28.
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            END_VERSION,
            true,
            *ALL_MIGRATIONS
        )

        // 3. Verify data survived.
        val cursor = migratedDb.query("SELECT * FROM vouchers WHERE id = 1")
        assert(cursor.moveToFirst()) { "Voucher should survive all migrations" }
        assert(cursor.getString(cursor.getColumnIndexOrThrow("voucherNumber")) == "INV-001")
        cursor.close()

        val ec = migratedDb.query("SELECT * FROM voucher_entries WHERE id = 1")
        assert(ec.moveToFirst()) { "VoucherEntry should survive all migrations" }
        assert(ec.getDouble(ec.getColumnIndexOrThrow("amount")) == 5000.0)
        ec.close()

        // The orphaned voucher must have been re-homed onto its ledger's company,
        // otherwise it stays invisible to every company-scoped query.
        val oc = migratedDb.query("SELECT companyId FROM vouchers WHERE id = 2")
        assert(oc.moveToFirst()) { "Orphaned voucher row should still exist" }
        assert(oc.getInt(oc.getColumnIndexOrThrow("companyId")) == 1) {
            "MIGRATION_27_28 should restore companyId=0 vouchers to their ledger's company"
        }
        oc.close()

        // 4. Open the migrated database through Room — this validates Room can
        //    read the final schema without throwing (would catch index mismatches,
        //    column type drift, etc.).
        val appDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java, TEST_DB_NAME
        ).build()

        // Actually read through Room DAO — if the schema drifted, this throws.
        val vouchers = appDb.voucherDao().getAllVouchersWithAllEntries(1)
        assert(vouchers.isNotEmpty()) { "Room DAO should read migrated voucher" }
        assert(vouchers.any { it.voucher.voucherNumber == "INV-001" })
        assert(vouchers.any { it.voucher.voucherNumber == "INV-002" }) {
            "Recovered voucher should be visible to the company-scoped query"
        }

        appDb.close()
        migratedDb.close()
    }
}
