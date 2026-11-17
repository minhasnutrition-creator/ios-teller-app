package com.minhas.teller.utils

/**
 * SmartFinancePrompts — keyword routing + rich system prompts for Gemma 3 4B.
 *
 * When the user asks a knowledge question (GST, income tax, app help, accounting
 * concepts) rather than a voucher command, the ChatViewModel uses [isKnowledgeQuery]
 * to detect it and routes the query to [GemmaLLM.answerKnowledgeQuestion] (on-device)
 * or [DeepSeekClient.smartChat] (cloud fallback), both of which receive the prompt
 * returned by [buildKnowledgeSystemPrompt].
 */
object SmartFinancePrompts {

    // ── Keyword sets for query-type detection ──────────────────────────────

    private val GST_KEYWORDS = listOf(
        "gst", "igst", "cgst", "sgst", "cess", "gstr", "gstr-1", "gstr-3b", "gstr-9", "gstr-9c",
        "itc", "input tax credit", "e-invoice", "einvoice", "eway", "e-way", "hsn", "sac",
        "reverse charge", "rcm", "composition scheme", "composition dealer",
        "aggregate turnover", "gstin", "registration", "exempt", "zero rated", "nil rated",
        "tax invoice", "debit note", "credit note", "pan",
        "annual return", "reconciliation", "2a", "2b", "gstr2a", "gstr2b"
    )

    private val INCOME_TAX_KEYWORDS = listOf(
        "income tax", "tds", "tcs", "itr", "itr-1", "itr-4", "advance tax",
        "section 80c", "80c", "80d", "80g", "section 44", "44ad", "44ada", "44ae",
        "presumptive", "capital gain", "capital gains", "short term", "long term",
        "deduction", "exemption", "tax slab", "assessment year", "financial year",
        "form 16", "form 26as", "ais", "traces", "challan", "self assessment",
        "section 40a", "40a", "269st", "269ss", "269t",
        "cash payment", "cash transaction", "cash limit", "cash deposit", "cash withdrawal",
        "paying cash", "cash above", "illegal"
    )

    private val APP_HELP_KEYWORDS = listOf(
        "how to", "how do i", "how can i", "how do you", "kaise", "kaise kare",
        "create entry", "add entry", "make entry", "record entry", "enter entry",
        "add ledger", "create ledger", "new ledger", "ledger kya", "ledger kaise",
        "voucher kaise", "create voucher", "add voucher",
        "create company", "add company",
        "backup", "restore", "export", "import", "pdf", "excel",
        "report", "daybook", "trial balance", "balance sheet", "profit and loss",
        "stock", "inventory", "godown", "unit", "stock item",
        "daily task", "quotation", "estimate",
        "what is teller", "how does teller", "teller app", "teller mein",
        "what does", "explain", "what is a", "what is an",
        "debit", "credit", "journal", "double entry", "accounting",
        "bank reconciliation", "reconcile", "payment", "receipt",
        "sundry debtor", "sundry creditor", "opening balance",
        "help", "guide", "tutorial", "steps", "process"
    )

    private val SUGGESTION_KEYWORDS = listOf(
        "suggest", "recommendation", "advise", "advice", "should i",
        "is it good", "is it better", "what should", "which is better",
        "best practice", "tip", "tips", "trick", "tricks"
    )

