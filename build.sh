#!/bin/bash

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

BACKEND_DIR="finance-hub-backend"

# Exit immediately if a command exits with a non-zero status.
set -euo pipefail

ENV_FILE=${1:-}

build_all() {
  if command -v mvn &> /dev/null; then
    echo "Building Finance Hub Backend Maven project..."
    # Run Maven build in a subshell to avoid changing the script's current directory.
    (cd "${BACKEND_DIR}" && mvn clean package install) || {
      echo "Maven build failed in ${BACKEND_DIR}!"
      exit 1
    }
    echo "Building Finance Hub Backend Maven project...done!"
  else
    echo "Maven not found, skipping Maven build."
  fi

  echo "Building Docker images using docker-compose..."
  # docker-compose will read the docker-compose.yml file and build all services.
  if [[ -n "${ENV_FILE}" ]]; then
    echo "Using env file: ${ENV_FILE}"
    docker-compose --env-file "${ENV_FILE}" up -d --wait
  else
    docker-compose up -d --wait
  fi
  echo "Building Docker images...done!"
}

build_all
exit 0
