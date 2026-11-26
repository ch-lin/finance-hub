#!/bin/bash
# =============================================================================
# Copyright (c) 2025-2026 Che-Hung Lin. All rights reserved.
#
# This software and associated documentation files (the "Software") are the
# proprietary and confidential property of Che-Hung Lin.
#
# No permission is granted to use, copy, modify, merge, publish, distribute,
# sublicense, and/or sell copies of the Software, in whole or in part, without
# the prior express written permission of the copyright holder.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
# WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
# IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
# =============================================================================

# Define Chrome NativeMessagingHosts directory for Mac
TARGET_DIR="$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts"
MANIFEST_NAME="ch.lin.finance.hub.native.json"
TARGET_FILE="$TARGET_DIR/$MANIFEST_NAME"
HOST_EXEC_NAME="ch.lin.finance.hub.native.py"
HOST_TARGET_PATH="$TARGET_DIR/$HOST_EXEC_NAME"

echo "=== Finance Hub Extension Native Host Uninstaller ==="

if [ -f "$TARGET_FILE" ]; then
    rm "$TARGET_FILE"
    echo "✅ Successfully removed $TARGET_FILE"
    
    if [ -f "$HOST_TARGET_PATH" ]; then
        rm "$HOST_TARGET_PATH"
        echo "✅ Successfully removed $HOST_TARGET_PATH"
    fi
    
    echo "The Native Host has been uninstalled. The extension will automatically revert to raw HTML save mode."
else
    echo "⚠️ Native Host manifest not found at $TARGET_FILE."
    echo "It seems it was already uninstalled or never installed."
fi
