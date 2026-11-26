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

ARG BASE_CONTAINER=mysql:8.4.6
FROM $BASE_CONTAINER

LABEL maintainer="Che-Hung Lin <edward730109@gmail.com>"

COPY --chown=mysql:mysql ./my.cnf /etc/mysql/conf.d/my.cnf

RUN mkdir -p /var/run/mysqld && \
    chown -R mysql:mysql /var/run/mysqld && \
    chmod 750 /var/run/mysqld
