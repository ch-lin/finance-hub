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
(function () {
    'use strict';

    async function fetchBackendData(orderData, platform) {
        return new Promise((resolve) => {
            try {
                chrome.storage.sync.get(['backendBaseUrl'], (result) => {
                    if (chrome.runtime.lastError) {
                        console.warn("Storage sync failed:", chrome.runtime.lastError);
                        resolve(null);
                        return;
                    }
                    const baseUrl = result.backendBaseUrl || 'http://localhost:8080';
                    let endpoint;
                    if (platform === 'bank') {
                        endpoint = `${baseUrl}/bank/extension/${orderData.bankName}/${orderData.statementMonth}`;
                    } else {
                        endpoint = `${baseUrl}/${platform}/extension/${orderData.orderId}`;
                    }
                    const controller = new AbortController();
                    const timeoutId = setTimeout(() => controller.abort(), 3000); // 3 seconds timeout

                    fetch(endpoint, { signal: controller.signal })
                        .then(res => {
                            clearTimeout(timeoutId);
                            if (!res.ok) {
                                if (res.status === 404) return null; // New order
                                throw new Error(`HTTP error! status: ${res.status}`);
                            }
                            return res.json();
                        })
                        .then(data => resolve(data))
                        .catch(err => {
                            clearTimeout(timeoutId);
                            console.warn("Failed to fetch backend data:", err);
                            resolve({ _error: err.name === 'AbortError' ? 'Connection Timeout' : err.message }); // Pass error for UI
                        });
                });
            } catch (e) {
                console.warn("Extension context invalidated during fetch:", e);
                resolve(null);
            }
        });
    }

    if (document.getElementById('finance-hub-trigger-btn')) return;

    const triggerBtn = document.createElement('button');
    triggerBtn.id = 'finance-hub-trigger-btn';
    triggerBtn.textContent = '🔍 Preview & Send to Finance Hub';
    
    // Set appropriate button color based on platform
    let bgColor = '#BF0000'; // Default Rakuten
    if (window.financeHubParser && window.financeHubParser.platform === 'amazon') {
        bgColor = '#FF9900';
    } else if (window.financeHubParser && window.financeHubParser.platform === 'sofmap') {
        bgColor = '#005bbb';
    }

    let btnStyle = `
        position: fixed; bottom: 30px; right: 30px; z-index: 99999;
        background-color: ${bgColor}; color: white; border: none;
        padding: 12px 18px; font-weight: bold; border-radius: 5px;
        cursor: move; box-shadow: 0 4px 6px rgba(0,0,0,0.3);
        user-select: none;
    `;

    const savedBtnPos = localStorage.getItem('financeHubTriggerBtnPos');
    if (savedBtnPos) {
        try {
            const pos = JSON.parse(savedBtnPos);
            const maxLeft = window.innerWidth - 50;
            const maxTop = window.innerHeight - 50;
            const safeLeft = Math.min(Math.max(0, pos.left), maxLeft);
            const safeTop = Math.min(Math.max(0, pos.top), maxTop);
            
            btnStyle = `
                position: fixed; left: ${safeLeft}px; top: ${safeTop}px; z-index: 99999;
                background-color: ${bgColor}; color: white; border: none;
                padding: 12px 18px; font-weight: bold; border-radius: 5px;
                cursor: move; box-shadow: 0 4px 6px rgba(0,0,0,0.3);
                user-select: none;
            `;
        } catch (e) {}
    }

    triggerBtn.style = btnStyle;
    document.body.appendChild(triggerBtn);

    // Draggable logic
    let isDragging = false, isDragged = false, startX, startY;

    triggerBtn.addEventListener('mousedown', (e) => {
        isDragging = true;
        isDragged = false;
        startX = e.clientX - triggerBtn.getBoundingClientRect().left;
        startY = e.clientY - triggerBtn.getBoundingClientRect().top;
        triggerBtn.style.transition = 'none'; // disable transitions while dragging
    });

    document.addEventListener('mousemove', (e) => {
        if (!isDragging) return;
        isDragged = true;
        
        let newLeft = e.clientX - startX;
        let newTop = e.clientY - startY;
        
        const maxLeft = window.innerWidth - triggerBtn.offsetWidth;
        const maxTop = window.innerHeight - triggerBtn.offsetHeight;
        
        newLeft = Math.min(Math.max(0, newLeft), maxLeft);
        newTop = Math.min(Math.max(0, newTop), maxTop);

        triggerBtn.style.right = 'auto';
        triggerBtn.style.bottom = 'auto';
        triggerBtn.style.left = newLeft + 'px';
        triggerBtn.style.top = newTop + 'px';
    });

    document.addEventListener('mouseup', () => {
        if (isDragging) {
            isDragging = false;
            if (isDragged) {
                const rect = triggerBtn.getBoundingClientRect();
                localStorage.setItem('financeHubTriggerBtnPos', JSON.stringify({ left: rect.left, top: rect.top }));
            }
        }
    });

    window.addEventListener('resize', () => {
        if (!document.getElementById('finance-hub-trigger-btn')) return;
        if (triggerBtn.style.top) {
            const rect = triggerBtn.getBoundingClientRect();
            const maxLeft = Math.max(0, window.innerWidth - rect.width);
            const maxTop = Math.max(0, window.innerHeight - rect.height);
            
            const newLeft = Math.min(Math.max(0, rect.left), maxLeft);
            const newTop = Math.min(Math.max(0, rect.top), maxTop);
            
            triggerBtn.style.left = newLeft + 'px';
            triggerBtn.style.top = newTop + 'px';
        }
    });

    triggerBtn.addEventListener('click', async (e) => {
        if (isDragged) {
            e.preventDefault();
            e.stopPropagation();
            // Reset state
            isDragged = false;
            return;
        }

        const existingPanel = document.getElementById('finance-preview-panel');
        if (existingPanel) {
            existingPanel.remove();
            localStorage.setItem('financeHubPanelOpen', 'false');
            return;
        }

        try {
            if (!window.financeHubParser) {
                throw new Error("No parser found for this page.");
            }
            const orderData = window.financeHubParser.parse();
            const platform = window.financeHubParser.platform;
            
            // Show loading or just wait? We'll await before opening panel.
            const backendData = await fetchBackendData(orderData, platform);

            try {
                chrome.runtime.sendMessage({ action: 'pingNativeHost' }, (response) => {
                    if (chrome.runtime.lastError) {
                        console.warn("pingNativeHost failed:", chrome.runtime.lastError.message);
                    }
                    const nativeInstalled = response && response.installed;
                    if (window.financeHubUI) {
                        window.financeHubUI.showPreviewPanel(orderData, nativeInstalled, platform, backendData);
                        localStorage.setItem('financeHubPanelOpen', 'true');
                    } else {
                        console.error("Finance Hub UI not loaded.");
                    }
                });
            } catch (sendMessageError) {
                console.warn('Extension context invalidated:', sendMessageError);
                // Fallback: assume no native host and show panel anyway
                if (window.financeHubUI) {
                    window.financeHubUI.showPreviewPanel(orderData, false, platform, backendData);
                    localStorage.setItem('financeHubPanelOpen', 'true');
                }
            }
        } catch (error) {
            alert('Parsing failed: ' + error.message);
            console.error(error);
        }
    });

    if (localStorage.getItem('financeHubPanelOpen') === 'true') {
        (async () => {
            try {
                if (window.financeHubParser && window.financeHubUI) {
                    const orderData = window.financeHubParser.parse();
                    const platform = window.financeHubParser.platform;
                    const backendData = await fetchBackendData(orderData, platform);
                    
                    try {
                        chrome.runtime.sendMessage({ action: 'pingNativeHost' }, (response) => {
                            if (chrome.runtime.lastError) {
                                console.warn("pingNativeHost failed:", chrome.runtime.lastError.message);
                            }
                            const nativeInstalled = response && response.installed;
                            window.financeHubUI.showPreviewPanel(orderData, nativeInstalled, platform, backendData);
                        });
                    } catch (sendMessageError) {
                        console.warn('Extension context invalidated:', sendMessageError);
                        window.financeHubUI.showPreviewPanel(orderData, false, platform, backendData);
                    }
                }
            } catch (e) {
                // silent fail
            }
        })();
    }
})();
