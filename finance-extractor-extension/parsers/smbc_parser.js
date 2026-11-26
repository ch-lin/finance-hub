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
 * SMBC Parser for Finance Hub Extension
 */

const currentYear = new Date().getFullYear();
const yearMonthPattern = /(\d{4})年(\d{1,2})月/;
const monthDayPattern = /(\d{1,2})月(\d{1,2})日/;

/**
 * Parses an amount string to integer.
 * @param {string} text 
 * @returns {number|null}
 */
function parseInteger(text) {
    if (!text) return null;
    const clean = text.replace(/[^0-9-]/g, '');
    if (clean === '' || clean === '-') return null;
    return parseInt(clean, 10);
}

function parseSmbc() {
    const transactions = [];
    const monthSections = document.querySelectorAll('.js-meisaiMonth');

    for (const monthSection of monthSections) {
        let year = currentYear;
        const monthHeader = monthSection.querySelector('.js-dispDateMonth');
        if (monthHeader) {
            const ymMatch = monthHeader.textContent.match(yearMonthPattern);
            if (ymMatch) {
                year = parseInt(ymMatch[1], 10);
            }
        }

        const rows = monthSection.querySelectorAll('dd.detail.js-meisai');
        for (const row of rows) {
            try {
                // Description
                const descEl = row.querySelector('.js-comment');
                const description = descEl ? descEl.textContent.trim() : 'Unknown';

                // Date
                const dateEl = row.querySelector('.js-dispDate');
                let transactionDate = new Date().toISOString().split('T')[0]; // fallback
                if (dateEl) {
                    const mdMatch = dateEl.textContent.match(monthDayPattern);
                    if (mdMatch) {
                        const month = mdMatch[1].padStart(2, '0');
                        const day = mdMatch[2].padStart(2, '0');
                        transactionDate = `${year}-${month}-${day}`;
                    }
                }

                // Amount
                let depositAmount = null;
                let withdrawalAmount = null;
                
                const minusEl = row.querySelector('.numPrice.-minus.js-amount');
                if (minusEl) {
                    withdrawalAmount = parseInteger(minusEl.textContent);
                } else {
                    const plusEl = row.querySelector('.numPrice.-plus.js-amount');
                    if (plusEl) {
                        depositAmount = parseInteger(plusEl.textContent);
                    }
                }

                // Balance
                const balanceEl = row.querySelector('.js-torihikigobalance');
                const balance = balanceEl ? parseInteger(balanceEl.textContent) : null;

                // Memo
                const memoEl = row.querySelector('.js-meisaiMemoDisp');
                const memo = memoEl ? memoEl.textContent.trim() : null;

                const tx = {
                    bankName: 'SMBC',
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
                console.warn('Failed to parse SMBC transaction', e);
            }
        }
    }

    // Determine a statementMonth from the first transaction or current date
    let statementMonth = new Date().toISOString().slice(0, 7);
    if (transactions.length > 0) {
        statementMonth = transactions[0].transactionDate.slice(0, 7);
    }

    return { 
        bankName: 'SMBC',
        statementMonth: statementMonth,
        transactions 
    };
}

window.financeHubParser = {
    parse: parseSmbc,
    platform: 'bank'
};
