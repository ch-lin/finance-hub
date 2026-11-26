/*=============================================================================
 * Copyright (c) 2025-2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin.
 *
 * No permission is granted to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, in whole or in part, without
 * the prior express written permission of the copyright holder.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *===========================================================================*/

/**
 * MUFG Parser for Finance Hub Extension
 */

const yearPattern = /(\d{4})年/;
const monthDayPattern = /(\d{1,2})\/(\d{1,2})/;

function parseInteger(text) {
    if (!text) return null;
    const clean = text.replace(/[^0-9]/g, '');
    if (clean === '') return null;
    return parseInt(clean, 10);
}

function parseMufg() {
    const transactions = [];
    const rows = document.querySelectorAll('.add-tbl-01 tbody tr');
    let currentYear = new Date().getFullYear();

    for (const row of rows) {
        const tds = row.querySelectorAll('td');

        // Year header row
        if (tds.length === 1 && row.classList.contains('noline')) {
            const match = tds[0].textContent.match(yearPattern);
            if (match) {
                currentYear = parseInt(match[1], 10);
            }
            continue;
        }

        // Transaction row
        if (tds.length >= 6) {
            try {
                // Date
                const dateMatch = tds[0].textContent.match(monthDayPattern);
                let transactionDate = null;
                if (dateMatch) {
                    const month = dateMatch[1].padStart(2, '0');
                    const day = dateMatch[2].padStart(2, '0');
                    transactionDate = `${currentYear}-${month}-${day}`;
                }

                if (!transactionDate) continue;

                // Amount
                const withdrawalAmount = parseInteger(tds[1].textContent);
                const depositAmount = parseInteger(tds[2].textContent);

                // Description
                let description = tds[3].textContent.trim();
                if (!description) description = 'Unknown';

                // Balance
                const balance = parseInteger(tds[4].textContent);

                // Memo
                let memo = null;
                const memoInput = tds[5].querySelector('input[type=text]');
                if (memoInput) {
                    memo = memoInput.value.trim();
                } else {
                    memo = tds[5].textContent.replace('メモ編集', '').trim();
                }

                const tx = {
                    bankName: 'MUFG',
                    transactionDate: transactionDate,
                    description: description,
                    depositAmount: depositAmount,
                    withdrawalAmount: withdrawalAmount,
                    balance: balance
                };

                if (memo) {
                    tx.memo = memo;
                }

                transactions.push(tx);
            } catch (e) {
                console.warn('Failed to parse MUFG transaction row', e);
            }
        }
    }

    // Determine a statementMonth from the first transaction or current date
    let statementMonth = new Date().toISOString().slice(0, 7);
    if (transactions.length > 0) {
        statementMonth = transactions[0].transactionDate.slice(0, 7);
    }

    return { 
        bankName: 'MUFG',
        statementMonth: statementMonth,
        transactions 
    };
}

window.financeHubParser = {
    parse: parseMufg,
    platform: 'bank'
};

// Auto-trigger preview when table is loaded (optional but good for UX)
if (window.location.hostname.includes('bk.mufg.jp')) {
    const observer = new MutationObserver((mutations, obs) => {
        const table = document.querySelector('.add-tbl-01 tbody tr');
        if (table) {
            obs.disconnect(); // Stop observing
            // We just let the trigger button in finance_hub_core.js do the work, 
            // or we can auto-click it.
        }
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });
}
