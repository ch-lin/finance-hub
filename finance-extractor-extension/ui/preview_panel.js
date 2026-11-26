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
window.financeHubUI = {
    showPreviewPanel: function(data, nativeInstalled, platform, backendData) {
        const existingPanel = document.getElementById('finance-preview-panel');
        if (existingPanel) existingPanel.remove();

        const panel = document.createElement('div');
        panel.id = 'finance-preview-panel';
        
        let initialWidth = '600px';
        let initialHeight = '500px';
        
        let initialStyle = `
            position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%);
            width: ${initialWidth}; height: ${initialHeight}; max-width: 95vw; max-height: 95vh;
            background: #ffffff; border: 1px solid #ccc; border-radius: 8px;
            z-index: 100000; box-shadow: 0 10px 25px rgba(0,0,0,0.2);
            display: flex; flex-direction: column;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            color: #333; resize: both; overflow: hidden;
        `;

        // Restore position and size if previously saved
        const savedPos = localStorage.getItem('financeHubPreviewPos');
        if (savedPos) {
            try {
                const pos = JSON.parse(savedPos);
                if (pos.width) initialWidth = Math.max(300, pos.width) + 'px';
                if (pos.height) initialHeight = Math.max(200, pos.height) + 'px';

                const pWidth = parseInt(initialWidth) || 600;
                const pHeight = parseInt(initialHeight) || 500;
                const maxLeft = Math.max(0, window.innerWidth - pWidth);
                const maxTop = Math.max(0, window.innerHeight - pHeight);
                const safeLeft = Math.min(Math.max(0, pos.left), maxLeft);
                const safeTop = Math.min(Math.max(0, pos.top), maxTop);
                
                initialStyle = `
                    position: fixed; left: ${safeLeft}px; top: ${safeTop}px;
                    width: ${initialWidth}; height: ${initialHeight}; max-width: 95vw; max-height: 95vh;
                    background: #ffffff; border: 1px solid #ccc; border-radius: 8px;
                    z-index: 100000; box-shadow: 0 10px 25px rgba(0,0,0,0.2);
                    display: flex; flex-direction: column;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    color: #333; resize: both; overflow: hidden;
                `;
            } catch (e) {}
        }
        panel.style = initialStyle;

        const itemsHtml = data.items ? data.items.map(item => `
            <tr style="border-bottom: 1px solid #eee;">
                <td style="padding: 8px; font-size: 13px;">
                    <div style="margin-bottom: 4px;">${item.itemName}</div>
                    <div style="font-size: 11px; color: #777;">
                        ${item.asin ? `<span style="background: #f0f0f0; padding: 2px 4px; border-radius: 3px;">ID: ${item.asin}</span>` : ''}
                        ${item.seller ? `<span style="margin-left: 5px; color: #555;">Seller: ${item.seller}</span>` : ''}
                    </div>
                </td>
                <td style="padding: 8px; text-align: center; vertical-align: top;">${item.quantity}</td>
                <td style="padding: 8px; text-align: right; vertical-align: top;">￥${item.unitPrice.toLocaleString()}</td>
            </tr>
        `).join('') : '';

        const shipmentsHtml = data.shipments && data.shipments.length > 0 ? `
            <div style="margin-top: 15px; border-top: 1px dashed #ddd; padding-top: 10px;">
                <p style="margin: 0 0 5px 0; font-size: 13px;"><strong>Shipments：</strong></p>
                <ul style="margin: 0; padding-left: 20px; font-size: 12px; color: #555;">
                    ${data.shipments.map(s => `<li>${s.shippingDate || 'Unknown Date'} - ${s.carrier || 'Standard'} ${s.trackingNumber ? `(ID: ${s.trackingNumber})` : ''}</li>`).join('')}
                </ul>
            </div>
        ` : '';

        const headerColor = platform === 'amazon' ? '#FF9900' : platform === 'sofmap' ? '#005bbb' : platform === 'bank' ? '#006622' : '#BF0000';
        const platformName = platform === 'amazon' ? 'Amazon' : platform === 'sofmap' ? 'Sofmap' : platform === 'bank' ? data.bankName : 'Rakuten';

        let bannerHtml = '';
        let disableSend = false;

        if (!backendData) {
            bannerHtml = `<div style="background: #e3f2fd; color: #0d47a1; padding: 10px 20px; font-size: 13px; font-weight: bold; text-align: center; border-bottom: 1px solid #bbdefb; flex-shrink: 0;">
                ℹ️ New Order: This order was not found in the Finance Hub backend.
            </div>`;
        } else if (backendData._error) {
            bannerHtml = `<div style="background: #fff3cd; color: #856404; padding: 10px 20px; font-size: 13px; font-weight: bold; text-align: center; border-bottom: 1px solid #ffeeba; flex-shrink: 0;">
                ⚠️ 系統提示：目前無法連線至後端 (${backendData._error})，暫時無法比對與儲存資料。
            </div>`;
            disableSend = true;
        } else {
            // Do deep comparison and collect differences
            function getDifferences(a, b, path = "") {
                let diffs = [];
                if (a === b) return diffs;
                if ((a === null || a === undefined || a === "") && (b === null || b === undefined || b === "")) return diffs;
                
                // Compare numeric values correctly
                if (a !== null && b !== null && a !== "" && b !== "" && !isNaN(a) && !isNaN(b) && Number(a) === Number(b)) return diffs;
                
                if (typeof a !== 'object' || typeof b !== 'object' || a == null || b == null) {
                    diffs.push({ path: path || 'root', uiValue: a, dbValue: b });
                    return diffs;
                }
                
                if (Array.isArray(a) && Array.isArray(b)) {
                    if (a.length !== b.length) {
                        diffs.push({ path: path || 'root', uiValue: `Array(${a.length})`, dbValue: `Array(${b.length})` });
                        return diffs;
                    }
                    for (let i = 0; i < a.length; i++) {
                        diffs = diffs.concat(getDifferences(a[i], b[i], `${path}[${i}]`));
                    }
                    return diffs;
                }

                // Ignore keys that are empty, null, or undefined
                const keysA = Object.keys(a).filter(k => a[k] !== undefined && a[k] !== null && a[k] !== "" && (typeof a[k] !== 'number' || a[k] !== 0));
                const keysB = Object.keys(b).filter(k => b[k] !== undefined && b[k] !== null && b[k] !== "" && (typeof b[k] !== 'number' || b[k] !== 0));
                
                const lowerKeysA = new Set(keysA.map(k => k.toLowerCase()));
                
                // Filter out backend-only keys that shouldn't cause a mismatch if they only exist in DB
                const ignoredBackendKeys = new Set(['id', 'ordersource', 'needsreview', 'reviewreason', 'reconciledamount']);
                const filteredKeysB = keysB.filter(k => !ignoredBackendKeys.has(k.toLowerCase()) || lowerKeysA.has(k.toLowerCase()));

                const combinedKeysMap = new Map();
                for (let k of keysA) combinedKeysMap.set(k.toLowerCase(), k);
                for (let k of filteredKeysB) {
                    if (!combinedKeysMap.has(k.toLowerCase())) combinedKeysMap.set(k.toLowerCase(), k);
                }
                
                for (let lowerKey of combinedKeysMap.keys()) {
                    const displayKey = combinedKeysMap.get(lowerKey);
                    const nextPath = path ? `${path}.${displayKey}` : displayKey;
                    
                    const actualKeyA = Object.keys(a).find(k => k.toLowerCase() === lowerKey);
                    const actualKeyB = Object.keys(b).find(k => k.toLowerCase() === lowerKey);
                    
                    const valA = actualKeyA ? a[actualKeyA] : undefined;
                    const valB = actualKeyB ? b[actualKeyB] : undefined;
                    
                    diffs = diffs.concat(getDifferences(valA, valB, nextPath));
                }
                
                return diffs;
            }

            const differences = getDifferences(data, backendData);

            if (differences.length === 0) {
                bannerHtml = `<div style="background: #d4edda; color: #155724; padding: 10px 20px; font-size: 13px; font-weight: bold; text-align: center; border-bottom: 1px solid #c3e6cb; flex-shrink: 0;">
                    ✅ Data is fully synced and up-to-date with Finance Hub backend.
                </div>`;
                disableSend = true;
            } else {
                const diffListHtml = differences.map(diff => 
                    `<li style="margin-bottom: 4px;"><strong>${diff.path}:</strong> on UI <span style="color: #b12704; font-weight: bold;">${JSON.stringify(diff.uiValue)}</span>, in DB <span style="color: #007bff; font-weight: bold;">${JSON.stringify(diff.dbValue)}</span></li>`
                ).join('');

                bannerHtml = `<div style="background: #f8d7da; color: #721c24; padding: 10px 20px; font-size: 13px; border-bottom: 1px solid #f5c6cb; flex-shrink: 0; text-align: left;">
                    <div style="font-weight: bold; text-align: center; margin-bottom: 8px;">🚨 Mismatch detected! The parsed data differs from the Finance Hub backend. Please review:</div>
                    <ul style="margin: 0; padding-left: 20px; font-size: 12px; max-height: 80px; overflow-y: auto;">
                        ${diffListHtml}
                    </ul>
                </div>`;
            }
        }

        let detailsHtml = '';
        if (data.type === 'amazon_list_page') {
            bannerHtml = `<div style="background: #e3f2fd; color: #0d47a1; padding: 10px 20px; font-size: 13px; font-weight: bold; text-align: center; border-bottom: 1px solid #bbdefb; flex-shrink: 0;">
                ℹ️ Found ${data.orders.length} order(s).
            </div>`;
            disableSend = true;
            const linksHtml = data.orders.map(order => `<li style="margin-bottom: 5px;"><a href="${order.link}" target="_blank" data-card-id="${order.cardId}" class="finance-hub-order-link" style="color: #007bff; text-decoration: none; font-weight: bold;">Order ID: ${order.orderId}</a></li>`).join('');
            detailsHtml = `
                <div style="margin-bottom: 15px; font-size: 14px; flex-shrink: 0;">
                    <h4 style="margin: 0 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Printable Order Summaries</h4>
                    <ul style="margin: 0; padding-left: 20px; font-size: 13px; max-height: 250px; overflow-y: auto; word-break: break-all;">
                        ${linksHtml || '<li>No orders found</li>'}
                    </ul>
                </div>
            `;
        } else if (platform === 'bank') {
            const backendTxs = backendData && backendData.transactions ? backendData.transactions : [];
            const isSaved = (tx) => {
                return backendTxs.some(bTx => 
                    bTx.transactionDate === tx.transactionDate &&
                    bTx.description === tx.description &&
                    Number(bTx.depositAmount || 0) === Number(tx.depositAmount || 0) &&
                    Number(bTx.withdrawalAmount || 0) === Number(tx.withdrawalAmount || 0) &&
                    Number(bTx.balance || 0) === Number(tx.balance || 0)
                );
            };

            if (backendData && !backendData._error) {
                const newTxsCount = (data.transactions || []).filter(tx => !isSaved(tx)).length;
                if (newTxsCount === 0) {
                    bannerHtml = `<div style="background: #d4edda; color: #155724; padding: 10px 20px; font-size: 13px; font-weight: bold; text-align: center; border-bottom: 1px solid #c3e6cb; flex-shrink: 0;">
                        ✅ All parsed transactions are already saved in the Finance Hub backend.
                    </div>`;
                    disableSend = true; // no new data
                } else {
                    bannerHtml = `<div style="background: #cce5ff; color: #004085; padding: 10px 20px; font-size: 13px; border-bottom: 1px solid #b8daff; flex-shrink: 0; text-align: center;">
                        <div style="font-weight: bold;">ℹ️ Found ${newTxsCount} new transactions to send.</div>
                    </div>`;
                }
            }

            const txHtml = (data.transactions || []).map(tx => {
                const saved = backendData && !backendData._error && isSaved(tx);
                const badge = saved ? '<span style="background: #28a745; color: white; padding: 2px 5px; border-radius: 3px; font-size: 10px; margin-right: 5px;">Saved</span>' 
                                    : (backendData && !backendData._error ? '<span style="background: #007bff; color: white; padding: 2px 5px; border-radius: 3px; font-size: 10px; margin-right: 5px;">New</span>' : '');
                return `
                <tr style="border-bottom: 1px solid #eee; ${saved ? 'opacity: 0.7; background: #f9f9f9;' : ''}">
                    <td style="padding: 8px; font-size: 13px; white-space: nowrap;">${badge}${tx.transactionDate}</td>
                    <td style="padding: 8px; font-size: 13px;">${tx.description}</td>
                    <td style="padding: 8px; text-align: right; color: green; font-size: 13px;">${tx.depositAmount != null ? '+' + tx.depositAmount.toLocaleString() : ''}</td>
                    <td style="padding: 8px; text-align: right; color: red; font-size: 13px;">${tx.withdrawalAmount != null ? '-' + tx.withdrawalAmount.toLocaleString() : ''}</td>
                    <td style="padding: 8px; text-align: right; font-size: 13px;">${tx.balance != null ? tx.balance.toLocaleString() : ''}</td>
                </tr>
            `}).join('');

            detailsHtml = `
                <div style="display: flex; flex-direction: row; gap: 20px; margin-bottom: 15px; font-size: 14px; flex-shrink: 0;">
                    <div style="flex: 1;">
                        <h4 style="margin: 0 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Bank Statement Info</h4>
                        <p style="margin: 5px 0;"><strong>Bank Name：</strong> ${data.bankName}</p>
                        <p style="margin: 5px 0;"><strong>Statement Month：</strong> ${data.statementMonth}</p>
                        <p style="margin: 5px 0;"><strong>Transactions Count：</strong> ${(data.transactions || []).length}</p>
                    </div>
                </div>
                <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px; flex-shrink: 0;">
                    <thead>
                        <tr style="background-color: #f3f3f3; border-bottom: 1px solid #ccc;">
                            <th style="padding: 8px; text-align: left; font-size: 13px; width: 120px;">Date</th>
                            <th style="padding: 8px; text-align: left; font-size: 13px;">Description</th>
                            <th style="padding: 8px; text-align: right; font-size: 13px;">Deposit</th>
                            <th style="padding: 8px; text-align: right; font-size: 13px;">Withdrawal</th>
                            <th style="padding: 8px; text-align: right; font-size: 13px;">Balance</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${txHtml || '<tr><td colspan="5" style="text-align:center; padding: 10px; color: #999;">No transactions detected</td></tr>'}
                    </tbody>
                </table>
            `;
        } else {
            detailsHtml = `
                <div style="display: flex; flex-direction: row; gap: 20px; margin-bottom: 15px; font-size: 14px; flex-shrink: 0;">
                    <div style="flex: 1; border-right: 1px solid #eee; padding-right: 15px;">
                        <h4 style="margin: 0 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Basic Info</h4>
                        <p style="margin: 5px 0;"><strong>Order Number：</strong> ${data.orderId}</p>
                        <p style="margin: 5px 0;"><strong>Order Date：</strong> ${data.orderDate}</p>
                        <p style="margin: 5px 0;"><strong>Shipping Fee：</strong> ￥${data.shippingFee ? data.shippingFee.toLocaleString() : 0}</p>
                        <p style="margin: 5px 0;"><strong>Order Total：</strong> <span style="color: #B12704; font-weight: bold;">￥${data.totalAmount ? data.totalAmount.toLocaleString() : 0}</span></p>
                        <p style="margin: 5px 0; margin-top: 10px; font-size: 15px;"><strong>Credit Card Billed Amount：</strong> <strong style="color: #B12704;">￥${data.billedAmount ? data.billedAmount.toLocaleString() : 0}</strong></p>
                        ${shipmentsHtml}
                    </div>
                    
                    <div style="flex: 1;">
                        <h4 style="margin: 0 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Financial Details & Payments</h4>
                        ${data.discounts > 0 ? '<p style="margin: 3px 0;"><strong>Discounts：</strong> <span style="color: green;">-￥' + data.discounts.toLocaleString() + '</span></p>' : ''}
                        ${data.amazonPointsUsed > 0 ? '<p style="margin: 3px 0;"><strong>Amazon Points：</strong> <span style="color: green;">-￥' + data.amazonPointsUsed.toLocaleString() + '</span></p>' : ''}
                        ${data.rakutenPointsUsed > 0 ? '<p style="margin: 3px 0;"><strong>Rakuten Points：</strong> <span style="color: green;">-￥' + data.rakutenPointsUsed.toLocaleString() + '</span></p>' : ''}
                        ${data.pointsUsed > 0 ? '<p style="margin: 3px 0;"><strong>Sofmap Points：</strong> <span style="color: green;">-￥' + data.pointsUsed.toLocaleString() + '</span></p>' : ''}
                        ${data.dPointsUsed > 0 ? '<p style="margin: 3px 0;"><strong>dPoints/Partner：</strong> <span style="color: green;">-￥' + data.dPointsUsed.toLocaleString() + '</span></p>' : ''}
                        ${data.giftCardAmount > 0 ? '<p style="margin: 3px 0;"><strong>Gift Card：</strong> <span style="color: green;">-￥' + data.giftCardAmount.toLocaleString() + '</span></p>' : ''}
                        ${data.refundAmount > 0 ? '<p style="margin: 3px 0;"><strong>Refund Amount：</strong> <span style="color: orange;">￥' + data.refundAmount.toLocaleString() + '</span></p>' : ''}
                        
                        <p style="margin: 10px 0 5px 0;"><strong>Payments：</strong></p>
                        <ul style="margin: 0; padding-left: 20px; font-size: 13px;">
                            ${(data.payments || []).map(p => '<li>' + p.method + ': <strong>￥' + (p.amount ? p.amount.toLocaleString() : 0) + '</strong></li>').join('') || '<li>No payments detected</li>'}
                        </ul>
                    </div>
                </div>

                <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px; flex-shrink: 0;">
                    <thead>
                        <tr style="background-color: #f3f3f3; border-bottom: 1px solid #ccc;">
                            <th style="padding: 8px; text-align: left; font-size: 13px;">Product Name</th>
                            <th style="padding: 8px; width: 50px; font-size: 13px;">Qty</th>
                            <th style="padding: 8px; text-align: right; width: 80px; font-size: 13px;">Unit Price</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${itemsHtml || '<tr><td colspan="3" style="text-align:center; padding: 10px; color: #999;">No product details detected</td></tr>'}
                    </tbody>
                </table>
            `;
        }

        panel.innerHTML = `
            <div id="finance-preview-header" style="flex-shrink: 0; display: flex; justify-content: space-between; align-items: center; background: #f9f9f9; padding: 12px 20px; border-bottom: 2px solid ${headerColor}; cursor: move; user-select: none; border-radius: 8px 8px 0 0;">
                <h3 style="margin: 0; font-size: 16px; color: #111; pointer-events: none;">📊 Finance Hub - ${platformName} Data Preview</h3>
                <span id='close-panel-x' style='cursor: pointer; font-size: 22px; font-weight: bold; line-height: 1;'>&times;</span>
            </div>
            ${bannerHtml}
            
            <div style="flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column;">
                ${detailsHtml}
                
                <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: auto;">
                    ${data.type === 'amazon_list_page' ? `<button id="panel-open-all-btn" style="padding: 8px 15px; background: #007bff; color: white; border: none; border-radius: 4px; font-weight: bold; cursor: pointer;">Open All Details</button>` : ''}
                    ${data.type !== 'amazon_list_page' && nativeInstalled 
                        ? `<button id="panel-native-save-btn" style="padding: 8px 15px; background: #007bff; color: white; border: none; border-radius: 4px; font-weight: bold; cursor: pointer;" title="Uses local script to trigger OS Cmd+S">💾 Save Full Page (Cmd+S)</button>`
                        : ''
                    }
                    ${data.type !== 'amazon_list_page' && !nativeInstalled 
                        ? `<button id="panel-raw-save-btn" style="padding: 8px 15px; background: #6c757d; color: white; border: none; border-radius: 4px; font-weight: bold; cursor: pointer;" title="Downloads raw HTML only. To save images, install native host script.">💾 Save HTML (Raw Only)</button>`
                        : ''
                    }
                    <button id="panel-cancel-btn" style="padding: 8px 15px; background: #e7e7e7; border: 1px solid #ccc; border-radius: 4px; cursor: pointer;">Cancel</button>
                    ${data.type !== 'amazon_list_page' 
                        ? `<button id="panel-send-btn" ${disableSend ? 'disabled style="padding: 8px 15px; background: #a5d6a7; color: white; border: none; border-radius: 4px; font-weight: bold; cursor: not-allowed;"' : 'style="padding: 8px 15px; background: #2c974b; color: white; border: none; border-radius: 4px; font-weight: bold; cursor: pointer;"'}>Confirm & Send</button>`
                        : ''
                    }
                </div>
            </div>
        `;

        document.body.appendChild(panel);

        function savePanelState() {
            if (!document.body.contains(panel)) return;
            const rect = panel.getBoundingClientRect();
            if (rect.width === 0 || rect.height === 0) return;
            localStorage.setItem('financeHubPreviewPos', JSON.stringify({ 
                left: rect.left, 
                top: rect.top,
                width: rect.width,
                height: rect.height
            }));
        }

        // Resize tracking
        let resizeTimeout;
        const resizeObserver = new ResizeObserver(() => {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(savePanelState, 200); // debounce
        });
        resizeObserver.observe(panel);

        // Keep panel within screen bounds on window resize
        window.addEventListener('resize', () => {
            if (!document.getElementById('finance-preview-panel')) return;
            
            // Only constrain if the panel is in manual top/left mode (dragged or restored)
            if (panel.style.top) {
                const rect = panel.getBoundingClientRect();
                const maxLeft = Math.max(0, window.innerWidth - rect.width);
                const maxTop = Math.max(0, window.innerHeight - rect.height);
                
                const newLeft = Math.min(Math.max(0, rect.left), maxLeft);
                const newTop = Math.min(Math.max(0, rect.top), maxTop);
                
                panel.style.left = newLeft + 'px';
                panel.style.top = newTop + 'px';
            }
        });

        // Panel Dragging Logic
        const header = document.getElementById('finance-preview-header');
        let isPanelDragging = false, panelStartX, panelStartY, panelInitialLeft, panelInitialTop;

        header.addEventListener('mousedown', (e) => {
            if (e.target.id === 'close-panel-x') return;
            isPanelDragging = true;
            panelStartX = e.clientX;
            panelStartY = e.clientY;
            
            const rect = panel.getBoundingClientRect();
            panelInitialLeft = rect.left;
            panelInitialTop = rect.top;
            
            panel.style.left = panelInitialLeft + 'px';
            panel.style.top = panelInitialTop + 'px';
            panel.style.bottom = 'auto';
            panel.style.transform = 'none';
        });

        document.addEventListener('mousemove', (e) => {
            if (!isPanelDragging) return;
            const dx = e.clientX - panelStartX;
            const dy = e.clientY - panelStartY;
            
            let newLeft = panelInitialLeft + dx;
            let newTop = panelInitialTop + dy;
            
            const maxLeft = Math.max(0, window.innerWidth - panel.offsetWidth);
            const maxTop = Math.max(0, window.innerHeight - panel.offsetHeight);
            newLeft = Math.min(Math.max(0, newLeft), maxLeft);
            newTop = Math.min(Math.max(0, newTop), maxTop);

            panel.style.left = newLeft + 'px';
            panel.style.top = newTop + 'px';
        });

        document.addEventListener('mouseup', () => {
            if (isPanelDragging) {
                isPanelDragging = false;
                savePanelState();
            }
        });

        document.getElementById('close-panel-x').addEventListener('click', () => {
            panel.remove();
            localStorage.setItem('financeHubPanelOpen', 'false');
        });
        document.getElementById('panel-cancel-btn').addEventListener('click', () => {
            panel.remove();
            localStorage.setItem('financeHubPanelOpen', 'false');
        });
        
        const openAllBtn = document.getElementById('panel-open-all-btn');
        if (openAllBtn) {
            openAllBtn.addEventListener('click', async () => {
                const orders = data.orders;
                if (!orders || orders.length === 0) return;
                
                openAllBtn.disabled = true;
                openAllBtn.textContent = 'Opening...';
                
                const settings = await new Promise(resolve => {
                    chrome.storage.sync.get(['openAllDelay'], (result) => {
                        resolve(result);
                    });
                });
                const delayMs = settings.openAllDelay === undefined ? 1500 : settings.openAllDelay;
                
                for (let i = 0; i < orders.length; i++) {
                    window.open(orders[i].link, '_blank');
                    if (i < orders.length - 1) {
                        await new Promise(r => setTimeout(r, delayMs)); // sleep to avoid 429
                    }
                }
                
                openAllBtn.textContent = '✅ All Opened';
                openAllBtn.style.background = '#28a745';
                setTimeout(() => {
                    openAllBtn.textContent = 'Open All Details';
                    openAllBtn.style.background = '#007bff';
                    openAllBtn.disabled = false;
                }, 2000);
            });
            
            // Add hover effect for highlighting Amazon order cards
            const links = panel.querySelectorAll('.finance-hub-order-link');
            links.forEach(link => {
                link.addEventListener('mouseenter', (e) => {
                    const cardId = e.target.getAttribute('data-card-id');
                    const card = document.getElementById(cardId);
                    if (card) {
                        card.style.transition = 'all 0.3s';
                        card.style.boxShadow = '0 0 15px 5px rgba(255, 153, 0, 0.8)';
                        card.style.border = '2px solid #FF9900';
                        card.style.transform = 'scale(1.02)';
                        card.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    }
                });
                link.addEventListener('mouseleave', (e) => {
                    const cardId = e.target.getAttribute('data-card-id');
                    const card = document.getElementById(cardId);
                    if (card) {
                        card.style.boxShadow = '';
                        card.style.border = '';
                        card.style.transform = '';
                    }
                });
            });
        }
        
        const sendBtn = document.getElementById('panel-send-btn');
        if (sendBtn) {
            sendBtn.addEventListener('click', () => {
                sendBtn.disabled = true;
                sendBtn.textContent = 'Sending...';

                chrome.storage.sync.get(['backendBaseUrl', 'dryRun'], (result) => {
                let baseUrl = result.backendBaseUrl || 'http://localhost:8080';
                baseUrl = baseUrl.replace(/\/+$/, '');
                let targetUrl = baseUrl + '/' + platform + '/extension';
                const isDryRun = result.dryRun === undefined ? false : result.dryRun;
                
                try {
                    const urlObj = new URL(targetUrl);
                    urlObj.searchParams.set('dryRun', isDryRun);
                    targetUrl = urlObj.toString();
                } catch (e) {
                    targetUrl += (targetUrl.includes('?') ? '&' : '?') + 'dryRun=' + isDryRun;
                }

                fetch(targetUrl, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data)
                })
                .then(response => {
                    if (response.ok) {
                        sendBtn.textContent = '✅ Success!' + (isDryRun ? ' [DRY RUN]' : '');
                        sendBtn.style.background = '#28a745';
                        setTimeout(() => {
                            sendBtn.textContent = 'Confirm & Send';
                            sendBtn.style.background = '#2c974b';
                            sendBtn.disabled = false;
                        }, 2000);
                    } else {
                        throw new Error('Server response error: ' + response.status);
                    }
                })
                .catch(err => {
                    alert('❌ Send failed, please ensure backend service is running: ' + err.message);
                    sendBtn.disabled = false;
                    sendBtn.textContent = 'Confirm & Send';
                });
            });
        });
        }

        // Dynamic Save Buttons logic
        const nativeSaveBtn = document.getElementById('panel-native-save-btn');
        if (nativeSaveBtn) {
            nativeSaveBtn.addEventListener('click', () => {
                nativeSaveBtn.disabled = true;
                nativeSaveBtn.textContent = 'Saving...';

                const originalTitle = document.title;
                const newTitle = platform === 'bank' ? `${data.bankName}_${data.statementMonth}` : `${data.orderId}`;
                document.title = newTitle;
                
                try { navigator.clipboard.writeText(newTitle); } catch(e) {}

                try {
                    chrome.runtime.sendMessage({ action: 'triggerNativeSave' }, (response) => {
                        setTimeout(() => { document.title = originalTitle; }, 3000);

                        if (chrome.runtime.lastError) {
                            console.warn('Native save failed:', chrome.runtime.lastError.message);
                            alert('Native save failed: ' + chrome.runtime.lastError.message);
                            nativeSaveBtn.textContent = '💾 Save Full Page (Cmd+S)';
                            nativeSaveBtn.disabled = false;
                            return;
                        }

                        if (response && response.success) {
                            nativeSaveBtn.textContent = '✅ Saved (Check OS)';
                            nativeSaveBtn.style.background = '#28a745';
                            setTimeout(() => {
                                nativeSaveBtn.textContent = '💾 Save Full Page (Cmd+S)';
                                nativeSaveBtn.style.background = '#007bff';
                                nativeSaveBtn.disabled = false;
                            }, 2000);
                        } else {
                            alert('Native save failed: ' + (response ? response.error : 'Unknown error'));
                            nativeSaveBtn.textContent = '💾 Save Full Page (Cmd+S)';
                            nativeSaveBtn.disabled = false;
                        }
                    });
                } catch (error) {
                    console.error('Extension context invalidated:', error);
                    alert('Extension connection lost. Please refresh the page.');
                    setTimeout(() => { document.title = originalTitle; }, 3000);
                    nativeSaveBtn.textContent = '❌ Extension Reloaded';
                    nativeSaveBtn.style.background = '#dc3545';
                    setTimeout(() => {
                        nativeSaveBtn.textContent = '💾 Save Full Page (Cmd+S)';
                        nativeSaveBtn.style.background = '#007bff';
                        nativeSaveBtn.disabled = false;
                    }, 2000);
                }
            });
        }

        const rawSaveBtn = document.getElementById('panel-raw-save-btn');
        if (rawSaveBtn) {
            rawSaveBtn.addEventListener('click', () => {
                const htmlContent = document.documentElement.outerHTML;
                const blob = new Blob([htmlContent], { type: "text/html" });
                const url = URL.createObjectURL(blob);
                const a = document.createElement("a");
                a.href = url;
                a.download = platform === 'bank' ? `${platformName}_${data.statementMonth}.html` : `${platformName}_Order_${data.orderId}.html`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
                
                const originalText = rawSaveBtn.textContent;
                rawSaveBtn.textContent = '✅ Saved';
                rawSaveBtn.style.background = '#28a745';
                setTimeout(() => {
                    rawSaveBtn.textContent = originalText;
                    rawSaveBtn.style.background = '#6c757d';
                }, 2000);
            });
        }
    }
};
