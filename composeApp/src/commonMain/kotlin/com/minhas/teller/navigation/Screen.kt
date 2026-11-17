package com.minhas.teller.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object LedgerList : Screen("ledger_list")
    object AddEditLedger : Screen("add_edit_ledger/{ledgerId}")
    object VoucherList : Screen("voucher_list")
    object AddEditVoucher : Screen("add_edit_voucher")
    object QuotationList : Screen("quotation_list")
    object CreateQuotation : Screen("create_quotation")
    object EditQuotation : Screen("edit_quotation/{quotationId}")
    object Reports : Screen("reports")
    object LedgerViewReport : Screen("ledger_view_report/{ledgerId}")
    object TrialBalanceReport : Screen("trial_balance_report")
    object StockItemList : Screen("stock_item_list")
    object AddEditStockItem : Screen("add_edit_stock_item/{stockItemId}")
    object UnitList : Screen("unit_list")
    object AddEditUnit : Screen("add_edit_unit/{unitId}")
    object ProfitAndLossReport : Screen("profit_and_loss_report")
    object ItemProfitability : Screen("item_profitability")
    object BalanceSheetReport : Screen("balance_sheet_report")
    object StockSummaryReport : Screen("stock_summary_report")
    object GroupReport : Screen("group_report/{groupName}")
    object PendingPaymentsMenu : Screen("pending_payments_menu")
    object OutstandingPaymentsSummary : Screen("outstanding_payments_summary?group={group}")
    object DisplayOptions : Screen("display_options")
    object AccountBooks : Screen("account_books")
    object GroupSummary : Screen("group_summary")
    object CashBankBooks : Screen("cash_bank_books")
    object JournalRegister : Screen("journal_register")
    object SalesRegister : Screen("sales_register")
    object PurchaseRegister : Screen("purchase_register")
    object ListOfAccounts : Screen("list_of_accounts")
    object Daybook : Screen("daybook")
    object StatementOfAccounts : Screen("statement_of_accounts")
    object InventoryBooks : Screen("inventory_books")
    object ReceiptsAndPayments : Screen("receipts_and_payments")
    object CompanyList : Screen("company_list")
    object CreateCompany : Screen("create_company")
    object EditCompany : Screen("edit_company")
    object AccountsInfo : Screen("accounts_info")
    object InventoryInfo : Screen("inventory_info")
    object GroupList : Screen("group_list")
    object AddEditGroup : Screen("add_edit_group")
    object VoucherView : Screen("voucher_view/{voucherId}")
    object About : Screen("about")
    object LiveStockManagement : Screen("live_stock_management")
    object DailyTasks : Screen("daily_tasks")
    object DailyTaskEmployeeDetail : Screen("daily_task_detail/{employeeCode}")
    object MonthlySalesReport : Screen("monthly_sales_report/{showroomCode}")
    object License : Screen("license?reason={reason}")
    object Settings : Screen("settings")
    object Chat : Screen("chat") {
        const val routeTemplate = "chat?initialQuery={initialQuery}&autoVoice={autoVoice}"
        fun createRoute(initialQuery: String? = null, autoVoice: Boolean = false): String {
            val parts = mutableListOf<String>()
            if (!initialQuery.isNullOrBlank()) {
                parts.add("initialQuery=" + java.net.URLEncoder.encode(initialQuery, "UTF-8"))
            }
            if (autoVoice) {
                parts.add("autoVoice=true")
            }
            return if (parts.isNotEmpty()) "chat?" + parts.joinToString("&") else "chat"
        }
    }
    object Help : Screen("help")
    object HelpDetail : Screen("help_detail/{topicId}")
    object AuditLog : Screen("audit_log")
    object BankReconciliation : Screen("bank_reconciliation")
    object EntrySelection : Screen("entry_selection")
    object AiStatus : Screen("ai_status")
    object WhyUpgrade : Screen("why_upgrade")
    object TodayInvoices : Screen("today_invoices")
    object TodayInvoicesByDate : Screen("today_invoices_by_date?selectedDateMillis={selectedDateMillis}")
    object InvoiceTemplates : Screen("invoice_templates")
    object PdfViewer : Screen("pdf_viewer?filePath={filePath}&title={title}&voucherId={voucherId}")
    object ExcelViewer : Screen("excel_viewer?filePath={filePath}&title={title}")
    object SignatureDrawing : Screen("signature_drawing")
    object Splash : Screen("splash")
    object OutstandingBillDetail : Screen("outstanding_bill_detail/{voucherId}/{ledgerId}")
    object TellerScore : Screen("teller_score")
    object AiAuditor : Screen("ai_auditor")
    object ImportCleanup : Screen("import_cleanup")
    object CaScrutiny : Screen("ca_scrutiny")
    object InstallGuide : Screen("install_guide")
    object WarningDetail : Screen("warning_detail/{warningId}")
    object GstReport : Screen("gst_report")
    object GstReturns : Screen("gst_returns")
    object GstFilingWizard : Screen("gst_filing_wizard/{companyId}?fp={fp}") {
        fun createRoute(companyId: Int, fp: String): String = "gst_filing_wizard/$companyId?fp=$fp"
    }
    object GstPortalWebView : Screen("gst_portal_webview?url={url}&title={title}") {
        fun createRoute(url: String, title: String): String =
            "gst_portal_webview?url=${java.net.URLEncoder.encode(url, "UTF-8")}&title=${java.net.URLEncoder.encode(title, "UTF-8")}"
    }
    object MonthlyInventory : Screen("monthly_inventory")
    object DesktopCompanion : Screen("desktop_companion")
    object CloudSync : Screen("cloud_sync")
    object ThermalPrinter : Screen("thermal_printer?voucherId={voucherId}") {
        fun createRoute(voucherId: Int? = null): String =
            if (voucherId != null) "thermal_printer?voucherId=$voucherId" else "thermal_printer"
    }
    object BarcodeGenerator : Screen("barcode_generator")
    object Returns : Screen("returns")
    object Gstr2bReconciler : Screen("gstr2b_reconciler")
    object Utilities : Screen("utilities")
    object QrScanner : Screen("qr_scanner")
    object BarcodeScanner : Screen("barcode_scanner")
    object PortalAutopilot : Screen("portal_autopilot")
    object EInvoice : Screen("einvoice/{voucherId}") {
        fun createRoute(voucherId: Int) = "einvoice/$voucherId"
    }
}

