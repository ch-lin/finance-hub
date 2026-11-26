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
document.addEventListener('DOMContentLoaded', () => {
    const urlInput = document.getElementById('backendBaseUrl');
    const dryRunInput = document.getElementById('dryRun');
    const delayInput = document.getElementById('openAllDelay');
    const saveBtn = document.getElementById('saveBtn');
    const statusDiv = document.getElementById('status');
    const defaultBaseUrl = "http://localhost:8080";

    // Load saved Base URL, dryRun state, and openAllDelay
    chrome.storage.sync.get(['backendBaseUrl', 'dryRun', 'openAllDelay'], (result) => {
        if (result.backendBaseUrl) {
            urlInput.value = result.backendBaseUrl;
        } else {
            urlInput.value = defaultBaseUrl;
        }
        
        // Default dryRun to false if not set
        dryRunInput.checked = result.dryRun === undefined ? false : result.dryRun;
        
        // Default openAllDelay to 1500 if not set
        delayInput.value = result.openAllDelay === undefined ? 500 : result.openAllDelay;
    });

    // Save Base URL, dryRun state, and openAllDelay
    saveBtn.addEventListener('click', () => {
        let newUrl = urlInput.value.trim();
        if (!newUrl) {
            newUrl = defaultBaseUrl;
            urlInput.value = newUrl;
        }
        
        const isDryRun = dryRunInput.checked;
        const delayValue = parseInt(delayInput.value, 10);
        const openAllDelay = isNaN(delayValue) || delayValue < 0 ? 500 : delayValue;

        chrome.storage.sync.set({ backendBaseUrl: newUrl, dryRun: isDryRun, openAllDelay: openAllDelay }, () => {
            statusDiv.style.display = 'block';
            setTimeout(() => {
                statusDiv.style.display = 'none';
            }, 2000);
        });
    });
});
