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

echo "=== Finance Hub Extension Native Host Installer ==="
echo "This script configures the native messaging host to allow the Chrome extension to trigger Cmd+S."
echo "Before proceeding, please make sure the extension is loaded in Chrome."
echo ""

# Prompt for Extension ID
read -p "Enter your Chrome Extension ID (found in chrome://extensions/): " EXT_ID

if [ -z "$EXT_ID" ]; then
    echo "❌ Extension ID is required. Installation aborted."
    exit 1
fi

mkdir -p "$TARGET_DIR"

# Get absolute path of python script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
HOST_PATH="$SCRIPT_DIR/native-host.py"

HOST_EXEC_NAME="ch.lin.finance.hub.native.py"
HOST_TARGET_PATH="$TARGET_DIR/$HOST_EXEC_NAME"

# Copy python script to the safe Chrome directory and make it executable
cp "$HOST_PATH" "$HOST_TARGET_PATH"
chmod +x "$HOST_TARGET_PATH"

# Create the JSON manifest from template
cat <<EOF > "$TARGET_FILE"
{
  "name": "ch.lin.finance.hub.native",
  "description": "Native Host for Finance Hub Chrome Extension",
  "path": "$HOST_TARGET_PATH",
  "type": "stdio",
  "allowed_origins": [
    "chrome-extension://$EXT_ID/"
  ]
}
EOF

echo ""
echo "✅ Installation complete!"
echo "Manifest written to: $TARGET_FILE"
echo "Host script installed at: $HOST_TARGET_PATH"
echo "You can now reload the extension page and open the Preview Panel to test the Native Save button."
