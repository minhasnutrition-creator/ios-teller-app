// Quick Bill - the simple PC billing screen: scan or search items, choose how the customer pays,
// save and print. No accounting words. Uses app.js's fetch() wrapper (pairing token, reconnect banner)
// and its globals (allStockItems, allLedgers, loadStockItems, loadLedgers, printVoucher, showToast).

let qbLines = [];        // { id, name, rate, gst, qty }
let qbPayment = 'cash';  // cash | upi | credit
let qbHi = 0;            // highlighted suggestion
let qbSuggestList = [];
let qbSaving = false;

function qbEsc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function qbMoney(n) {
    return '₹' + (Number(n) || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function qbNorm(s) { return String(s || '').toLowerCase().trim(); }
function qbCodes(item) { return String(item.alias || '').split(/[,;|]/).map(qbNorm).filter(Boolean); }

// ── opening the window ───────────────────────────────────────────
function qbOnOpen() {
    qbRender();
    qbSetPayment(qbPayment);
    Promise.all([loadStockItems(), loadLedgers()]).then(qbFillCustomers);
    qbLoadNextNumber();
    setTimeout(() => { const s = document.getElementById('qbSearch'); if (s) s.focus(); }, 60);
}

function qbFillCustomers() {
    const dl = document.getElementById('qbCustomerList');
    if (!dl) return;
    dl.innerHTML = allLedgers
        .filter(l => /debtor/i.test(l.group || ''))
        .map(l => '<option value="' + qbEsc(l.name) + '"></option>').join('');
}

async function qbLoadNextNumber() {
    const el = document.getElementById('qbNextNo');
    if (!el) return;
    try {
        const r = await (await fetch('/api/next-number')).json();
        el.textContent = r.voucherNumber ? 'Bill no. ' + r.voucherNumber : '';
    } catch (e) { /* the reconnect banner covers an unreachable phone */ }
}

// Used by the old Voucher Entry window too: replace its time-based number with the phone's real next one.
async function qbFillNextNumber(inputId) {
    const el = document.getElementById(inputId);
    if (!el) return;
    const before = el.value;
    try {
        const r = await (await fetch('/api/next-number')).json();
        if (r.voucherNumber && el.value === before) el.value = r.voucherNumber;
    } catch (e) {}
}

// ── item search (barcode scanner types the code, then Enter) ─────
function qbFind(q) {
    q = qbNorm(q);
    if (!q) return { exact: [], list: [] };
    const exact = [], starts = [], contains = [];
    allStockItems.forEach(it => {
        const name = qbNorm(it.name), codes = qbCodes(it);
        if (codes.indexOf(q) >= 0 || name === q) exact.push(it);
        else if (name.startsWith(q)) starts.push(it);
        else if (name.includes(q) || codes.some(c => c.includes(q))) contains.push(it);
    });
    return { exact: exact, list: exact.concat(starts, contains).slice(0, 8) };
}

function qbSearchInput() {
    const q = document.getElementById('qbSearch').value;
    qbSuggestList = qbFind(q).list;
    qbHi = 0;
    qbRenderSuggest();
}

function qbRenderSuggest() {
    const box = document.getElementById('qbSuggest');
    if (!qbSuggestList.length) { box.style.display = 'none'; box.innerHTML = ''; return; }
    box.innerHTML = qbSuggestList.map((it, i) =>
        '<div class="qb-sg-row' + (i === qbHi ? ' hi' : '') + '" onmousedown="qbPick(' + i + ')">' +
        '<span>' + qbEsc(it.name) + '</span><span class="qb-sg-price">' + qbMoney(it.rate) + '</span></div>'
    ).join('');
    box.style.display = 'block';
}

function qbPick(i) {
    const it = qbSuggestList[i];
    if (it) qbAdd(it);
}

function qbSearchKey(e) {
    const input = document.getElementById('qbSearch');
    if (e.key === 'ArrowDown' && qbSuggestList.length) {
        e.preventDefault(); qbHi = (qbHi + 1) % qbSuggestList.length; qbRenderSuggest();
    } else if (e.key === 'ArrowUp' && qbSuggestList.length) {
        e.preventDefault(); qbHi = (qbHi - 1 + qbSuggestList.length) % qbSuggestList.length; qbRenderSuggest();
    } else if (e.key === 'Enter') {
        e.preventDefault();
        const q = input.value;
        if (!qbNorm(q)) return;
        const r = qbFind(q);
        if (r.exact.length === 1) qbAdd(r.exact[0]);                       // a scanned barcode / exact name
        else if (r.list.length) qbAdd(r.list[Math.min(qbHi, r.list.length - 1)]);
        else { qbMessage('No item found for "' + q + '". Check the spelling or the barcode.'); }
    } else if (e.key === 'Escape' && (input.value || qbSuggestList.length)) {
        e.stopPropagation();                                                // clear the search, don't close the window
        qbClearSearch();
    }
}

function qbClearSearch() {
    const input = document.getElementById('qbSearch');
    input.value = '';
    qbSuggestList = [];
    qbRenderSuggest();
    input.focus();
}

// ── the bill ─────────────────────────────────────────────────────
function qbAdd(item) {
    qbMessage('');
    const ex = qbLines.find(l => l.id === item.id);
    if (ex) ex.qty = (Number(ex.qty) || 0) + 1;
    else qbLines.push({ id: item.id, name: item.name, rate: Number(item.rate) || 0, gst: Number(item.gstRate) || 0, qty: 1 });
    qbClearSearch();
    qbRender();
}

function qbRemove(i) {
    qbLines.splice(i, 1);
    qbRender();
}

function qbEdit(i, field, value) {
    const l = qbLines[i];
    if (!l) return;
    l[field] = parseFloat(value) || 0;
    const cell = document.getElementById('qbAmt' + i);
    if (cell) cell.textContent = qbMoney(l.qty * l.rate);
    qbUpdateTotal();
}

function qbTotal() {
    return qbLines.reduce((s, l) => s + (Number(l.qty) || 0) * (Number(l.rate) || 0), 0);
}

function qbUpdateTotal() {
    document.getElementById('qbTotal').textContent = qbMoney(qbTotal());
}

function qbRender() {
    const body = document.getElementById('qbBody');
    if (!body) return;
    if (!qbLines.length) {
        body.innerHTML = '<tr><td colspan="5" class="qb-empty">Scan a barcode or type an item name above to start the bill.</td></tr>';
    } else {
        body.innerHTML = qbLines.map((l, i) =>
            '<tr>' +
            '<td>' + qbEsc(l.name) + (l.gst ? '<span class="qb-gst">GST ' + l.gst + '%</span>' : '') + '</td>' +
            '<td><input type="number" class="dex-input mono qb-num" min="0" step="any" value="' + l.qty + '" oninput="qbEdit(' + i + ',\'qty\',this.value)"></td>' +
            '<td><input type="number" class="dex-input mono qb-num" min="0" step="any" value="' + l.rate + '" oninput="qbEdit(' + i + ',\'rate\',this.value)"></td>' +
            '<td class="tar mono" id="qbAmt' + i + '" style="font-weight:700;">' + qbMoney(l.qty * l.rate) + '</td>' +
            '<td style="text-align:center;"><button class="win-btn close" style="width:22px;height:22px;" onclick="qbRemove(' + i + ')">✕</button></td>' +
            '</tr>'
        ).join('');
    }
    qbUpdateTotal();
}

function qbSetPayment(mode) {
    qbPayment = mode;
    [['cash', 'qbPayCash'], ['upi', 'qbPayUpi'], ['credit', 'qbPayCredit']].forEach(p => {
        const b = document.getElementById(p[1]);
        if (b) b.classList.toggle('active', p[0] === mode);
    });
    const hint = {
        cash: '',
        upi: 'The money goes to your bank / UPI account.',
        credit: 'Customer name is needed. The amount will show under Outstanding for this customer.'
    }[mode] || '';
    const h = document.getElementById('qbPayHint');
    if (h) h.textContent = hint;
}

function qbMessage(text) {
    const m = document.getElementById('qbMsg');
    if (m) m.textContent = text || '';
}

function qbReset() {
    qbLines = [];
    document.getElementById('qbCustomer').value = '';
    qbSetPayment('cash');
    qbMessage('');
    qbClearSearch();
    qbRender();
    qbLoadNextNumber();
}

async function qbSave(print) {
    if (qbSaving) return;
    qbMessage('');
    if (!qbLines.length) return qbMessage('Add at least one item first.');
    for (const l of qbLines) {
        if (!(l.qty > 0)) return qbMessage('Check the quantity of ' + l.name + '.');
        if (!(l.rate >= 0)) return qbMessage('Check the price of ' + l.name + '.');
    }
    const total = qbTotal();
    if (!(total > 0)) return qbMessage('Enter a price: the bill total is zero.');
    const customer = document.getElementById('qbCustomer').value.trim();
    if (qbPayment === 'credit' && !customer) {
        document.getElementById('qbCustomer').focus();
        return qbMessage("Type the customer's name for a pay-later bill.");
    }

    // Open the print tab now, inside the click/keypress, so the browser's pop-up blocker allows it.
    const w = print ? window.open('', '_blank') : null;
    qbSaving = true;
    document.getElementById('qbSavePrint').disabled = true;
    document.getElementById('qbSaveOnly').disabled = true;
    try {
        const res = await fetch('/api/quickbill/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                customerName: customer,
                payment: qbPayment,
                items: qbLines.map(l => ({ stockItemId: l.id, quantity: l.qty, rate: l.rate }))
            })
        });
        const r = await res.json();
        if (r.success) {
            showToast('✓ Bill ' + r.voucherNumber + ' saved - ' + qbMoney(total));
            if (print) printVoucher(r.voucherId, w);
            qbReset();
            loadDashboard(); loadStockItems(); loadLedgers();
        } else {
            if (w) w.close();
            qbMessage(r.error || 'The phone could not save this bill.');
        }
    } catch (err) {
        if (w) w.close();
        qbMessage("Could not reach the phone. Your bill is still here: try again when it is back.");
    } finally {
        qbSaving = false;
        document.getElementById('qbSavePrint').disabled = false;
        document.getElementById('qbSaveOnly').disabled = false;
    }
}

window.addEventListener('keydown', (e) => {
    if (e.key === 'F2') {
        e.preventDefault();
        openWindow('win-quickbill');
    } else if (e.ctrlKey && e.key.toLowerCase() === 's') {
        const w = document.getElementById('win-quickbill');
        if (w && w.style.display !== 'none') { e.preventDefault(); qbSave(true); }
    }
});