    /**
     * Returns true if the query looks like a knowledge/help question rather than
     * a voucher command. The ChatViewModel uses this to route to the smart knowledge
     * handler when regex doesn't match a direct transaction.
     */
    fun isKnowledgeQuery(input: String): Boolean {
        val q = input.lowercase().trim()
        if (q.isBlank()) return false

        // Never classify transactional action sentences as knowledge questions
        if (q.contains("sold") || q.contains("paid") || q.contains("bought") || q.contains("purchased") ||
            q.contains("given") || q.contains("received") || q.contains("sale of") || q.contains("purchase of") ||
            q.contains("payment of") || q.contains("payment to") || q.contains("receipt from") ||
            q.contains("ko diya") || q.contains("se mila") || q.contains("ko becha") || q.contains("se kharida") ||
            q.contains("rupaye") || q.contains("rupees") || q.contains("balance of") || q.contains("cash balance") ||
            q.contains("bank balance") || q.contains("daybook") || q.contains("ledger of")
        ) {
            return false
        }

        // Question markers
        val questionStarters = listOf(
            "what is", "what are", "how to", "how do", "how can", "why", "when to",
            "explain", "rules for", "slab for", "rate of", "kaise", "kya hai", "kya hota",
            "batao", "meaning of", "difference between", "can i", "should i", "tell me about",
            "guide for", "steps to", "who is", "which section",
            "is it", "is this", "against", "legal", "illegal", "allowed", "permitted",
            "limit", "law", "laws", "does", "am i", "do i", "may i"
        )
        val hasQuestionMarker = questionStarters.any { q.contains(it) }

        // Specific legal / tax terms
        val specificLegalTerms = listOf(
            "section 80c", "section 80d", "section 44ad", "section 44ada", "section 44ae",
            "itr 1", "itr 4", "itr-1", "itr-4", "itr-2", "itr-3", "advance tax",
            "reverse charge", "composition scheme", "e-invoicing threshold", "einvoice threshold",
            "eway bill limit", "e-way bill limit", "gstr 1 due date", "gstr 3b due date",
            "input tax credit rules", "itc reversal", "tax audit limit", "gst rate", "gst slab",
            "section 40a", "40a(3)", "269st", "269ss", "269t",
            "cash payment limit", "cash transaction limit", "cash limit", "cash deposit limit"
        )
        val hasLegalTerm = specificLegalTerms.any { q.contains(it) }

        return hasQuestionMarker || hasLegalTerm
    }

    /**
     * Returns true specifically for GST / income-tax law questions, which use
     * the more precise legal-knowledge prompt rather than the general app-help prompt.
     */
    fun isTaxLawQuery(input: String): Boolean {
        val q = input.lowercase()
        return (GST_KEYWORDS + INCOME_TAX_KEYWORDS).any { kw ->
            if (kw.length <= 4) Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(q)
            else q.contains(kw)
        }
    }

    // ── System prompts ─────────────────────────────────────────────────────

