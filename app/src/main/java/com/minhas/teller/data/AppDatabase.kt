package com.minhas.teller.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.minhas.teller.data.converters.Converters
import com.minhas.teller.data.dao.LedgerDao
import com.minhas.teller.data.dao.VoucherDao
import com.minhas.teller.data.dao.VoucherEntryDao
import com.minhas.teller.data.dao.StockItemDao
import com.minhas.teller.data.dao.UnitDao
import com.minhas.teller.data.dao.InventoryEntryDao
import com.minhas.teller.data.dao.CompanyDao
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Company

import com.minhas.teller.data.dao.AccountGroupDao
import com.minhas.teller.data.entities.AccountGroup

import com.minhas.teller.data.dao.AuditLogDao
import com.minhas.teller.data.dao.BillAllocationDao
import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.data.entities.AuditLog
import com.minhas.teller.data.entities.TaxCategory
import com.minhas.teller.data.entities.Godown
import com.minhas.teller.data.entities.ChatMessageEntity
import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.entities.Quotation
import com.minhas.teller.data.entities.HindiMapping
import com.minhas.teller.data.entities.ImportJob
import com.minhas.teller.data.entities.ImportMapping
import com.minhas.teller.data.entities.EvidenceAttachment
import com.minhas.teller.data.dao.ImportJobDao
import com.minhas.teller.data.dao.ImportMappingDao
import com.minhas.teller.data.dao.EvidenceAttachmentDao
import com.minhas.teller.data.dao.ChatMessageDao

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Ledger::class, Voucher::class, VoucherEntry::class, StockItem::class, Unit::class, InventoryEntry::class, Company::class, AccountGroup::class, BillAllocation::class, AuditLog::class, TaxCategory::class, Godown::class, ChatMessageEntity::class, HsnEntry::class, Quotation::class, HindiMapping::class, ImportJob::class, ImportMapping::class, EvidenceAttachment::class], version = AppDatabase.DB_VERSION)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun voucherDao(): VoucherDao
    abstract fun voucherEntryDao(): VoucherEntryDao
    abstract fun stockItemDao(): StockItemDao
    abstract fun unitDao(): UnitDao
    abstract fun inventoryEntryDao(): InventoryEntryDao
    abstract fun companyDao(): CompanyDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun accountGroupDao(): AccountGroupDao
    abstract fun billAllocationDao(): BillAllocationDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun hsnDao(): com.minhas.teller.data.dao.HsnDao
    abstract fun quotationDao(): com.minhas.teller.data.dao.QuotationDao
    abstract fun hindiMappingDao(): com.minhas.teller.data.dao.HindiMappingDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun importMappingDao(): ImportMappingDao
    abstract fun evidenceAttachmentDao(): EvidenceAttachmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vouchers ADD COLUMN customField TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `action` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `details` TEXT NOT NULL, `companyId` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE companies ADD COLUMN backupEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledgers ADD COLUMN maintainBillWise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bill_allocations ADD COLUMN refName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE bill_allocations ADD COLUMN refType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE bill_allocations ADD COLUMN dueDate INTEGER DEFAULT NULL")
                
                db.execSQL("CREATE TABLE IF NOT EXISTS `bill_allocations_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `voucherEntryId` INTEGER NOT NULL, `refVoucherId` INTEGER, `refName` TEXT, `refType` TEXT, `dueDate` INTEGER, `amount` REAL NOT NULL)")
                db.execSQL("INSERT INTO bill_allocations_new (id, voucherEntryId, refVoucherId, amount) SELECT id, voucherEntryId, refVoucherId, amount FROM bill_allocations")
                db.execSQL("DROP TABLE bill_allocations")
                db.execSQL("ALTER TABLE bill_allocations_new RENAME TO bill_allocations")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vouchers ADD COLUMN chargeInterest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN interestRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN interestDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE companies ADD COLUMN logoUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE companies ADD COLUMN signatureUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE companies ADD COLUMN stampDesignName TEXT DEFAULT NULL")
            }
        }

        // Bridge migration: v13 -> v19 for users who were on intermediate builds
        // These versions had no schema changes that weren't captured by v19_20
        val MIGRATION_13_19 = object : Migration(13, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op: versions 14-18 had no schema changes beyond what is
                // already present. The next migration (19->20) adds HSN/tax columns.
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add HSN and Tax fields to stock_items table
                db.execSQL("ALTER TABLE stock_items ADD COLUMN hsnCode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE stock_items ADD COLUMN gstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE stock_items ADD COLUMN cgstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE stock_items ADD COLUMN sgstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE stock_items ADD COLUMN igstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE stock_items ADD COLUMN hsnAutoDetected INTEGER NOT NULL DEFAULT 0")
                
                // Add HSN and Tax fields to inventory_entries table
                db.execSQL("ALTER TABLE inventory_entries ADD COLUMN hsnCode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE inventory_entries ADD COLUMN gstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE inventory_entries ADD COLUMN cgstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE inventory_entries ADD COLUMN sgstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE inventory_entries ADD COLUMN igstRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE inventory_entries ADD COLUMN taxableAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE inventory_entries ADD COLUMN taxAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE inventory_entries ADD COLUMN totalAmount REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create quotations table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quotations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `quotationNumber` TEXT NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `customerPhone` TEXT,
                        `customerAddress` TEXT,
                        `quotationDate` INTEGER NOT NULL,
                        `expiryDate` INTEGER,
                        `items` TEXT NOT NULL,
                        `subTotal` REAL NOT NULL,
                        `taxAmount` REAL NOT NULL DEFAULT 0.0,
                        `discount` REAL NOT NULL DEFAULT 0.0,
                        `grandTotal` REAL NOT NULL,
                        `notes` TEXT,
                        `terms` TEXT,
                        `companyId` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op: schema was identical between v21 and v22;
                // structural changes began in v22->v23 (isFinanced, hindi_mappings)
            }
        }

        // Migration to add isFinanced column to vouchers
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vouchers ADD COLUMN isFinanced INTEGER NOT NULL DEFAULT 0")
                // Create Hindi mappings table for multilingual support
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `hindi_mappings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `hindiName` TEXT NOT NULL,
                        `hinglishName` TEXT NOT NULL,
                        `englishName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `hsnCode` TEXT,
                        `keywords` TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hindi_mappings_hinglishName` ON `hindi_mappings` (`hinglishName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hindi_mappings_englishName` ON `hindi_mappings` (`englishName`)")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `import_jobs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `companyId` INTEGER NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `rowCount` INTEGER NOT NULL DEFAULT 0, 
                        `errorDetails` TEXT, 
                        `summaryJson` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_jobs_companyId` ON `import_jobs` (`companyId`)")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `import_mappings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `importJobId` INTEGER NOT NULL, 
                        `sourceField` TEXT NOT NULL, 
                        `targetEntity` TEXT NOT NULL, 
                        `targetField` TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_mappings_importJobId` ON `import_mappings` (`importJobId`)")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `evidence_attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `voucherId` INTEGER NOT NULL, 
                        `companyId` INTEGER NOT NULL, 
                        `fileUri` TEXT NOT NULL, 
                        `ocrText` TEXT, 
                        `dateAdded` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_attachments_voucherId` ON `evidence_attachments` (`voucherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_attachments_companyId` ON `evidence_attachments` (`companyId`)")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add indices on foreign key columns for query performance
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vouchers_companyId` ON `vouchers` (`companyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vouchers_companyId_date` ON `vouchers` (`companyId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vouchers_type` ON `vouchers` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voucher_entries_voucherId` ON `voucher_entries` (`voucherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voucher_entries_ledgerId` ON `voucher_entries` (`ledgerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_allocations_voucherEntryId` ON `bill_allocations` (`voucherEntryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_allocations_refVoucherId` ON `bill_allocations` (`refVoucherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledgers_companyId` ON `ledgers` (`companyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledgers_companyId_group` ON `ledgers` (`companyId`, `group`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_entries_voucherId` ON `inventory_entries` (`voucherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_entries_stockItemId` ON `inventory_entries` (`stockItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_items_companyId` ON `stock_items` (`companyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_items_unitId` ON `stock_items` (`unitId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_companyId` ON `audit_logs` (`companyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_entityType_entityId` ON `audit_logs` (`entityType`, `entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_groups_companyId` ON `account_groups` (`companyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_godowns_companyId` ON `godowns` (`companyId`)")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE companies ADD COLUMN upiId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledgers ADD COLUMN phoneNumber TEXT DEFAULT NULL")
            }
        }

        /**
         * Data repair — no schema change.
         *
         * Editing a voucher used to rebuild its header without a companyId, and because
         * voucherDao.update writes every column, the voucher's companyId was overwritten
         * with the 0 default. The row was never deleted, but every read is scoped
         * "WHERE companyId = :companyId", so the voucher vanished from the day book,
         * the registers and all reports — indistinguishable from a delete.
         *
         * Re-home those vouchers using the ledgers their entries point at, which still
         * carry the correct owning company. Vouchers with no usable entry are recovered
         * only when the install has exactly one company, so we never guess.
         */
        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE vouchers SET companyId = (
                        SELECT l.companyId FROM voucher_entries ve
                        JOIN ledgers l ON l.id = ve.ledgerId
                        WHERE ve.voucherId = vouchers.id AND l.companyId != 0
                        LIMIT 1
                    )
                    WHERE companyId = 0 AND EXISTS (
                        SELECT 1 FROM voucher_entries ve
                        JOIN ledgers l ON l.id = ve.ledgerId
                        WHERE ve.voucherId = vouchers.id AND l.companyId != 0
                    )
                """.trimIndent())

                db.execSQL("""
                    UPDATE vouchers
                    SET companyId = (SELECT id FROM companies LIMIT 1)
                    WHERE companyId = 0 AND (SELECT COUNT(*) FROM companies) = 1
                """.trimIndent())
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledgers ADD COLUMN gstin TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE ledgers ADD COLUMN stateCode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN placeOfSupply TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN supplyType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN reverseCharge INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add cloud-sync columns (uid = stable cross-device key, updatedAt = last-writer-wins
                // clock, isDeleted = soft-delete tombstone). Existing rows are stamped "now" so the
                // first sync uploads everything.
                val now = System.currentTimeMillis()
                for (table in listOf("companies", "ledgers", "stock_items", "units", "vouchers")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN uid TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE $table ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE $table SET updatedAt = $now")
                }
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Money migration: monetary columns move from REAL rupees to INTEGER paise.
                // Rebuild each affected table so the stored values are multiplied by 100 (and
                // ROUNDed to avoid the 12.34 * 100 = 1233.999... float truncation).
                // Rates/quantities/percentages stay REAL and are untouched.

                // ledgers.openingBalance
                db.execSQL("CREATE TABLE IF NOT EXISTS `ledgers_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `alias` TEXT, `group` TEXT NOT NULL, `openingBalance` INTEGER NOT NULL, `maintainBillWise` INTEGER NOT NULL, `phoneNumber` TEXT, `companyId` INTEGER NOT NULL, `gstin` TEXT, `stateCode` TEXT, `uid` TEXT, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `ledgers_new` (`id`,`name`,`alias`,`group`,`openingBalance`,`maintainBillWise`,`phoneNumber`,`companyId`,`gstin`,`stateCode`,`uid`,`updatedAt`,`isDeleted`) SELECT `id`,`name`,`alias`,`group`,CAST(ROUND(`openingBalance` * 100) AS INTEGER),`maintainBillWise`,`phoneNumber`,`companyId`,`gstin`,`stateCode`,`uid`,`updatedAt`,`isDeleted` FROM `ledgers`")
                db.execSQL("DROP TABLE `ledgers`")
                db.execSQL("ALTER TABLE `ledgers_new` RENAME TO `ledgers`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledgers_companyId` ON `ledgers` (`companyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledgers_companyId_group` ON `ledgers` (`companyId`, `group`)")

                // voucher_entries.amount
                db.execSQL("CREATE TABLE IF NOT EXISTS `voucher_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `voucherId` INTEGER NOT NULL, `ledgerId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `isDebit` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `voucher_entries_new` (`id`,`voucherId`,`ledgerId`,`amount`,`isDebit`) SELECT `id`,`voucherId`,`ledgerId`,CAST(ROUND(`amount` * 100) AS INTEGER),`isDebit` FROM `voucher_entries`")
                db.execSQL("DROP TABLE `voucher_entries`")
                db.execSQL("ALTER TABLE `voucher_entries_new` RENAME TO `voucher_entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voucher_entries_voucherId` ON `voucher_entries` (`voucherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_voucher_entries_ledgerId` ON `voucher_entries` (`ledgerId`)")

                // inventory_entries.amount, taxableAmount, taxAmount, totalAmount
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `voucherId` INTEGER NOT NULL, `stockItemId` INTEGER NOT NULL, `quantity` REAL NOT NULL, `rate` REAL NOT NULL, `amount` INTEGER NOT NULL, `hsnCode` TEXT, `gstRate` REAL NOT NULL, `cgstRate` REAL NOT NULL, `sgstRate` REAL NOT NULL, `igstRate` REAL NOT NULL, `taxableAmount` INTEGER NOT NULL, `taxAmount` INTEGER NOT NULL, `totalAmount` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `inventory_entries_new` (`id`,`voucherId`,`stockItemId`,`quantity`,`rate`,`amount`,`hsnCode`,`gstRate`,`cgstRate`,`sgstRate`,`igstRate`,`taxableAmount`,`taxAmount`,`totalAmount`) SELECT `id`,`voucherId`,`stockItemId`,`quantity`,`rate`,CAST(ROUND(`amount` * 100) AS INTEGER),`hsnCode`,`gstRate`,`cgstRate`,`sgstRate`,`igstRate`,CAST(ROUND(`taxableAmount` * 100) AS INTEGER),CAST(ROUND(`taxAmount` * 100) AS INTEGER),CAST(ROUND(`totalAmount` * 100) AS INTEGER) FROM `inventory_entries`")
                db.execSQL("DROP TABLE `inventory_entries`")
                db.execSQL("ALTER TABLE `inventory_entries_new` RENAME TO `inventory_entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_entries_voucherId` ON `inventory_entries` (`voucherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_entries_stockItemId` ON `inventory_entries` (`stockItemId`)")

                // bill_allocations.amount
                db.execSQL("CREATE TABLE IF NOT EXISTS `bill_allocations_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `voucherEntryId` INTEGER NOT NULL, `refVoucherId` INTEGER, `refName` TEXT, `refType` TEXT, `dueDate` INTEGER, `amount` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `bill_allocations_new` (`id`,`voucherEntryId`,`refVoucherId`,`refName`,`refType`,`dueDate`,`amount`) SELECT `id`,`voucherEntryId`,`refVoucherId`,`refName`,`refType`,`dueDate`,CAST(ROUND(`amount` * 100) AS INTEGER) FROM `bill_allocations`")
                db.execSQL("DROP TABLE `bill_allocations`")
                db.execSQL("ALTER TABLE `bill_allocations_new` RENAME TO `bill_allocations`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_allocations_voucherEntryId` ON `bill_allocations` (`voucherEntryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_allocations_refVoucherId` ON `bill_allocations` (`refVoucherId`)")

                // quotations.subTotal, taxAmount, discount, grandTotal (items JSON is migrated on read by Converters)
                db.execSQL("CREATE TABLE IF NOT EXISTS `quotations_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `quotationNumber` TEXT NOT NULL, `customerName` TEXT NOT NULL, `customerPhone` TEXT, `customerAddress` TEXT, `quotationDate` INTEGER NOT NULL, `expiryDate` INTEGER, `items` TEXT NOT NULL, `subTotal` INTEGER NOT NULL, `taxAmount` INTEGER NOT NULL, `discount` INTEGER NOT NULL, `grandTotal` INTEGER NOT NULL, `notes` TEXT, `terms` TEXT, `companyId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `quotations_new` (`id`,`quotationNumber`,`customerName`,`customerPhone`,`customerAddress`,`quotationDate`,`expiryDate`,`items`,`subTotal`,`taxAmount`,`discount`,`grandTotal`,`notes`,`terms`,`companyId`,`createdAt`,`updatedAt`) SELECT `id`,`quotationNumber`,`customerName`,`customerPhone`,`customerAddress`,`quotationDate`,`expiryDate`,`items`,CAST(ROUND(`subTotal` * 100) AS INTEGER),CAST(ROUND(`taxAmount` * 100) AS INTEGER),CAST(ROUND(`discount` * 100) AS INTEGER),CAST(ROUND(`grandTotal` * 100) AS INTEGER),`notes`,`terms`,`companyId`,`createdAt`,`updatedAt` FROM `quotations`")
                db.execSQL("DROP TABLE `quotations`")
                db.execSQL("ALTER TABLE `quotations_new` RENAME TO `quotations`")
            }
        }

        /** Bump together with a new Migration whenever the schema changes. */
        const val DB_VERSION = 33
        const val DB_NAME = "teller_database"

        /** v32: vouchers.returnOfVoucherId links a Sales/Purchase Return to the original bill. */
        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable, no default - matches the entity field exactly, existing rows get NULL.
                db.execSQL("ALTER TABLE `vouchers` ADD COLUMN `returnOfVoucherId` INTEGER")
            }
        }

        /** v33: stock_items.barcode - scan a product's barcode at billing time to find it instantly. */
        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stock_items` ADD COLUMN `barcode` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_items_companyId_barcode` ON `stock_items` (`companyId`, `barcode`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33)
                // No destructive fallback on downgrade: an older APK installed over a newer one must
                // never wipe the user's books. MainActivity checks DatabaseVersionGuard.isDowngrade()
                // first and shows an "update the app" screen instead of opening the database.
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
