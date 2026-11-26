#!/usr/bin/python3
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
import sys
import json
import struct
import subprocess
import logging
import traceback

# Setup logging
logging.basicConfig(
    filename='/tmp/finance-native-host.log',
    level=logging.DEBUG,
    format='%(asctime)s %(levelname)s: %(message)s'
)

def read_message():
    raw_length = sys.stdin.buffer.read(4)
    if not raw_length:
        sys.exit(0)
    message_length = struct.unpack('@I', raw_length)[0]
    message = sys.stdin.buffer.read(message_length).decode('utf-8')
    return json.loads(message)

def send_message(message_dict):
    encoded = json.dumps(message_dict).encode('utf-8')
    sys.stdout.buffer.write(struct.pack('@I', len(encoded)))
    sys.stdout.buffer.write(encoded)
    sys.stdout.buffer.flush()

def trigger_save():
    script = '''
    tell application "System Events"
        set frontApp to first application process whose frontmost is true
        set frontAppName to name of frontApp
        log "Frontmost app is: " & frontAppName
        delay 0.2
        tell process frontAppName
            keystroke "s" using command down
        end tell
    end tell
    '''
    return subprocess.run(['osascript', '-e', script], capture_output=True, text=True)

def main():
    logging.info("Native host started")
    while True:
        try:
            msg = read_message()
            logging.info(f"Received message: {msg}")
            
            if msg.get('command') == 'ping':
                send_message({"status": "ok", "installed": True})
            elif msg.get('command') == 'save_page':
                logging.info("Triggering save...")
                res = trigger_save()
                logging.info(f"Save script finished with returncode={res.returncode}, stderr={res.stderr}")
                
                if res.returncode == 0:
                    send_message({"status": "ok", "success": True})
                else:
                    send_message({"status": "error", "success": False, "error": res.stderr})
            else:
                logging.warning(f"Unknown command: {msg.get('command')}")
                send_message({"status": "unknown_command"})
        except Exception as e:
            # Exit silently on stream errors (Chrome closed)
            logging.error(f"Error in main loop: {e}\n{traceback.format_exc()}")
            sys.exit(1)

if __name__ == '__main__':
    main()
