(function () {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    const i18nSource = document.getElementById('borrowWorkspace');

    const state = {
        reader: null,
        returnQueue: [],
        nextReturnMode: 'normal'
    };

    const els = {
        commandButtons: document.querySelectorAll('[data-command]'),
        commandStatus: document.getElementById('borrowCommandStatus'),
        borrowReaderNo: document.getElementById('borrowReaderNo'),
        borrowBookIsbn: document.getElementById('borrowBookIsbn'),
        borrowSubmit: document.getElementById('borrowSubmit'),
        borrowReset: document.getElementById('borrowReset'),
        readerStatus: document.getElementById('readerStatus'),
        bookStatus: document.getElementById('bookStatus'),
        borrowFlowState: document.getElementById('borrowFlowState'),
        returnBookIsbn: document.getElementById('returnBookIsbn'),
        returnQueue: document.getElementById('returnQueue'),
        returnSummary: document.getElementById('returnSummary'),
        returnBatch: document.getElementById('returnBatch'),
        returnClear: document.getElementById('returnClear'),
        returnModeBadge: document.getElementById('returnModeBadge'),
        renewBookIsbn: document.getElementById('renewBookIsbn'),
        renewSubmit: document.getElementById('renewSubmit'),
        renewStatus: document.getElementById('renewStatus'),
        recordBody: document.getElementById('borrowRecordBody'),
        toast: document.getElementById('borrowToast')
    };

    if (!els.borrowReaderNo) {
        return;
    }

    function t(key, fallback, ...args) {
        const template = i18nSource?.dataset[key] || fallback;
        return args.reduce((text, value, index) => text.replaceAll(`{${index}}`, value), template);
    }

    document.addEventListener('DOMContentLoaded', () => {
        focusMode('borrow');
    });

    els.commandButtons.forEach((button) => {
        button.addEventListener('click', () => focusMode(button.dataset.command));
    });

    document.addEventListener('keydown', (event) => {
        if (event.key === 'F2') {
            event.preventDefault();
            focusMode('borrow');
        }
        if (event.key === 'F3') {
            event.preventDefault();
            focusMode('return');
        }
        if (event.key === 'F4') {
            event.preventDefault();
            focusMode('renew');
        }
        if (event.altKey && event.key.toLowerCase() === 'd') {
            event.preventDefault();
            setReturnMode('damaged');
        }
        if (event.altKey && event.key.toLowerCase() === 'l') {
            event.preventDefault();
            setReturnMode('lost');
        }
    });

    els.borrowReaderNo.addEventListener('keydown', async (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            await scanReader();
        }
    });

    els.borrowBookIsbn.addEventListener('keydown', async (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            await checkout();
        }
    });

    els.borrowSubmit.addEventListener('click', checkout);
    els.borrowReset.addEventListener('click', resetBorrowFlow);

    els.returnBookIsbn.addEventListener('keydown', async (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            await queueReturn();
        }
    });

    els.returnBatch.addEventListener('click', batchReturn);
    els.returnClear.addEventListener('click', () => {
        state.returnQueue = [];
        renderReturnQueue();
    });

    els.renewBookIsbn.addEventListener('keydown', async (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            await renewByScan();
        }
    });
    els.renewSubmit.addEventListener('click', renewByScan);

    els.recordBody.addEventListener('click', async (event) => {
        const button = event.target.closest('[data-row-action]');
        if (!button) {
            return;
        }
        const recordId = Number(button.dataset.recordId);
        if (button.dataset.rowAction === 'return') {
            await returnSingle(recordId);
        }
        if (button.dataset.rowAction === 'renew') {
            await renewRecord(recordId);
        }
    });

    async function scanReader() {
        const readerNo = els.borrowReaderNo.value.trim();
        if (!readerNo) {
            showToast('error', t('inputReaderNo', '请输入读者证号'));
            beep();
            return;
        }
        try {
            setStatus(els.readerStatus, 'loading', t('identifyingReader', '正在识别读者...'));
            const data = await apiGet(`/borrow/api/readers/${encodeURIComponent(readerNo)}`);
            state.reader = data.reader;
            renderReader(data.reader);
            if (data.reader.borrowAllowed) {
                els.borrowBookIsbn.disabled = false;
                els.borrowSubmit.disabled = false;
                els.borrowBookIsbn.focus();
                els.borrowFlowState.textContent = t('waitBook', '等待图书');
            } else {
                els.borrowBookIsbn.disabled = true;
                els.borrowSubmit.disabled = true;
                els.borrowReaderNo.select();
                beep();
            }
        } catch (error) {
            state.reader = null;
            setStatus(els.readerStatus, 'error', error.message);
            els.borrowBookIsbn.disabled = true;
            els.borrowSubmit.disabled = true;
            els.borrowReaderNo.value = '';
            els.borrowReaderNo.focus();
            beep();
        }
    }

    async function checkout() {
        if (!state.reader || !state.reader.borrowAllowed) {
            await scanReader();
            if (!state.reader || !state.reader.borrowAllowed) {
                return;
            }
        }
        const isbn = els.borrowBookIsbn.value.trim();
        if (!isbn) {
            showToast('error', t('inputBookIsbn', '请输入图书 ISBN'));
            beep();
            return;
        }
        try {
            els.borrowSubmit.disabled = true;
            setStatus(els.bookStatus, 'loading', t('validatingCheckout', '正在校验并借出...'));
            const data = await apiPostForm('/borrow/api/checkout', {
                readerNo: state.reader.readerNo,
                isbn
            });
            renderReader(data.reader);
            renderBorrowResult(data);
            upsertRecordRow(data.record, true);
            showToast('success', data.message);
            resetBorrowFlow(true);
        } catch (error) {
            setStatus(els.bookStatus, 'error', error.message);
            els.borrowBookIsbn.value = '';
            els.borrowBookIsbn.focus();
            els.borrowSubmit.disabled = false;
            beep();
        }
    }

    async function queueReturn() {
        const isbn = els.returnBookIsbn.value.trim();
        if (!isbn) {
            showToast('error', t('inputBookIsbn', '请输入图书 ISBN'));
            beep();
            return;
        }
        try {
            const data = await apiGet(`/borrow/api/returns/resolve?isbn=${encodeURIComponent(isbn)}`);
            const alreadyQueued = state.returnQueue.some((item) => item.record.id === data.record.id);
            if (alreadyQueued) {
                showToast('error', t('alreadyQueued', '该记录已在待还队列中'));
                beep();
            } else {
                state.returnQueue.push({
                    record: data.record,
                    estimatedFine: Number(data.estimatedFine || 0),
                    mode: state.nextReturnMode
                });
                renderReturnQueue();
                showToast('success', data.message);
            }
            setReturnMode('normal');
            els.returnBookIsbn.value = '';
            els.returnBookIsbn.focus();
        } catch (error) {
            showToast('error', error.message);
            els.returnBookIsbn.value = '';
            els.returnBookIsbn.focus();
            beep();
        }
    }

    async function batchReturn() {
        if (state.returnQueue.length === 0) {
            showToast('error', t('returnQueueEmpty', '待还队列为空'));
            beep();
            return;
        }
        const items = state.returnQueue.map((item) => ({
            recordId: item.record.id,
            damaged: item.mode === 'damaged',
            lost: item.mode === 'lost'
        }));
        try {
            els.returnBatch.disabled = true;
            const data = await apiPostJson('/borrow/api/returns/batch', { items });
            data.processed.forEach((record) => upsertRecordRow(record, false));
            const processedIds = new Set(data.processed.map((record) => record.id));
            state.returnQueue = state.returnQueue.filter((item) => !processedIds.has(item.record.id));
            renderReturnQueue();
            showToast(data.errors && data.errors.length ? 'error' : 'success', data.message);
            if (data.errors && data.errors.length) {
                beep();
            }
        } catch (error) {
            showToast('error', error.message);
            beep();
        } finally {
            renderReturnQueue();
            els.returnBookIsbn.focus();
        }
    }

    async function returnSingle(recordId) {
        try {
            const data = await apiPostJson('/borrow/api/returns/batch', {
                items: [{ recordId, damaged: false, lost: false }]
            });
            data.processed.forEach((record) => upsertRecordRow(record, false));
            showToast(data.errors && data.errors.length ? 'error' : 'success', data.message);
        } catch (error) {
            showToast('error', error.message);
            beep();
        }
    }

    async function renewByScan() {
        const isbn = els.renewBookIsbn.value.trim();
        if (!isbn) {
            showToast('error', t('inputBookIsbn', '请输入图书 ISBN'));
            beep();
            return;
        }
        try {
            setStatus(els.renewStatus, 'loading', t('renewing', '正在续借...'));
            const data = await apiPostForm('/borrow/api/renew', { isbn });
            setStatus(els.renewStatus, 'success', t('renewedUntil', '{0} 已续借至 {1}', data.record.bookTitle, data.record.dueDate));
            upsertRecordRow(data.record, false);
            els.renewBookIsbn.value = '';
            els.renewBookIsbn.focus();
            showToast('success', data.message);
        } catch (error) {
            setStatus(els.renewStatus, 'error', error.message);
            els.renewBookIsbn.value = '';
            els.renewBookIsbn.focus();
            beep();
        }
    }

    async function renewRecord(recordId) {
        try {
            const data = await apiPostForm(`/borrow/api/renew/${recordId}`, {});
            upsertRecordRow(data.record, false);
            showToast('success', data.message);
        } catch (error) {
            showToast('error', error.message);
            beep();
        }
    }

    function renderReader(reader) {
        const blockers = reader.blockers && reader.blockers.length
            ? `<div class="scan-blockers">${reader.blockers.map(escapeHtml).join('，')}</div>`
            : '';
        els.readerStatus.className = `scan-status ${reader.borrowAllowed ? 'success' : 'warning'}`;
        els.readerStatus.innerHTML = `
            <strong>${escapeHtml(reader.readerNo)} ${escapeHtml(reader.name)}</strong>
            <dl>
                <div><dt>${escapeHtml(t('member', '会员'))}</dt><dd>${escapeHtml(reader.memberLevel)}</dd></div>
                <div><dt>${escapeHtml(t('activeBorrow', '在借'))}</dt><dd>${reader.activeBorrowCount}/${reader.maxBorrowBooks}</dd></div>
                <div><dt>${escapeHtml(t('deposit', '押金'))}</dt><dd>${formatMoney(reader.depositAmount)}</dd></div>
                <div><dt>${escapeHtml(t('status', '状态'))}</dt><dd>${escapeHtml(reader.status)}</dd></div>
            </dl>
            ${blockers}
        `;
    }

    function renderBorrowResult(data) {
        els.bookStatus.className = 'scan-status success';
        els.bookStatus.innerHTML = `
            <strong>${escapeHtml(data.record.bookTitle)}</strong>
            <dl>
                <div><dt>ISBN</dt><dd>${escapeHtml(data.record.bookIsbn)}</dd></div>
                <div><dt>${escapeHtml(t('due', '应还'))}</dt><dd>${data.record.dueDate}</dd></div>
                <div><dt>${escapeHtml(t('reader', '读者'))}</dt><dd>${escapeHtml(data.record.readerName)}</dd></div>
            </dl>
        `;
    }

    function resetBorrowFlow(keepResult) {
        state.reader = null;
        els.borrowReaderNo.value = '';
        els.borrowBookIsbn.value = '';
        els.borrowBookIsbn.disabled = true;
        els.borrowSubmit.disabled = true;
        els.borrowFlowState.textContent = t('waitReader', '等待读者');
        if (!keepResult) {
            setStatus(els.readerStatus, 'empty', t('waitReaderScan', '等待读者扫描'));
            setStatus(els.bookStatus, 'empty', t('waitBookScan', '等待图书扫描'));
        }
        els.borrowReaderNo.focus();
    }

    function renderReturnQueue() {
        if (state.returnQueue.length === 0) {
            els.returnQueue.innerHTML = `<div class="queue-empty">${escapeHtml(t('returnQueueEmpty', '待还队列为空'))}</div>`;
        } else {
            els.returnQueue.innerHTML = state.returnQueue.map((item, index) => `
                <div class="queue-item" data-index="${index}">
                    <div>
                        <strong>${escapeHtml(item.record.bookTitle)}</strong>
                        <span>${escapeHtml(item.record.readerNo)} ${escapeHtml(item.record.readerName)}</span>
                    </div>
                    <div class="queue-meta">
                        <span>${escapeHtml(t('due', '应还'))} ${item.record.dueDate}</span>
                        <span>${escapeHtml(t('estimatedFine', '预计罚款'))} ${formatMoney(item.estimatedFine)}</span>
                        <button class="mode-chip ${item.mode}" type="button" data-queue-mode="${index}">${modeLabel(item.mode)}</button>
                        <button class="queue-remove" type="button" data-queue-remove="${index}">${escapeHtml(t('remove', '移除'))}</button>
                    </div>
                </div>
            `).join('');
        }
        els.returnQueue.querySelectorAll('[data-queue-remove]').forEach((button) => {
            button.addEventListener('click', () => {
                state.returnQueue.splice(Number(button.dataset.queueRemove), 1);
                renderReturnQueue();
            });
        });
        els.returnQueue.querySelectorAll('[data-queue-mode]').forEach((button) => {
            button.addEventListener('click', () => {
                const item = state.returnQueue[Number(button.dataset.queueMode)];
                item.mode = item.mode === 'normal' ? 'damaged' : item.mode === 'damaged' ? 'lost' : 'normal';
                renderReturnQueue();
            });
        });
        const totalFine = state.returnQueue.reduce((sum, item) => sum + Number(item.estimatedFine || 0), 0);
        els.returnSummary.textContent = t('returnSummary', '待还 {0} 本，预计罚款 {1}', state.returnQueue.length, formatMoney(totalFine));
        els.returnBatch.disabled = state.returnQueue.length === 0;
    }

    function setReturnMode(mode) {
        state.nextReturnMode = mode;
        els.returnModeBadge.textContent = modeLabel(mode);
        els.returnModeBadge.className = `status-pill ${mode}`;
        if (mode !== 'normal') {
            showToast('success', t('nextMarked', '下一本将标记为{0}', modeLabel(mode)));
        }
    }

    function focusMode(mode) {
        els.commandButtons.forEach((button) => {
            button.classList.toggle('active', button.dataset.command === mode);
        });
        document.querySelectorAll('[data-panel]').forEach((panel) => {
            panel.classList.toggle('active-panel', panel.dataset.panel === mode);
        });
        if (mode === 'borrow') {
            els.borrowReaderNo.focus();
            els.commandStatus.textContent = t('scanBorrow', '扫码借书');
        }
        if (mode === 'return') {
            els.returnBookIsbn.focus();
            els.commandStatus.textContent = t('scanReturn', '扫码还书');
        }
        if (mode === 'renew') {
            els.renewBookIsbn.focus();
            els.commandStatus.textContent = t('quickRenew', '快速续借');
        }
    }

    function upsertRecordRow(record, prepend) {
        const existing = els.recordBody.querySelector(`tr[data-record-id="${record.id}"]`);
        const row = existing || document.createElement('tr');
        row.dataset.recordId = record.id;
        row.dataset.recordStatus = record.status;
        row.dataset.bookIsbn = record.bookIsbn;
        row.innerHTML = `
            <td data-cell="book">${escapeHtml(record.bookTitle)}</td>
            <td data-cell="reader">${escapeHtml(record.readerNo)} ${escapeHtml(record.readerName)}</td>
            <td data-cell="borrowDate">${record.borrowDate || '-'}</td>
            <td data-cell="dueDate">${record.dueDate || '-'}</td>
            <td data-cell="returnDate">${record.returnDate || '-'}</td>
            <td data-cell="status">${escapeHtml(record.statusLabel)}</td>
            <td data-cell="fine">${formatMoney(record.fineAmount)}</td>
            <td class="table-actions" data-cell="actions">${recordActions(record)}</td>
        `;
        if (!existing) {
            if (prepend && els.recordBody.firstChild) {
                els.recordBody.insertBefore(row, els.recordBody.firstChild);
            } else {
                els.recordBody.appendChild(row);
            }
        }
    }

    function recordActions(record) {
        const buttons = [];
        if (record.status === 'BORROWED') {
            buttons.push(`<button class="layui-btn layui-btn-xs" type="button" data-row-action="renew" data-record-id="${record.id}">${escapeHtml(t('renew', '续借'))}</button>`);
        }
        if (record.status === 'BORROWED' || record.status === 'OVERDUE') {
            buttons.push(`<button class="layui-btn layui-btn-xs layui-btn-normal" type="button" data-row-action="return" data-record-id="${record.id}">${escapeHtml(t('return', '归还'))}</button>`);
        }
        return buttons.join('');
    }

    function setStatus(element, type, text) {
        element.className = `scan-status ${type}`;
        element.textContent = text;
    }

    async function apiGet(url) {
        const response = await fetch(url, { credentials: 'same-origin' });
        return parseResponse(response);
    }

    async function apiPostForm(url, values) {
        const body = new URLSearchParams();
        Object.entries(values).forEach(([key, value]) => body.append(key, value));
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                [csrfHeader]: csrfToken
            },
            credentials: 'same-origin',
            body
        });
        return parseResponse(response);
    }

    async function apiPostJson(url, payload) {
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            credentials: 'same-origin',
            body: JSON.stringify(payload)
        });
        return parseResponse(response);
    }

    async function parseResponse(response) {
        const data = await response.json().catch(() => null);
        if (!response.ok || (data && data.success === false)) {
            throw new Error(data?.message || t('operationFailed', '操作失败'));
        }
        return data;
    }

    function showToast(type, text) {
        els.toast.textContent = text;
        els.toast.className = `borrow-toast show ${type}`;
        window.clearTimeout(showToast.timer);
        showToast.timer = window.setTimeout(() => {
            els.toast.className = 'borrow-toast';
        }, 2600);
    }

    function beep() {
        try {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            const context = new AudioContext();
            const oscillator = context.createOscillator();
            const gain = context.createGain();
            oscillator.type = 'square';
            oscillator.frequency.value = 220;
            gain.gain.value = 0.03;
            oscillator.connect(gain);
            gain.connect(context.destination);
            oscillator.start();
            window.setTimeout(() => {
                oscillator.stop();
                context.close();
            }, 120);
        } catch (ignored) {
            // Browsers may block audio until the page has user interaction.
        }
    }

    function modeLabel(mode) {
        if (mode === 'damaged') {
            return t('damaged', '损坏');
        }
        if (mode === 'lost') {
            return t('lost', '丢失');
        }
        return t('normalReturn', '正常归还');
    }

    function formatMoney(value) {
        const number = Number(value || 0);
        return Number.isFinite(number) ? number.toFixed(2) : '0.00';
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
})();
