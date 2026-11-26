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

# --- Build Stage ---
# Use a Maven image with Eclipse Temurin 25 to build the application.
FROM maven:3.9-eclipse-temurin-25 AS builder

LABEL maintainer="Che-Hung Lin <edward730109@gmail.com>"

# Set the working directory for the build stage.
WORKDIR /build

# Copy the Platform project and build it first.
# This assumes the Docker build context is the parent directory of both 'platform' and 'youtube-hub'.
COPY platform /build/platform
RUN mvn -f /build/platform/pom.xml clean install -Dmaven.test.skip=true

# Copy the Finance-Hub backend project.
COPY finance-hub/finance-hub-backend /build/finance-hub-backend

# Set the working directory to the Finance-Hub backend project for its build.
WORKDIR /build/finance-hub-backend

# Build the application, skipping tests for a faster build.
RUN mvn clean package -pl hub -am -Dmaven.test.skip=true

# Extract Layers
WORKDIR /build/finance-hub-backend/bin
RUN java -Djarmode=tools -jar finance-hub.jar extract --layers --launcher

# --- Runtime Stage ---
# Use a secure and minimal base image with a Java 25 JRE for the final image.
FROM eclipse-temurin:25-jre-jammy AS final

LABEL maintainer="Che-Hung Lin <edward730109@gmail.com>"

# Install curl for healthcheck
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create a dedicated user and group for the application to enhance security by not running as root.
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Set the working directory inside the container.
WORKDIR /app

# Create a directory for data import and set ownership to the appuser
RUN mkdir -p /app/data && chown appuser:appgroup /app/data

# Copy Layers
COPY --from=builder /build/finance-hub-backend/bin/finance-hub/dependencies/ ./
COPY --from=builder /build/finance-hub-backend/bin/finance-hub/spring-boot-loader/ ./
COPY --from=builder /build/finance-hub-backend/bin/finance-hub/snapshot-dependencies/ ./
COPY --from=builder /build/finance-hub-backend/bin/finance-hub/application/ ./

# Switch to the non-root user.
USER appuser

EXPOSE 8080

# The command to run the application.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