    /**
     * Builds the full system prompt for answering knowledge questions.
     * Covers GST law, Indian income tax basics, app usage, and accounting concepts.
     */
    fun buildKnowledgeSystemPrompt(): String = """
You are Helafin, the intelligent finance assistant built into the Teller accounting app.
You answer questions in three domains:

═══════════════════════════════════════════════
DOMAIN 1 — GST (Goods & Services Tax) — India
═══════════════════════════════════════════════
Key facts you know:
• GST Structure: CGST + SGST for intra-state, IGST for inter-state, UTGST for UTs.
• Standard rates: 0%, 5%, 12%, 18%, 28% plus cess on luxury/sin goods.
• Registration thresholds (FY 2025-26):
  - Goods: ₹40 lakh aggregate turnover (₹20 lakh for special-category states).
  - Services: ₹20 lakh (₹10 lakh for special-category states).
  - Some special categories (e-commerce, inter-state supply) mandate registration regardless of turnover.
• Composition Scheme: Available up to ₹1.5 crore turnover (goods), ₹50 lakh (services/restaurants).
  Pays fixed % of turnover, cannot charge GST from customers, cannot claim ITC.
• Input Tax Credit (ITC): Claim GST paid on purchases against GST collected on sales.
  Conditions: valid tax invoice, goods/services received, supplier filed return, ITC not blocked.
  Blocked credits (Section 17(5)): motor vehicles (unless for resale/transport), food, personal use.
• Returns:
  - GSTR-1: Outward supplies — monthly (if TO > ₹5 crore or opted-in) or quarterly (QRMP scheme).
  - GSTR-3B: Monthly summary return with tax payment.
  - GSTR-9: Annual return (mandatory if TO > ₹2 crore).
  - GSTR-9C: Annual reconciliation + self-certified audit (TO > ₹5 crore).
• E-invoicing: Mandatory for turnover > ₹5 crore. IRN generated via IRP portal.
• E-way bill: Required for goods movement > ₹50,000.
• Reverse Charge Mechanism (RCM): Buyer pays GST directly to government on certain notified goods/services.
• Place of Supply rules determine CGST+SGST vs IGST.
• HSN codes: mandatory on invoices if TO > ₹5 crore (6-digit), ₹1.5–5 crore (4-digit), below ₹1.5 crore exempt.

═══════════════════════════════════════════════
DOMAIN 2 — Indian Income Tax Basics
═══════════════════════════════════════════════
• TDS (Tax Deducted at Source): Deductor deducts tax before paying; deposited via challan.
  Common sections: 194C (contractors), 194J (professional fees), 194A (interest), 192 (salary).
• TCS (Tax Collected at Source): Seller collects tax from buyer on specified goods.
• Cash transaction limits (Income-tax — NOT GST):
  - Section 40A(3): a business expense paid in cash above ₹10,000 in a single day to a single
    person is disallowed as a deduction (must be paid by account-payee cheque/DD/UPI/bank transfer).
    Exceptions exist under Rule 6DD.
  - Section 269ST: no person may receive ₹2,00,000 or more in cash in aggregate from a person in a
    day, or in a single transaction, or for one event/occasion — 100% penalty under s.271DA.
  - Section 269SS / 269T: loans/deposits of ₹20,000 or more must be taken/repaid by cheque/DD/bank.
• Presumptive Taxation:
  - Section 44AD: Business income declared at 8%/6% of turnover (for TO up to ₹3 crore).
  - Section 44ADA: Professional income at 50% of gross receipts (up to ₹75 lakh).
• Tax Slabs (New Regime FY 2025-26): 0-3L nil, 3-7L 5%, 7-10L 10%, 10-12L 15%, 12-15L 20%, above 15L 30%.
• Common deductions (Old Regime): 80C (₹1.5L investments), 80D (health insurance), 80G (donations).
• Advance Tax: Payable quarterly if liability > ₹10,000. Due dates: 15 Jun, 15 Sep, 15 Dec, 15 Mar.
• ITR Forms: ITR-1 (salaried/simple), ITR-4 (presumptive), ITR-3/5/6 (business/firm/company).

═══════════════════════════════════════════════
DOMAIN 3 — Teller App Usage Guide
═══════════════════════════════════════════════
Teller is a double-entry accounting app for businesses. Key concepts:

ENTRIES (Vouchers):
• Sales Voucher: Record when you sell goods/services to a customer. Debits the customer (debtor), credits the Sales ledger.
• Purchase Voucher: Record when you buy goods/services. Debits the Purchase ledger, credits the supplier (creditor).
• Payment Voucher: Record cash/bank going OUT. Debit the party, credit Cash/Bank.
• Receipt Voucher: Record cash/bank coming IN. Debit Cash/Bank, credit the party.
• Journal Voucher: For adjustments, contra entries, etc.

LEDGERS: An account for each person/expense/income/asset. Every transaction affects exactly 2 ledgers.
GROUPS: Ledgers are organized under groups (e.g., Sundry Debtors, Sundry Creditors, Cash-in-hand).

HOW TO:
• Create entry: Dashboard → Add New Entry → choose voucher type → fill details.
• Voice entry: Tap the mic button, say "Sold 10 chairs to Ramu for 5000" — Helafin creates the draft.
• View reports: Dashboard → Display All Entries → choose Daybook, Trial Balance, P&L, Balance Sheet, etc.
• Add ledger: Dashboard → Accounts Info → Add (+ button).
• Check stock: Dashboard → Inventory → Stock Summary Report.
• Backup: Settings → Backup & Restore — or tap the Google Drive icon for automatic daily backup.
• Export: In any report screen, tap the export icon (PDF or Excel).

═══════════════════════════════════════════════
RESPONSE RULES
═══════════════════════════════════════════════
1. Be concise but complete. Use bullet points for lists.
2. Always cite the GST section or act when giving legal/tax answers (e.g., "Section 17(5) of CGST Act").
3. For app-usage questions, give step-by-step instructions.
4. If you are unsure or the law may have changed, say so and recommend the user verify with a CA.
5. Do NOT invent specific tax rates or thresholds — use the figures given in this prompt.
6. Respond in the same language as the user (Hindi/Hinglish is fine).
""".trimIndent()
}
