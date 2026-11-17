// The pairing token arrives once on the page URL (?token=...). We keep it in this browser's
// localStorage (so reloads, the friendly .local URL and /print-station keep working), then strip it
// from the address bar so it doesn't linger in history, bookmarks or screenshots. The phone only
// accepts it via the Authorization header.
const AUTH_TOKEN = (function () {
    const fromUrl = new URLSearchParams(window.location.search).get('token');
    if (fromUrl) {
        try { localStorage.setItem('teller_token', fromUrl); } catch (e) {}
        history.replaceState(null, '', window.location.pathname + window.location.hash);
        return fromUrl;
    }
    try { return localStorage.getItem('teller_token') || ''; } catch (e) { return ''; }
})();
const _origFetch = window.fetch;
window.fetch = function(url, opts) {
    opts = opts || {};
    opts.headers = Object.assign({}, opts.headers, { 'Authorization': 'Bearer ' + AUTH_TOKEN });
    return _origFetch(url, opts).then(function (res) {
        if (res.status === 401) {
            // Not paired yet, or "Disconnect all computers" was pressed on the phone: ask the phone for approval.
            try { localStorage.removeItem('teller_token'); } catch (e) {}
            showPairOverlay();
        } else {
            markConnected();
        }
        return res;
    }, function (err) {
        markDisconnected();
        throw err;
    });
};

// ── "Can't reach your phone" banner ──────────────────────────────
let _connLost = false;

function markDisconnected() {
    if (_connLost) return;
    _connLost = true;
    const b = document.getElementById('conn-banner');
    if (b) b.style.display = 'flex';
}

function markConnected() {
    if (!_connLost) return;
    _connLost = false;
    const b = document.getElementById('conn-banner');
    if (b) b.style.display = 'none';
    // The phone is back: refresh what is on screen. (Nothing the user typed is lost; the page stayed open.)
    loadCompanies(); loadDashboard(); loadLedgers(); loadStockItems();
    showToast('Reconnected to your phone');
}

// ── Approve-on-phone pairing ─────────────────────────────────────
// No token yet (or it was revoked): ask the phone. The phone shows an Allow/Deny dialog with the same
// 4-digit code; on Allow we receive the token once, store it and reload.
let _pairPoll = null;
let _pairShown = false;

function showPairOverlay() {
    const ov = document.getElementById('pair-overlay');
    if (!ov || _pairShown) return;
    _pairShown = true;
    ov.style.display = 'flex';
    startPairing();
}

async function startPairing() {
    clearInterval(_pairPoll);
    const codeEl = document.getElementById('pair-code');
    const msgEl = document.getElementById('pair-msg');
    const retry = document.getElementById('pair-retry');
    retry.style.display = 'none';
    codeEl.textContent = '····';
    msgEl.textContent = 'Contacting your phone…';
    const fail = (text) => {
        clearInterval(_pairPoll);
        msgEl.textContent = text;
        retry.style.display = 'inline-block';
    };
    let req;
    try {
        const res = await _origFetch('/api/pair/request', { method: 'POST' });
        if (res.status === 429) return fail('Too many tries. Wait a minute, then press Try again.');
        req = await res.json();
        if (!req || !req.id) throw new Error('bad response');
    } catch (e) {
        return fail("Can't reach your phone. Open Teller on the phone, switch on PC Desktop Companion, and make sure it is on the same Wi-Fi as this computer.");
    }
    codeEl.textContent = req.code;
    msgEl.textContent = 'Your phone will ask "Allow this computer?". Check that the number matches, then tap Allow.';
    const started = Date.now();
    _pairPoll = setInterval(async () => {
        if (Date.now() - started > 120000) return fail('This code expired. Press Try again for a new one.');
        try {
            const res = await _origFetch('/api/pair/status?id=' + encodeURIComponent(req.id));
            const data = await res.json();
            if (data.status === 'approved' && data.token) {
                clearInterval(_pairPoll);
                let saved = false;
                try { localStorage.setItem('teller_token', data.token); saved = localStorage.getItem('teller_token') === data.token; } catch (e) {}
                if (!saved) return fail('Your browser blocked saving the connection. Allow site data for this page, then try again.');
                msgEl.textContent = 'Connected! Opening Teller…';
                location.reload();
            } else if (data.status === 'denied') {
                fail('The phone said No. Press Try again if that was a mistake.');
            } else if (data.status === 'expired') {
                fail('This code expired. Press Try again for a new one.');
            }
        } catch (e) { /* phone briefly unreachable - keep trying until the code expires */ }
    }, 1500);
}

// Window Registry & State
let openWindows = new Set(['win-dashboard']);
let highestZ = 100;
let allLedgers = [];
let allStockItems = [];
let allDaybookData = [];
let allRegisterData = [];
let allUnits = [];
let selectedContextVoucher = null;

// ── Sidebar navigation (software app shell) ──────────────────────
const SIDEBAR_ITEMS = [
    { section: 'Overview' },
    { id: 'win-dashboard', icon: '📊', label: 'Dashboard' },
    { id: 'win-daybook', icon: '📑', label: 'Daybook' },
    { section: 'Vouchers' },
    { id: 'win-quickbill', icon: '🛒', label: 'Quick Bill' },
    { id: 'win-voucher', icon: '⚡', label: 'New Voucher', action: 'voucher' },
    { section: 'Accounts' },
    { id: 'win-ledgers', icon: '👥', label: 'Ledgers' },
    { id: 'win-ledger-statement', icon: '📄', label: 'Ledger Statement' },
    { id: 'win-outstanding', icon: '⏳', label: 'Outstanding' },
    { section: 'Inventory' },
    { id: 'win-stock', icon: '📦', label: 'Stock Items' },
    { id: 'win-stock-summary', icon: '📦', label: 'Stock Summary' },
    { section: 'Reports' },
    { id: 'win-register', icon: '📒', label: 'Registers' },
    { id: 'win-trial-balance', icon: '⚖️', label: 'Trial Balance' },
    { id: 'win-profit-loss', icon: '📈', label: 'Profit & Loss' },
    { id: 'win-balance-sheet', icon: '🏛️', label: 'Balance Sheet' },
    { id: 'win-gst-report', icon: '🧾', label: 'GST Report' },
];

function buildSidebar() {
    const nav = document.getElementById('sidebar-nav');
    if (!nav) return;
    nav.innerHTML = '';
    SIDEBAR_ITEMS.forEach(item => {
        if (item.section) {
            const sec = document.createElement('div');
            sec.className = 'sidebar-section';
            sec.textContent = item.section;
            nav.appendChild(sec);
        } else {
            const it = document.createElement('div');
            it.className = 'sidebar-item';
            it.id = 'nav-' + item.id;
            it.innerHTML = '<span class="ic">' + item.icon + '</span><span>' + item.label + '</span>';
            it.onclick = function () {
                if (item.action === 'voucher') openNewVoucherWindow('Sales');
                else openWindow(item.id);
            };
            nav.appendChild(it);
        }
    });
    updateSidebarActive('win-dashboard');
}

function updateSidebarActive(winId) {
    document.querySelectorAll('#sidebar-nav .sidebar-item').forEach(el => el.classList.remove('active'));
    const el = document.getElementById('nav-' + winId);
    if (el) el.classList.add('active');
}
buildSidebar();

// Clock Update
function updateClock() {
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const dateStr = now.toLocaleDateString([], { month: 'short', day: 'numeric', weekday: 'short' });
    document.getElementById('dexClockTime').textContent = timeStr;
    document.getElementById('dexClockDate').textContent = dateStr;
}
setInterval(updateClock, 1000);
updateClock();

// Window Management
function openWindow(winId) {
    const el = document.getElementById(winId);
    if (!el) return;
    // Single-page navigation: hide all windows, show only the selected one.
    document.querySelectorAll('.dex-window').forEach(w => w.style.display = 'none');
    el.style.display = 'flex';
    el.classList.add('active');
    openWindows = new Set([winId]);
    updateSidebarActive(winId);

    if (winId === 'win-dashboard') loadDashboard();
    if (winId === 'win-daybook') loadDaybook();
    if (winId === 'win-ledgers') loadLedgers();
    if (winId === 'win-stock') loadStockItems();
    if (winId === 'win-trial-balance') loadTrialBalance();
    if (winId === 'win-register') loadRegister();
    if (winId === 'win-ledger-statement') loadLedgerStatement();
    if (winId === 'win-outstanding') loadOutstanding();
    if (winId === 'win-stock-summary') loadStockSummary();
    if (winId === 'win-profit-loss') loadProfitLoss();
    if (winId === 'win-balance-sheet') loadBalanceSheet();
    if (winId === 'win-gst-report') loadGstReport();
    if (winId === 'win-quickbill' && typeof qbOnOpen === 'function') qbOnOpen();
}

function closeWindow(winId) {
    const el = document.getElementById(winId);
    if (!el) return;
    el.style.display = 'none';
    openWindows.delete(winId);
    renderTaskbarWindows();
}

function minimizeWindow(winId) {
    const el = document.getElementById(winId);
    if (!el) return;
    el.classList.add('minimized');
    renderTaskbarWindows();
}

function toggleMaximize(winId) {
    const el = document.getElementById(winId);
    if (!el) return;
    el.classList.toggle('maximized');
    bringToFront(el);
}

function bringToFront(el) {
    highestZ += 2;
    el.style.zIndex = highestZ;
    document.querySelectorAll('.dex-window').forEach(w => w.classList.remove('active'));
    el.classList.add('active');
    renderTaskbarWindows();
}

function renderTaskbarWindows() {
    const container = document.getElementById('taskbarWindowContainer');
    container.innerHTML = '';
    const names = {
        'win-dashboard': '📊 Dashboard',
        'win-daybook': '📑 Daybook',
        'win-voucher': '⚡ Voucher Entry',
        'win-ledgers': '👥 Ledgers',
        'win-stock': '📦 Stock Items',
        'win-trial-balance': '⚖️ Trial Balance',
        'win-register': '📒 Registers',
        'win-ledger-statement': '📄 Ledger Statement',
        'win-outstanding': '⏳ Outstanding',
        'win-stock-summary': '📦 Stock Summary',
        'win-profit-loss': '📈 Profit & Loss',
        'win-balance-sheet': '🏛️ Balance Sheet',
        'win-ledger-form': '👤 Ledger',
        'win-stock-form': '📦 Item',
        'win-unit-form': '📏 Unit',
        'win-quickbill': '🛒 Quick Bill',
        'win-voucher-view': '🧾 Voucher',
        'win-gst-report': '🧾 GST Report'
    };
    openWindows.forEach(id => {
        const w = document.getElementById(id);
        if (!w) return;
        const isMin = w.classList.contains('minimized');
        const isAct = w.classList.contains('active') && !isMin;
        const chip = document.createElement('div');
        chip.className = 'taskbar-window-chip' + (isAct ? ' active' : '');
        chip.textContent = names[id] || id;
        chip.onclick = () => {
            if (isMin) {
                w.classList.remove('minimized');
                bringToFront(w);
            } else if (isAct) {
                minimizeWindow(id);
            } else {
                bringToFront(w);
            }
        };
        container.appendChild(chip);
    });
}

// Window Clamping & Multi-Monitor Adaptation
function clampWindowToScreen(win) {
    const ws = document.getElementById('desktop-workspace');
    if (!ws) return;
    const maxW = ws.clientWidth;
    const maxH = ws.clientHeight;
    let left = win.offsetLeft;
    let top = win.offsetTop;
    if (left + win.offsetWidth > maxW) left = Math.max(10, maxW - win.offsetWidth - 10);
    if (top + win.offsetHeight > maxH) top = Math.max(10, maxH - win.offsetHeight - 10);
    if (left < 10) left = 10;
    if (top < 10) top = 10;
    win.style.left = left + 'px';
    win.style.top = top + 'px';
}

window.addEventListener('resize', () => {
    document.querySelectorAll('.dex-window').forEach(w => {
        if (!w.classList.contains('maximized') && !w.classList.contains('snapped-left') && !w.classList.contains('snapped-right')) {
            clampWindowToScreen(w);
        }
    });
});

// Draggable Window Headers, Aero Snap & Resizing
const snapPreview = document.getElementById('snap-preview');
let currentSnapState = null;

document.querySelectorAll('.dex-window').forEach(win => {
    const header = win.querySelector('.window-header');
    let isDragging = false;
    let startX, startY, initialLeft, initialTop;

    header.addEventListener('dblclick', () => {
        win.classList.remove('snapped-left', 'snapped-right');
        toggleMaximize(win.id);
    });

    header.addEventListener('mousedown', (e) => {
        if (e.target.closest('.win-btn')) return;
        if (win.classList.contains('maximized') || win.classList.contains('snapped-left') || win.classList.contains('snapped-right')) {
            win.classList.remove('maximized', 'snapped-left', 'snapped-right');
            win.style.width = '880px';
            win.style.height = '580px';
            win.style.left = Math.max(10, e.clientX - 440) + 'px';
            win.style.top = Math.max(10, e.clientY - 20) + 'px';
        }
        isDragging = true;
        bringToFront(win);
        startX = e.clientX;
        startY = e.clientY;
        initialLeft = win.offsetLeft;
        initialTop = win.offsetTop;
    });

    document.addEventListener('mousemove', (e) => {
        if (!isDragging) return;
        const dx = e.clientX - startX;
        const dy = e.clientY - startY;
        const ws = document.getElementById('desktop-workspace');
        win.style.left = (initialLeft + dx) + 'px';
        win.style.top = (initialTop + dy) + 'px';

        // Aero Snap Detection (DeX style)
        if (e.clientY <= 15) {
            currentSnapState = 'max';
            snapPreview.style.top = '8px';
            snapPreview.style.left = '8px';
            snapPreview.style.width = (ws.clientWidth - 16) + 'px';
            snapPreview.style.height = (ws.clientHeight - 16) + 'px';
            snapPreview.style.display = 'block';
        } else if (e.clientX <= 15) {
            currentSnapState = 'left';
            snapPreview.style.top = '8px';
            snapPreview.style.left = '8px';
            snapPreview.style.width = (Math.floor(ws.clientWidth / 2) - 12) + 'px';
            snapPreview.style.height = (ws.clientHeight - 16) + 'px';
            snapPreview.style.display = 'block';
        } else if (e.clientX >= ws.clientWidth - 15) {
            currentSnapState = 'right';
            snapPreview.style.top = '8px';
            snapPreview.style.left = (Math.floor(ws.clientWidth / 2) + 4) + 'px';
            snapPreview.style.width = (Math.floor(ws.clientWidth / 2) - 12) + 'px';
            snapPreview.style.height = (ws.clientHeight - 16) + 'px';
            snapPreview.style.display = 'block';
        } else {
            currentSnapState = null;
            snapPreview.style.display = 'none';
        }
    });

    document.addEventListener('mouseup', () => {
        if (!isDragging) return;
        isDragging = false;
        snapPreview.style.display = 'none';
        if (currentSnapState === 'max') {
            win.classList.remove('snapped-left', 'snapped-right');
            win.classList.add('maximized');
        } else if (currentSnapState === 'left') {
            win.classList.remove('maximized', 'snapped-right');
            win.classList.add('snapped-left');
        } else if (currentSnapState === 'right') {
            win.classList.remove('maximized', 'snapped-left');
            win.classList.add('snapped-right');
        } else {
            clampWindowToScreen(win);
        }
        currentSnapState = null;
    });

    win.addEventListener('mousedown', () => {
        bringToFront(win);
    });

    // Resizable window handle
    const resizeHandle = document.createElement('div');
    resizeHandle.className = 'win-resize-handle';
    win.appendChild(resizeHandle);

    let isResizing = false;
    let startW, startH, rStartX, rStartY;
    resizeHandle.addEventListener('mousedown', (e) => {
        e.stopPropagation();
        if (win.classList.contains('maximized') || win.classList.contains('snapped-left') || win.classList.contains('snapped-right')) {
            win.classList.remove('maximized', 'snapped-left', 'snapped-right');
        }
        isResizing = true;
        rStartX = e.clientX;
        rStartY = e.clientY;
        startW = win.offsetWidth;
        startH = win.offsetHeight;
        document.body.style.cursor = 'se-resize';
    });
    document.addEventListener('mousemove', (e) => {
        if (!isResizing) return;
        const newW = Math.max(480, startW + (e.clientX - rStartX));
        const newH = Math.max(320, startH + (e.clientY - rStartY));
        win.style.width = newW + 'px';
        win.style.height = newH + 'px';
    });
    document.addEventListener('mouseup', () => {
        if (isResizing) {
            isResizing = false;
            document.body.style.cursor = 'default';
        }
    });
});

// Start Menu Toggle
function toggleStartMenu() {
    const menu = document.getElementById('dex-start-menu');
    menu.classList.toggle('open');
}

document.addEventListener('click', (e) => {
    const menu = document.getElementById('dex-start-menu');
    const startBtn = document.querySelector('.start-btn');
    if (menu.classList.contains('open') && !menu.contains(e.target) && !startBtn.contains(e.target)) {
        menu.classList.remove('open');
    }
    const cmd = document.getElementById('dex-command-palette');
    if (cmd.classList.contains('open') && !cmd.contains(e.target) && !e.target.closest('.taskbar-search-trigger')) {
        cmd.classList.remove('open');
    }
    const ctx = document.getElementById('dex-context-menu');
    if (ctx.style.display === 'block' && !ctx.contains(e.target)) {
        ctx.style.display = 'none';
    }
});

// Fullscreen Toggle
function toggleFullscreen() {
    if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(() => {});
    } else {
        document.exitFullscreen().catch(() => {});
    }
}

// Toast Helper
function showToast(msg) {
    const toast = document.getElementById('dex-toast');
    toast.textContent = msg;
    toast.style.display = 'block';
    setTimeout(() => { toast.style.display = 'none'; }, 2600);
}

// Data Loaders
async function loadCompanies() {
    try {
        const res = await fetch('/api/companies');
        const data = await res.json();
        const sel = document.getElementById('companySelector');
        sel.innerHTML = '';
        data.companies.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = c.name;
            if (c.id === data.activeCompanyId) opt.selected = true;
            sel.appendChild(opt);
        });
        const active = data.companies.find(c => c.id === data.activeCompanyId);
        const name = active ? active.name : '';
        document.getElementById('dashCompanyBadge').textContent = name;
        document.title = name ? ('Teller DeX Desktop | ' + name) : 'Teller DeX Desktop';
    } catch (err) { console.error('Failed to load companies', err); }
}

async function onCompanyChange(id) {
    try {
        await fetch('/api/select-company', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ companyId: parseInt(id) })
        });
        location.reload();
    } catch (err) { alert('Company switch error: ' + err); }
}

async function loadDashboard() {
    try {
        const res = await fetch('/api/dashboard');
        const data = await res.json();
        document.getElementById('kpiSales').textContent = '₹' + (data.todaySales || 0).toLocaleString('en-IN', {minimumFractionDigits: 2});
        document.getElementById('kpiPurchases').textContent = '₹' + (data.todayPurchases || 0).toLocaleString('en-IN', {minimumFractionDigits: 2});
        document.getElementById('kpiCash').textContent = '₹' + (data.cashInHand || 0).toLocaleString('en-IN', {minimumFractionDigits: 2});
        document.getElementById('kpiBank').textContent = '₹' + (data.bankBalance || 0).toLocaleString('en-IN', {minimumFractionDigits: 2});

        const tbody = document.getElementById('recentTableBody');
        tbody.innerHTML = '';
        if (!data.recentTransactions || data.recentTransactions.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--dex-text-dim); padding: 30px;">No vouchers recorded today. Press F8 to record a sale.</td></tr>';
            return;
        }
        data.recentTransactions.forEach(tx => {
            const tr = document.createElement('tr');
            const pillClass = 'pill pill-' + (tx.type || '').toLowerCase();
            tr.innerHTML = `
                <td class="mono">${tx.date}</td>
                <td><span class="${pillClass}">${tx.type}</span></td>
                <td class="mono" style="font-weight: 700;">${tx.voucherNumber}</td>
                <td>${tx.particulars || '—'}</td>
                <td class="tar mono" style="font-weight: 700;">₹${Number(tx.amount || 0).toLocaleString('en-IN', {minimumFractionDigits: 2})}</td>
                <td class="tar"><button class="dex-btn dex-btn-subtle" onclick="printVoucher(${tx.id})" style="padding: 3px 8px; font-size: 11px;">🖨️ Print</button></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) { console.error(err); }
}

async function loadDaybook() {
    try {
        const date = document.getElementById('daybookDatePicker').value;
        const url = date ? '/api/daybook?date=' + date : '/api/daybook';
        const res = await fetch(url);
        const data = await res.json();
        allDaybookData = data.vouchers || [];
        renderDaybookTable(allDaybookData);
    } catch (err) { console.error(err); }
}

function setDaybookToday() {
    document.getElementById('daybookDatePicker').value = new Date().toISOString().split('T')[0];
    loadDaybook();
}

function loadDaybookAll() {
    document.getElementById('daybookDatePicker').value = '';
    loadDaybook();
}

function renderDaybookTable(list) {
    const tbody = document.getElementById('daybookTableBody');
    tbody.innerHTML = '';
    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--dex-text-dim); padding: 30px;">No transactions found for this date.</td></tr>';
        return;
    }
    list.forEach(v => {
        const tr = document.createElement('tr');
        const pillClass = 'pill pill-' + (v.type || '').toLowerCase();
        tr.innerHTML = `
            <td class="mono">${v.date}</td>
            <td><span class="${pillClass}">${v.type}</span></td>
            <td class="mono" style="font-weight: 700;">${v.voucherNumber}</td>
            <td><a style="color:#60a5fa; cursor:pointer; text-decoration:underline;" onclick="openLedgerForm(${v.particularsLedgerId})">${v.particulars || '—'}</a></td>
            <td style="color: var(--dex-text-dim);">${v.narration || '—'}</td>
            <td class="tar mono">${v.totalDebit ? '₹' + v.totalDebit.toLocaleString('en-IN', {minimumFractionDigits: 2}) : '—'}</td>
            <td class="tar mono">${v.totalCredit ? '₹' + v.totalCredit.toLocaleString('en-IN', {minimumFractionDigits: 2}) : '—'}</td>
            <td class="tar">
                <button class="dex-btn dex-btn-subtle" onclick="viewVoucher(${v.id})" style="padding:2px 7px; font-size:11px;">👁️</button>
                <button class="dex-btn dex-btn-subtle" onclick="printVoucher(${v.id})" style="padding:2px 7px; font-size:11px;">🖨️</button>
                <button class="win-btn close" onclick="deleteVoucher(${v.id})" style="width:22px; height:22px;">✕</button>
            </td>
        `;
        tr.oncontextmenu = (e) => {
            e.preventDefault();
            selectedContextVoucher = v;
            showContextMenu(e.clientX, e.clientY);
        };
        tbody.appendChild(tr);
    });
}

function filterDaybook(term) {
    const q = term.toLowerCase().trim();
    const filtered = allDaybookData.filter(v =>
        (v.voucherNumber || '').toLowerCase().includes(q) ||
        (v.particulars || '').toLowerCase().includes(q) ||
        (v.type || '').toLowerCase().includes(q) ||
        (v.narration || '').toLowerCase().includes(q)
    );
    renderDaybookTable(filtered);
}

function exportDaybookCsv() {
    if (!allDaybookData || allDaybookData.length === 0) return alert('No daybook rows to export');
    let csv = "Date,Type,VoucherNumber,Particulars,Narration,Debit,Credit\n";
    allDaybookData.forEach(v => {
        csv += `"${v.date}","${v.type}","${v.voucherNumber}","${v.particulars || ''}","${v.narration || ''}",${v.totalDebit || 0},${v.totalCredit || 0}\n`;
    });
    downloadBlob(csv, "Daybook_Export.csv", "text/csv;charset=utf-8;");
}

async function loadLedgers() {
    try {
        const res = await fetch('/api/ledgers');
        const data = await res.json();
        allLedgers = data.ledgers || [];
        renderLedgersTable(allLedgers);
        populateVoucherLedgerSelects();
        populateLedgerStatementSelect();
    } catch (err) { console.error(err); }
}

function renderLedgersTable(list) {
    const tbody = document.getElementById('ledgersTableBody');
    tbody.innerHTML = '';
    list.forEach(l => {
        const tr = document.createElement('tr');
        const bal = l.balance || 0;
        const balColor = bal > 0 ? '#34d399' : (bal < 0 ? '#f87171' : 'var(--dex-text-dim)');
        tr.innerHTML = `
            <td style="font-weight: 700; color: #fff;">${l.name}</td>
            <td><span class="badge">${l.group}</span></td>
            <td class="mono">${l.gstin || '—'}</td>
            <td>${l.phoneNumber || '—'}</td>
            <td class="tar mono" style="font-weight: 700; color: ${balColor};">₹${Math.abs(bal).toLocaleString('en-IN', {minimumFractionDigits: 2})} ${bal >= 0 ? 'Dr' : 'Cr'}</td>
            <td class="tar">
                <button class="dex-btn dex-btn-subtle" onclick="openLedgerForm(${l.id})" style="padding:2px 7px; font-size:11px;">✏️</button>
                <button class="win-btn close" onclick="deleteLedger(${l.id})" style="width:22px; height:22px;">✕</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function filterLedgers(q) {
    q = q.toLowerCase().trim();
    const filtered = allLedgers.filter(l =>
        l.name.toLowerCase().includes(q) ||
        l.group.toLowerCase().includes(q) ||
        (l.gstin || '').toLowerCase().includes(q)
    );
    renderLedgersTable(filtered);
}

function exportLedgersCsv() {
    if (!allLedgers || allLedgers.length === 0) return;
    let csv = "Name,Group,GSTIN,Phone,Balance\n";
    allLedgers.forEach(l => {
        csv += `"${l.name}","${l.group}","${l.gstin || ''}","${l.phoneNumber || ''}",${l.balance || 0}\n`;
    });
    downloadBlob(csv, "Ledgers_Export.csv", "text/csv;charset=utf-8;");
}

async function loadStockItems() {
    try {
        const res = await fetch('/api/stock-items');
        const data = await res.json();
        allStockItems = data.stockItems || [];
        renderStockTable(allStockItems);
    } catch (err) { console.error(err); }
}

function renderStockTable(list) {
    const tbody = document.getElementById('stockTableBody');
    tbody.innerHTML = '';
    list.forEach(s => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td style="font-weight: 700; color: #fff;">${s.name}</td>
            <td><span class="badge">${s.unit}</span></td>
            <td class="mono">${s.hsnCode || '—'}</td>
            <td class="tar mono">${s.gstRate}%</td>
            <td class="tar mono" style="font-weight: 700;">₹${Number(s.rate || 0).toLocaleString('en-IN', {minimumFractionDigits: 2})}</td>
            <td class="tar mono">${s.stock || 0}</td>
            <td class="tar">
                <button class="dex-btn dex-btn-subtle" onclick="openStockForm(${s.id})" style="padding:2px 7px; font-size:11px;">✏️</button>
                <button class="win-btn close" onclick="deleteStock(${s.id})" style="width:22px; height:22px;">✕</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function filterStock(q) {
    q = q.toLowerCase().trim();
    const filtered = allStockItems.filter(s =>
        s.name.toLowerCase().includes(q) ||
        (s.hsnCode || '').toLowerCase().includes(q)
    );
    renderStockTable(filtered);
}

async function loadTrialBalance() {
    try {
        const res = await fetch('/api/reports/trial-balance');
        const data = await res.json();
        const tbody = document.getElementById('tbTableBody');
        tbody.innerHTML = '';
        let totDr = 0, totCr = 0;
        (data.rows || []).forEach(r => {
            totDr += r.debit || 0;
            totCr += r.credit || 0;
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td style="font-weight: 700; color: #fff;">${r.name}</td>
                <td style="color: var(--dex-text-muted);">${r.group}</td>
                <td class="tar mono">${r.debit ? '₹' + r.debit.toLocaleString('en-IN', {minimumFractionDigits: 2}) : '—'}</td>
                <td class="tar mono">${r.credit ? '₹' + r.credit.toLocaleString('en-IN', {minimumFractionDigits: 2}) : '—'}</td>
            `;
            tbody.appendChild(tr);
        });
        document.getElementById('tbTotalDebit').textContent = '₹' + totDr.toLocaleString('en-IN', {minimumFractionDigits: 2});
        document.getElementById('tbTotalCredit').textContent = '₹' + totCr.toLocaleString('en-IN', {minimumFractionDigits: 2});
    } catch (err) { console.error(err); }
}

// Voucher Creation Modal
let activeVoucherType = 'Sales';
let editingVoucherId = null;
let vvCurrentId = null;

function openNewVoucherWindow(type = 'Sales') {
    activeVoucherType = type;
    editingVoucherId = null;
    openWindow('win-voucher');
    setVoucherType(type);
    document.getElementById('vNum').value = 'INV-' + (Math.floor(Date.now() / 1000) % 100000);
    // Ask the phone for the real next bill number (the time-based one above repeats and skips numbers).
    if (type === 'Sales' && typeof qbFillNextNumber === 'function') qbFillNextNumber('vNum');
    document.getElementById('vDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('itemsTableBody').innerHTML = '';
    addItemRow();
    recalculateGrandTotal();
}

function setVoucherType(type) {
    activeVoucherType = type;
    document.getElementById('voucherWinTitle').textContent = 'Voucher Entry (' + type + ')';
    const lblD = document.getElementById('lblDebit');
    const lblC = document.getElementById('lblCredit');
    if (type === 'Sales') {
        lblD.textContent = 'Party / Customer (Debit)';
        lblC.textContent = 'Sales Account (Credit)';
    } else if (type === 'Purchase') {
        lblD.textContent = 'Purchase Account (Debit)';
        lblC.textContent = 'Supplier / Vendor (Credit)';
    } else if (type === 'Receipt') {
        lblD.textContent = 'Cash / Bank (Debit)';
        lblC.textContent = 'Received From Party (Credit)';
    } else if (type === 'Payment') {
        lblD.textContent = 'Paid To Party / Expense (Debit)';
        lblC.textContent = 'Cash / Bank (Credit)';
    } else {
        lblD.textContent = 'Debit Ledger';
        lblC.textContent = 'Credit Ledger';
    }
    populateVoucherLedgerSelects();
}

function populateVoucherLedgerSelects() {
    const deb = document.getElementById('vDebit');
    const cre = document.getElementById('vCredit');
    if (!deb || !cre) return;
    deb.innerHTML = '';
    cre.innerHTML = '';
    allLedgers.forEach(l => {
        const o1 = document.createElement('option');
        o1.value = l.id;
        o1.textContent = l.name + ' (' + l.group + ')';
        deb.appendChild(o1);

        const o2 = document.createElement('option');
        o2.value = l.id;
        o2.textContent = l.name + ' (' + l.group + ')';
        cre.appendChild(o2);
    });

    // Smart Defaults
    if (activeVoucherType === 'Sales') {
        const party = allLedgers.find(l => l.group.includes('Debtor') || l.name.includes('Cash'));
        const sales = allLedgers.find(l => l.group.includes('Sales') || l.name.includes('Sales'));
        if (party) deb.value = party.id;
        if (sales) cre.value = sales.id;
    } else if (activeVoucherType === 'Purchase') {
        const pur = allLedgers.find(l => l.group.includes('Purchase'));
        const supp = allLedgers.find(l => l.group.includes('Creditor') || l.name.includes('Cash'));
        if (pur) deb.value = pur.id;
        if (supp) cre.value = supp.id;
    } else if (activeVoucherType === 'Receipt') {
        const cash = allLedgers.find(l => l.group.includes('Cash') || l.group.includes('Bank'));
        if (cash) deb.value = cash.id;
    } else if (activeVoucherType === 'Payment') {
        const cash = allLedgers.find(l => l.group.includes('Cash') || l.group.includes('Bank'));
        if (cash) cre.value = cash.id;
    }
}

function addItemRow() {
    const tbody = document.getElementById('itemsTableBody');
    const tr = document.createElement('tr');
    let itemOptions = '<option value="">-- Select Item --</option>';
    allStockItems.forEach(s => {
        itemOptions += `<option value="${s.id}" data-rate="${s.rate}" data-gst="${s.gstRate}">${s.name}</option>`;
    });

    tr.innerHTML = `
        <td>
            <select class="dex-input" style="padding: 4px 8px; width: 100%; font-size: 12px;" onchange="onItemSelect(this)">
                ${itemOptions}
            </select>
        </td>
        <td><input type="number" class="dex-input mono qty-in" value="1" min="0" step="any" style="padding: 4px 8px; width: 100%;" oninput="recalculateRow(this)"></td>
        <td><input type="number" class="dex-input mono rate-in" value="0.00" min="0" step="any" style="padding: 4px 8px; width: 100%;" oninput="recalculateRow(this)"></td>
        <td><input type="number" class="dex-input mono gst-in" value="0" min="0" step="any" style="padding: 4px 8px; width: 100%;" oninput="recalculateRow(this)"></td>
        <td class="tar mono row-total" style="font-weight: 700;">₹0.00</td>
        <td style="text-align: center;"><button class="win-btn close" onclick="this.closest('tr').remove(); recalculateGrandTotal();" style="width: 22px; height: 22px;">✕</button></td>
    `;
    tbody.appendChild(tr);
}

function onItemSelect(selectEl) {
    const tr = selectEl.closest('tr');
    const opt = selectEl.options[selectEl.selectedIndex];
    if (opt) {
        const rate = opt.getAttribute('data-rate') || 0;
        const gst = opt.getAttribute('data-gst') || 0;
        tr.querySelector('.rate-in').value = rate;
        tr.querySelector('.gst-in').value = gst;
        recalculateRow(selectEl);
    }
}

function recalculateRow(inputEl) {
    const tr = inputEl.closest('tr');
    const qty = parseFloat(tr.querySelector('.qty-in').value) || 0;
    const rate = parseFloat(tr.querySelector('.rate-in').value) || 0;
    // Rate is the tax-INCLUSIVE selling price; the gross is qty × rate and the tax is backed out server-side.
    const gross = qty * rate;
    tr.querySelector('.row-total').textContent = '₹' + gross.toFixed(2);
    recalculateGrandTotal();
}

function recalculateGrandTotal() {
    let sum = 0;
    document.querySelectorAll('#itemsTableBody tr').forEach(tr => {
        const qty = parseFloat(tr.querySelector('.qty-in').value) || 0;
        const rate = parseFloat(tr.querySelector('.rate-in').value) || 0;
        sum += qty * rate;
    });
    if (sum > 0) {
        document.getElementById('vAmount').value = sum.toFixed(2);
    }
}

async function saveVoucher() {
    const btn = document.getElementById('btnPostVoucher');
    const vNum = document.getElementById('vNum').value.trim() || ('V-' + Math.floor(Date.now() / 1000));
    const date = document.getElementById('vDate').value;
    const debit = parseInt(document.getElementById('vDebit').value);
    const credit = parseInt(document.getElementById('vCredit').value);
    const amount = parseFloat(document.getElementById('vAmount').value) || 0;
    const narr = document.getElementById('vNarr').value.trim();

    if (!debit || !credit) return alert('Please select debit and credit ledgers');
    if (debit === credit) return alert('Debit and credit ledgers cannot be identical');
    if (amount <= 0) return alert('Please enter a valid amount greater than 0');

    const items = [];
    document.querySelectorAll('#itemsTableBody tr').forEach(tr => {
        const sId = parseInt(tr.querySelector('select').value);
        const qty = parseFloat(tr.querySelector('.qty-in').value) || 0;
        const rate = parseFloat(tr.querySelector('.rate-in').value) || 0;
        const gst = parseFloat(tr.querySelector('.gst-in').value) || 0;
        if (sId && qty > 0) {
            items.push({ stockItemId: sId, quantity: qty, rate: rate, gstRate: gst });
        }
    });

    btn.disabled = true;
    btn.textContent = 'Posting to Phone...';

    try {
        const payload = {
            type: activeVoucherType,
            voucherNumber: vNum,
            date: date,
            debitLedgerId: debit,
            creditLedgerId: credit,
            amount: amount,
            narration: narr,
            items: items
        };
        if (editingVoucherId) payload.id = editingVoucherId;
        const res = await fetch(editingVoucherId ? '/api/vouchers/update' : '/api/vouchers/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const result = await res.json();
        if (result.success) {
            showToast('✓ Voucher #' + vNum + ' saved!');
            editingVoucherId = null;
            closeWindow('win-voucher');
            loadDashboard();
            loadDaybook();
            loadRegister();
            const party = allLedgers.find(l => l.id === debit);
            if (party && party.gstin) {
                showToast('💡 ' + party.name + ' is GST-registered — enter their address & pincode before e-Invoice/e-Way Bill.');
            }
        } else {
            alert('Save Failed: ' + (result.error || 'Server rejected voucher'));
        }
    } catch (err) {
        alert('Connection error: ' + err.message);
    } finally {
        btn.disabled = false;
        btn.textContent = 'Save & Post to Phone (Ctrl+S)';
    }
}

async function printVoucher(id, preOpened) {
    // Open the window synchronously (avoids popup blockers), then fill it with the invoice fetched
    // using the Authorization header - the token never goes into a URL. Callers that save first
    // (Quick Bill) open the window themselves, inside the click, and pass it as `preOpened`.
    const w = preOpened || window.open('', '_blank');
    try {
        const res = await fetch('/api/voucher-print?id=' + encodeURIComponent(id));
        if (!res.ok) throw new Error('the phone could not build this invoice (' + res.status + ')');
        const type = res.headers.get('Content-Type') || '';
        if (type.indexOf('application/pdf') >= 0) {
            // Sales/Purchase come back as a real PDF (same as the phone's invoice), not HTML.
            const blob = await res.blob();
            const url = URL.createObjectURL(blob);
            if (w) w.location.href = url;
            else showToast('Pop-up blocked: allow pop-ups for this page to print.');
        } else {
            const html = await res.text();
            if (w) { w.document.open(); w.document.write(html); w.document.close(); }
        }
    } catch (err) {
        if (w) w.close();
        alert('Could not load invoice: ' + err.message);
    }
}

// Context Menu Handlers
function showContextMenu(x, y) {
    const ctx = document.getElementById('dex-context-menu');
    ctx.style.left = x + 'px';
    ctx.style.top = y + 'px';
    ctx.style.display = 'block';
}

function contextPrintVoucher() {
    if (selectedContextVoucher) printVoucher(selectedContextVoucher.id);
    document.getElementById('dex-context-menu').style.display = 'none';
}

function contextCopyVoucherNo() {
    if (selectedContextVoucher) {
        navigator.clipboard.writeText(selectedContextVoucher.voucherNumber);
        showToast('Copied ' + selectedContextVoucher.voucherNumber);
    }
    document.getElementById('dex-context-menu').style.display = 'none';
}

// Command Palette (Ctrl+K)
function openCommandPalette() {
    const pal = document.getElementById('dex-command-palette');
    pal.classList.add('open');
    const inp = document.getElementById('cmdInput');
    inp.value = '';
    inp.focus();
    handleCommandSearch('');
}

function handleCommandSearch(q) {
    q = q.toLowerCase().trim();
    const results = document.getElementById('cmdResults');
    results.innerHTML = '';
    const commands = [
        { title: '📊 Open Dashboard', action: () => openWindow('win-dashboard') },
        { title: '📑 Open Daybook', action: () => openWindow('win-daybook') },
        { title: '⚡ New Sales Invoice [F8]', action: () => openNewVoucherWindow('Sales') },
        { title: '🛒 Quick Bill [F2]', action: () => openWindow('win-quickbill') },
        { title: '⚡ New Purchase Bill [F9]', action: () => openNewVoucherWindow('Purchase') },
        { title: '⚡ New Receipt [F6]', action: () => openNewVoucherWindow('Receipt') },
        { title: '⚡ New Payment [F5]', action: () => openNewVoucherWindow('Payment') },
        { title: '👥 View Ledgers & Accounts', action: () => openWindow('win-ledgers') },
        { title: '📦 View Stock & Inventory', action: () => openWindow('win-stock') },
        { title: '⚖️ Open Trial Balance', action: () => openWindow('win-trial-balance') },
        { title: '📒 Open Registers', action: () => openWindow('win-register') },
        { title: '📄 Ledger Statement', action: () => openWindow('win-ledger-statement') },
        { title: '⏳ Outstanding Bills', action: () => openWindow('win-outstanding') },
        { title: '📦 Stock Summary', action: () => openWindow('win-stock-summary') },
        { title: '📈 Profit & Loss', action: () => openWindow('win-profit-loss') },
        { title: '🏛️ Balance Sheet', action: () => openWindow('win-balance-sheet') },
        { title: '🖥️ Toggle Fullscreen', action: () => toggleFullscreen() }
    ];

    const filtered = commands.filter(c => c.title.toLowerCase().includes(q));
    filtered.forEach(c => {
        const div = document.createElement('div');
        div.className = 'cmd-row';
        div.textContent = c.title;
        div.onclick = () => {
            document.getElementById('dex-command-palette').classList.remove('open');
            c.action();
        };
        results.appendChild(div);
    });
}

// Global Keyboard Shortcuts (Tally / DeX PC Experience)
window.addEventListener('keydown', (e) => {
    if (e.key === 'F4') { e.preventDefault(); openNewVoucherWindow('Contra'); }
    else if (e.key === 'F5') { e.preventDefault(); openNewVoucherWindow('Payment'); }
    else if (e.key === 'F6') { e.preventDefault(); openNewVoucherWindow('Receipt'); }
    else if (e.key === 'F7') { e.preventDefault(); openNewVoucherWindow('Journal'); }
    else if (e.key === 'F8') { e.preventDefault(); openNewVoucherWindow('Sales'); }
    else if (e.key === 'F9') { e.preventDefault(); openNewVoucherWindow('Purchase'); }
    else if (e.ctrlKey && e.key.toLowerCase() === 'k') { e.preventDefault(); openCommandPalette(); }
    else if (e.ctrlKey && e.key.toLowerCase() === 's') {
        e.preventDefault();
        const vw = document.getElementById('win-voucher');
        if (vw.style.display !== 'none' && !vw.classList.contains('minimized')) {
            saveVoucher();
        }
    } else if (e.key === 'Escape') {
        const cmd = document.getElementById('dex-command-palette');
        if (cmd.classList.contains('open')) { cmd.classList.remove('open'); return; }
        const start = document.getElementById('dex-start-menu');
        if (start.classList.contains('open')) { start.classList.remove('open'); return; }
        const active = document.querySelector('.dex-window.active');
        if (active) closeWindow(active.id);
    }
});

function downloadBlob(content, filename, contentType) {
    const blob = new Blob([content], { type: contentType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

// ── Registers & Books ───────────────────────────────────────────
async function loadRegister() {
    try {
        const type = document.getElementById('registerType').value;
        let url = '/api/vouchers?type=' + encodeURIComponent(type);
        const from = document.getElementById('registerFrom') ? document.getElementById('registerFrom').value : '';
        const to = document.getElementById('registerTo') ? document.getElementById('registerTo').value : '';
        if (from) url += '&from=' + from;
        if (to) url += '&to=' + to;
        const res = await fetch(url);
        const data = await res.json();
        allRegisterData = data.vouchers || [];
        renderRegisterTable(allRegisterData);
    } catch (err) { console.error(err); }
}

function renderRegisterTable(list) {
    const tbody = document.getElementById('registerTableBody');
    tbody.innerHTML = '';
    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--dex-text-dim);padding:30px;">No vouchers of this type.</td></tr>';
        return;
    }
    list.forEach(v => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="mono">${v.date}</td>
            <td class="mono" style="font-weight:700;">${v.voucherNumber}</td>
            <td><a style="color:#60a5fa; cursor:pointer; text-decoration:underline;" onclick="openLedgerForm(${v.particularsLedgerId})">${v.particulars || '—'}</a></td>
            <td style="color:var(--dex-text-dim);">${v.narration || '—'}</td>
            <td class="tar mono">${v.totalDebit ? '₹' + v.totalDebit.toLocaleString('en-IN', {minimumFractionDigits:2}) : '—'}</td>
            <td class="tar mono">${v.totalCredit ? '₹' + v.totalCredit.toLocaleString('en-IN', {minimumFractionDigits:2}) : '—'}</td>
            <td class="tar">
                <button class="dex-btn dex-btn-subtle" onclick="viewVoucher(${v.id})" style="padding:2px 7px; font-size:11px;">👁️</button>
                <button class="win-btn close" onclick="deleteVoucher(${v.id})" style="width:22px; height:22px;">✕</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function exportRegisterCsv() {
    if (!allRegisterData || allRegisterData.length === 0) return alert('No rows to export');
    let csv = "Date,VoucherNumber,Particulars,Narration,Debit,Credit\n";
    allRegisterData.forEach(v => {
        csv += `"${v.date}","${v.voucherNumber}","${v.particulars || ''}","${v.narration || ''}",${v.totalDebit || 0},${v.totalCredit || 0}\n`;
    });
    downloadBlob(csv, "Register_Export.csv", "text/csv;charset=utf-8;");
}

// ── Ledger Statement ────────────────────────────────────────────
function populateLedgerStatementSelect() {
    const sel = document.getElementById('ledgerStmtLedger');
    if (!sel) return;
    const prev = sel.value;
    sel.innerHTML = '';
    allLedgers.forEach(l => {
        const opt = document.createElement('option');
        opt.value = l.id;
        opt.textContent = l.name + ' (' + l.group + ')';
        sel.appendChild(opt);
    });
    if (prev) sel.value = prev;
}

async function loadLedgerStatement() {
    try {
        const id = document.getElementById('ledgerStmtLedger').value;
        if (!id) return;
        const res = await fetch('/api/ledger-statement?ledgerId=' + id);
        const data = await res.json();
        document.getElementById('ledgerStmtOpening').textContent = '₹' + (data.ledger.openingBalance || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        document.getElementById('ledgerStmtClosing').textContent = '₹' + (data.closingBalance || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        const tbody = document.getElementById('ledgerStmtTableBody');
        tbody.innerHTML = '';
        if (!data.entries || data.entries.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--dex-text-dim);padding:30px;">No transactions for this ledger.</td></tr>';
            return;
        }
        data.entries.forEach(e => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td class="mono">${e.date}</td>
                <td class="mono" style="font-weight:700;">${e.voucherNumber}</td>
                <td>${e.type}</td>
                <td class="tar mono">${e.debit ? '₹' + e.debit.toLocaleString('en-IN', {minimumFractionDigits:2}) : '—'}</td>
                <td class="tar mono">${e.credit ? '₹' + e.credit.toLocaleString('en-IN', {minimumFractionDigits:2}) : '—'}</td>
                <td class="tar mono" style="font-weight:700;">₹${e.balance.toLocaleString('en-IN', {minimumFractionDigits:2})}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) { console.error(err); }
}

// ── Outstanding ─────────────────────────────────────────────────
async function loadOutstanding() {
    try {
        const res = await fetch('/api/outstanding');
        const data = await res.json();
        renderOutstandingTable('receivablesTableBody', data.receivables || []);
        renderOutstandingTable('payablesTableBody', data.payables || []);
    } catch (err) { console.error(err); }
}

function renderOutstandingTable(tbodyId, list) {
    const tbody = document.getElementById(tbodyId);
    tbody.innerHTML = '';
    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--dex-text-dim);padding:20px;">None.</td></tr>';
        return;
    }
    list.forEach(o => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="mono">${o.date}</td>
            <td class="mono" style="font-weight:700;">${o.voucherNumber}</td>
            <td>${o.party || '—'}</td>
            <td class="tar mono">₹${(o.originalAmount || 0).toLocaleString('en-IN', {minimumFractionDigits:2})}</td>
            <td class="tar mono" style="font-weight:700;">₹${(o.pendingAmount || 0).toLocaleString('en-IN', {minimumFractionDigits:2})}</td>
            <td class="tar mono">${o.interestAmount ? '₹' + o.interestAmount.toLocaleString('en-IN', {minimumFractionDigits:2}) : '—'}</td>
        `;
        tbody.appendChild(tr);
    });
}

// ── Stock Summary ───────────────────────────────────────────────
async function loadStockSummary() {
    try {
        const res = await fetch('/api/reports/stock-summary');
        const data = await res.json();
        const tbody = document.getElementById('stockSummaryTableBody');
        tbody.innerHTML = '';
        (data.items || []).forEach(s => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td style="font-weight:700;">${s.name}</td>
                <td><span class="badge">${s.unit}</span></td>
                <td class="tar mono">${s.opening}</td>
                <td class="tar mono">${s.inward}</td>
                <td class="tar mono">${s.outward}</td>
                <td class="tar mono" style="font-weight:700;">${s.closing}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) { console.error(err); }
}

// ── Profit & Loss ───────────────────────────────────────────────
async function loadProfitLoss() {
    try {
        let url = '/api/reports/profit-loss';
        const from = document.getElementById('pnlFrom') ? document.getElementById('pnlFrom').value : '';
        const to = document.getElementById('pnlTo') ? document.getElementById('pnlTo').value : '';
        const qs = [];
        if (from) qs.push('from=' + from);
        if (to) qs.push('to=' + to);
        if (qs.length) url += '?' + qs.join('&');
        const res = await fetch(url);
        const data = await res.json();
        document.getElementById('pnlIncome').textContent = '₹' + (data.totalIncome || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        document.getElementById('pnlExpense').textContent = '₹' + (data.totalExpense || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        const net = data.netProfit || 0;
        const netEl = document.getElementById('pnlNet');
        netEl.textContent = '₹' + Math.abs(net).toLocaleString('en-IN', {minimumFractionDigits:2});
        netEl.style.color = net >= 0 ? '#34d399' : '#f87171';
        renderGroupTable('pnlIncomeTableBody', data.incomes || []);
        renderGroupTable('pnlExpenseTableBody', data.expenses || []);
    } catch (err) { console.error(err); }
}

function renderGroupTable(tbodyId, list) {
    const tbody = document.getElementById(tbodyId);
    tbody.innerHTML = '';
    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;color:var(--dex-text-dim);padding:20px;">None.</td></tr>';
        return;
    }
    list.forEach(r => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${r.group}</td>
            <td class="tar mono" style="font-weight:700;">₹${r.amount.toLocaleString('en-IN', {minimumFractionDigits:2})}</td>
        `;
        tbody.appendChild(tr);
    });
}

// ── Balance Sheet ───────────────────────────────────────────────
async function loadBalanceSheet() {
    try {
        let url = '/api/reports/balance-sheet';
        const to = document.getElementById('bsTo') ? document.getElementById('bsTo').value : '';
        if (to) url += '?to=' + to;
        const res = await fetch(url);
        const data = await res.json();
        document.getElementById('bsAssets').textContent = '₹' + (data.totalAssets || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        document.getElementById('bsLiabilities').textContent = '₹' + (data.totalLiabilities || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        renderGroupTable('bsAssetsTableBody', data.assets || []);
        renderGroupTable('bsLiabilitiesTableBody', data.liabilities || []);
    } catch (err) { console.error(err); }
}

// ── Masters CRUD: Ledgers ───────────────────────────────────────
function openLedgerForm(id) {
    const l = id ? allLedgers.find(x => x.id === id) : null;
    document.getElementById('ledgerFormId').value = l ? l.id : '';
    document.getElementById('ledgerFormTitle').textContent = l ? 'Edit Ledger' : 'New Ledger';
    document.getElementById('ledgerName').value = l ? l.name : '';
    document.getElementById('ledgerGroup').value = l ? l.group : '';
    document.getElementById('ledgerOpening').value = l ? (l.openingBalance || 0) : 0;
    document.getElementById('ledgerPhone').value = l ? (l.phoneNumber || '') : '';
    document.getElementById('ledgerGstin').value = l ? (l.gstin || '') : '';
    document.getElementById('ledgerAlias').value = l ? (l.alias || '') : '';
    openWindow('win-ledger-form');
}

async function saveLedger() {
    const id = document.getElementById('ledgerFormId').value;
    const payload = {
        name: document.getElementById('ledgerName').value.trim(),
        group: document.getElementById('ledgerGroup').value.trim(),
        openingBalance: parseFloat(document.getElementById('ledgerOpening').value) || 0,
        phoneNumber: document.getElementById('ledgerPhone').value.trim(),
        gstin: document.getElementById('ledgerGstin').value.trim(),
        alias: document.getElementById('ledgerAlias').value.trim()
    };
    if (!payload.name) return alert('Ledger name is required');
    try {
        const res = await fetch(id ? '/api/ledgers/update' : '/api/ledgers/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(id ? Object.assign({ id: parseInt(id) }, payload) : payload)
        });
        const result = await res.json();
        if (result.success) { showToast('✓ Ledger saved'); closeWindow('win-ledger-form'); loadLedgers(); }
        else alert('Save failed: ' + (result.error || ''));
    } catch (err) { alert('Connection error: ' + err.message); }
}

async function deleteLedger(id) {
    if (!confirm('Delete this ledger?')) return;
    try {
        const res = await fetch('/api/ledgers/delete', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: id })
        });
        const result = await res.json();
        if (result.success) { showToast('✓ Ledger deleted'); loadLedgers(); }
        else alert('Delete failed: ' + (result.error || ''));
    } catch (err) { alert('Connection error: ' + err.message); }
}

// ── Masters CRUD: Stock Items ───────────────────────────────────
function openStockForm(id) {
    const s = id ? allStockItems.find(x => x.id === id) : null;
    document.getElementById('stockFormId').value = s ? s.id : '';
    document.getElementById('stockFormTitle').textContent = s ? 'Edit Stock Item' : 'New Stock Item';
    document.getElementById('stockName').value = s ? s.name : '';
    document.getElementById('stockHsn').value = s ? (s.hsnCode || '') : '';
    document.getElementById('stockGst').value = s ? (s.gstRate || 0) : 0;
    document.getElementById('stockRate').value = s ? (s.rate || 0) : 0;
    document.getElementById('stockOpening').value = s ? (s.stock || 0) : 0;
    if (s && s.unitId) document.getElementById('stockUnit').value = s.unitId;
    openWindow('win-stock-form');
}

async function saveStock() {
    const id = document.getElementById('stockFormId').value;
    const payload = {
        name: document.getElementById('stockName').value.trim(),
        unitId: parseInt(document.getElementById('stockUnit').value) || 1,
        hsnCode: document.getElementById('stockHsn').value.trim(),
        gstRate: parseFloat(document.getElementById('stockGst').value) || 0,
        rate: parseFloat(document.getElementById('stockRate').value) || 0,
        openingStock: parseFloat(document.getElementById('stockOpening').value) || 0
    };
    if (!payload.name) return alert('Item name is required');
    try {
        const res = await fetch(id ? '/api/stock-items/update' : '/api/stock-items/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(id ? Object.assign({ id: parseInt(id) }, payload) : payload)
        });
        const result = await res.json();
        if (result.success) { showToast('✓ Item saved'); closeWindow('win-stock-form'); loadStockItems(); }
        else alert('Save failed: ' + (result.error || ''));
    } catch (err) { alert('Connection error: ' + err.message); }
}

async function deleteStock(id) {
    if (!confirm('Delete this stock item?')) return;
    try {
        const res = await fetch('/api/stock-items/delete', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: id })
        });
        const result = await res.json();
        if (result.success) { showToast('✓ Item deleted'); loadStockItems(); }
        else alert('Delete failed: ' + (result.error || ''));
    } catch (err) { alert('Connection error: ' + err.message); }
}

// ── Auto-detect HSN + GST for a stock item name ─────────────────
async function detectTaxForItem() {
    const name = document.getElementById('stockName') ? document.getElementById('stockName').value.trim() : '';
    if (!name) return;
    try {
        const res = await fetch('/api/stock-items/tax-info?name=' + encodeURIComponent(name));
        const data = await res.json();
        if (data.found) {
            document.getElementById('stockHsn').value = data.hsnCode || '';
            document.getElementById('stockGst').value = data.gstRate || 0;
            showToast('✓ HSN ' + data.hsnCode + ' • ' + data.gstRate + '% GST');
        }
    } catch (err) { /* ignore */ }
}

// ── Masters CRUD: Units ─────────────────────────────────────────
async function loadUnits() {
    try {
        const res = await fetch('/api/units');
        const data = await res.json();
        allUnits = data.units || [];
        const sel = document.getElementById('stockUnit');
        if (!sel) return;
        sel.innerHTML = '';
        allUnits.forEach(u => {
            const opt = document.createElement('option');
            opt.value = u.id;
            opt.textContent = u.name;
            sel.appendChild(opt);
        });
    } catch (err) { console.error(err); }
}

function openUnitForm() {
    document.getElementById('unitName').value = '';
    document.getElementById('unitFormal').value = '';
    openWindow('win-unit-form');
}

async function saveUnit() {
    const name = document.getElementById('unitName').value.trim();
    if (!name) return alert('Unit name is required');
    try {
        const res = await fetch('/api/units/create', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: name, formalName: document.getElementById('unitFormal').value.trim() })
        });
        const result = await res.json();
        if (result.success) { showToast('✓ Unit created'); closeWindow('win-unit-form'); loadUnits(); }
        else alert('Save failed: ' + (result.error || ''));
    } catch (err) { alert('Connection error: ' + err.message); }
}

// ── Voucher view & delete ───────────────────────────────────────
async function viewVoucher(id) {
    try {
        const res = await fetch('/api/voucher?id=' + id);
        const d = await res.json();
        vvCurrentId = id;
        document.getElementById('vvNumber').textContent = d.voucherNumber;
        document.getElementById('vvType').textContent = d.type;
        document.getElementById('vvDate').textContent = d.date;
        document.getElementById('vvNarration').textContent = d.narration || '—';
        const eb = document.getElementById('vvEntriesBody');
        eb.innerHTML = '';
        (d.entries || []).forEach(e => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><a style="color:#60a5fa; cursor:pointer; text-decoration:underline;" onclick="openLedgerForm(${e.ledgerId})">${e.ledger}</a></td>
                <td class="tar mono">${e.isDebit ? '₹' + e.amount.toLocaleString('en-IN', {minimumFractionDigits:2}) : '—'}</td>
                <td class="tar mono">${!e.isDebit ? '₹' + e.amount.toLocaleString('en-IN', {minimumFractionDigits:2}) : '—'}</td>
            `;
            eb.appendChild(tr);
        });
        const ib = document.getElementById('vvItemsBody');
        ib.innerHTML = '';
        (d.items || []).forEach(s => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><a style="color:#60a5fa; cursor:pointer; text-decoration:underline;" onclick="openStockForm(${s.stockItemId})">${s.name}</a></td>
                <td class="tar mono">${s.quantity}</td>
                <td class="tar mono">${s.rate}</td>
                <td class="tar mono">${s.gstRate}%</td>
                <td class="tar mono" style="font-weight:700;">₹${s.amount.toLocaleString('en-IN', {minimumFractionDigits:2})}</td>
            `;
            ib.appendChild(tr);
        });
        openWindow('win-voucher-view');
    } catch (err) { console.error(err); }
}

async function deleteVoucher(id) {
    if (!confirm('Delete this voucher? This cannot be undone.')) return;
    try {
        const res = await fetch('/api/vouchers/delete', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: id })
        });
        const result = await res.json();
        if (result.success) { showToast('✓ Voucher deleted'); loadDaybook(); loadDashboard(); loadRegister(); }
        else alert('Delete failed: ' + (result.error || ''));
    } catch (err) { alert('Connection error: ' + err.message); }
}

// ── Voucher edit & GST exports ──────────────────────────────────
async function editVoucher(id) {
    try {
        const res = await fetch('/api/voucher?id=' + id);
        const d = await res.json();
        closeWindow('win-voucher-view');
        openNewVoucherWindow(d.type);
        editingVoucherId = d.id;
        setVoucherType(d.type);
        document.getElementById('vNum').value = d.voucherNumber;
        const p = (d.date || '').split('-');
        if (p.length === 3) document.getElementById('vDate').value = p[2] + '-' + p[1] + '-' + p[0];
        document.getElementById('vNarr').value = d.narration || '';
        const debitEntry = (d.entries || []).find(e => e.isDebit);
        const creditEntry = (d.entries || []).find(e => !e.isDebit);
        if (debitEntry && debitEntry.ledgerId) document.getElementById('vDebit').value = debitEntry.ledgerId;
        if (creditEntry && creditEntry.ledgerId) document.getElementById('vCredit').value = creditEntry.ledgerId;
        document.getElementById('itemsTableBody').innerHTML = '';
        const items = d.items || [];
        if (items.length > 0) {
            items.forEach(s => {
                addItemRow();
                const tr = document.querySelector('#itemsTableBody tr:last-child');
                if (tr) {
                    tr.querySelector('select').value = s.stockItemId;
                    tr.querySelector('.qty-in').value = s.quantity;
                    tr.querySelector('.rate-in').value = s.rate;
                    tr.querySelector('.gst-in').value = s.gstRate;
                    recalculateRow(tr.querySelector('.qty-in'));
                }
            });
        } else {
            addItemRow();
        }
        recalculateGrandTotal();
        showToast('Editing ' + d.voucherNumber);
    } catch (err) { alert('Error loading voucher: ' + err.message); }
}

function gstExportUrl(id) {
    const addr = document.getElementById('partyAddr') ? document.getElementById('partyAddr').value : '';
    const place = document.getElementById('partyPlace') ? document.getElementById('partyPlace').value : '';
    const pin = document.getElementById('partyPincode') ? document.getElementById('partyPincode').value : '';
    const dist = document.getElementById('partyDistance') ? document.getElementById('partyDistance').value : '';
    let url = '?id=' + id;
    if (addr) url += '&address=' + encodeURIComponent(addr);
    if (place) url += '&place=' + encodeURIComponent(place);
    if (pin) url += '&pincode=' + encodeURIComponent(pin);
    if (dist) url += '&distance=' + encodeURIComponent(dist);
    return url;
}

async function exportEwayBill(id) {
    try {
        if (!(document.getElementById('partyAddr') && document.getElementById('partyAddr').value.trim())) {
            return alert('Please enter the party Address before generating the e-Way Bill.');
        }
        const res = await fetch('/api/voucher/ewaybill' + gstExportUrl(id));
        const text = await res.text();
        if (!res.ok) { try { return alert(JSON.parse(text).error); } catch (e) { return alert('Could not generate e-Way Bill.'); } }
        downloadBlob(text, 'eWayBill_' + id + '.json', 'application/json');
    } catch (err) { alert('Error: ' + err.message); }
}

async function exportEInvoice(id) {
    try {
        if (!(document.getElementById('partyAddr') && document.getElementById('partyAddr').value.trim())) {
            return alert('Please enter the party Address before generating the e-Invoice.');
        }
        const res = await fetch('/api/voucher/einvoice' + gstExportUrl(id));
        const text = await res.text();
        if (!res.ok) { try { return alert(JSON.parse(text).error); } catch (e) { return alert('Could not generate e-Invoice.'); } }
        downloadBlob(text, 'eInvoice_' + id + '.json', 'application/json');
    } catch (err) { alert('Error: ' + err.message); }
}

// ── GST Report (HSN summary) ────────────────────────────────────
async function loadGstReport() {
    try {
        const res = await fetch('/api/reports/gst');
        const data = await res.json();
        document.getElementById('gstTaxable').textContent = '₹' + (data.totalTaxable || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        document.getElementById('gstOutput').textContent = '₹' + (data.outputTax || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        document.getElementById('gstItc').textContent = '₹' + (data.inputTaxCredit || 0).toLocaleString('en-IN', {minimumFractionDigits:2});
        const net = data.netTax || 0;
        const netEl = document.getElementById('gstNet');
        netEl.textContent = '₹' + Math.abs(net).toLocaleString('en-IN', {minimumFractionDigits:2});
        netEl.style.color = net >= 0 ? '#ff5252' : '#00e676';
        const tbody = document.getElementById('gstTableBody');
        tbody.innerHTML = '';
        (data.rows || []).forEach(r => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td class="mono">${r.hsn}</td>
                <td class="tar mono">${r.quantity}</td>
                <td class="tar mono">₹${r.taxableValue.toLocaleString('en-IN', {minimumFractionDigits:2})}</td>
                <td class="tar mono">₹${(r.cgst + r.sgst + r.igst).toLocaleString('en-IN', {minimumFractionDigits:2})}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) { console.error(err); }
}

// ── Phone-as-trackpad/keyboard input injection ───────────────────
let inputLastSeq = -1; // -1 = "just opened": skip whatever the phone queued earlier instead of replaying it

function dispatchPointer(type, x, y) {
    const el = document.elementFromPoint(x, y);
    const target = el || document.body;
    target.dispatchEvent(new MouseEvent(type, { clientX: x, clientY: y, bubbles: true, cancelable: true, view: window }));
}

function setCursor(el, pos) {
    try { el.setSelectionRange(pos, pos); } catch (_) {}
}

function injectKey(k) {
    const el = document.activeElement;
    if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) return;
    const start = (el.selectionStart != null) ? el.selectionStart : el.value.length;
    const insert = (s) => { el.value = el.value.slice(0, start) + s + el.value.slice(start); setCursor(el, start + s.length); };
    if (k === 'BACKSPACE') {
        if (start > 0) { el.value = el.value.slice(0, start - 1) + el.value.slice(start); setCursor(el, start - 1); }
    } else if (k === 'ENTER') {
        if (el.tagName === 'INPUT') { el.blur(); } else { insert('\n'); }
    } else if (k === 'SPACE') {
        insert(' ');
    } else {
        insert(k);
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
}

// Idle: ask every couple of seconds (this also notices when the phone becomes unreachable).
// While the phone's trackpad switch is on, ask 10x a second so the pointer feels live.
const INPUT_POLL_IDLE_MS = 2500;
const INPUT_POLL_ACTIVE_MS = 100;

async function pollInputEvents() {
    let next = INPUT_POLL_IDLE_MS;
    try {
        const ctl = new AbortController();
        const timer = setTimeout(() => ctl.abort(), 4000);
        let res;
        try { res = await fetch('/api/input/events?since=' + inputLastSeq, { signal: ctl.signal }); }
        finally { clearTimeout(timer); }
        if (res.status === 401) return; // the pairing screen takes over; it reloads the page when done
        if (res.ok) {
            const data = await res.json();
            if (data.lastSeq != null) inputLastSeq = data.lastSeq;
            (data.events || []).forEach(e => {
                if (e.type === 'move') { dispatchPointer('mousemove', e.x * window.innerWidth, e.y * window.innerHeight); }
                else if (e.type === 'down') { dispatchPointer('mousedown', e.x * window.innerWidth, e.y * window.innerHeight); }
                else if (e.type === 'up') {
                    const x = e.x * window.innerWidth, y = e.y * window.innerHeight;
                    dispatchPointer('mouseup', x, y);
                    dispatchPointer('click', x, y);
                } else if (e.type === 'key') { injectKey(e.key); }
            });
            if (data.active) next = INPUT_POLL_ACTIVE_MS;
        }
    } catch (_) { next = 3000; }
    setTimeout(pollInputEvents, next);
}

// Signal the phone to show its virtual keyboard whenever a text field has focus.
let _lastKbNeeded = false;
function updateKeyboardNeeded() {
    const el = document.activeElement;
    const needs = !!(el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA'));
    if (needs !== _lastKbNeeded) {
        _lastKbNeeded = needs;
        fetch('/api/input/keyboard', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ needed: needs }) }).catch(() => {});
    }
}

if (AUTH_TOKEN) pollInputEvents();
setInterval(updateKeyboardNeeded, 400);
document.addEventListener('focusin', updateKeyboardNeeded);
document.addEventListener('focusout', () => setTimeout(updateKeyboardNeeded, 50));

// ── GSTR-1 / GSTR-3B export ─────────────────────────────────────
async function exportGstr1() {
    try {
        const res = await fetch('/api/reports/gstr1');
        const text = await res.text();
        downloadBlob(text, 'GSTR1.json', 'application/json');
    } catch (err) { alert('Error: ' + err.message); }
}

async function exportGstr3b() {
    try {
        const res = await fetch('/api/reports/gstr3b');
        const text = await res.text();
        downloadBlob(text, 'GSTR3B.json', 'application/json');
    } catch (err) { alert('Error: ' + err.message); }
}

// Initialize on Load
window.onload = () => {
    if (!AUTH_TOKEN) { showPairOverlay(); return; }
    loadCompanies();
    loadDashboard();
    loadLedgers();
    loadStockItems();
    loadUnits();
    setDaybookToday();
};
