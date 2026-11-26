/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.app.service.config.scheduler.result;

/**
 * Represents a time zone option with its ID and a user-friendly display name.
 *
 * @param id          The time zone ID (e.g., "Asia/Taipei").
 * @param displayName The formatted display name (e.g., "(UTC+08:00)
 *                    Asia/Taipei").
 */
public record TimeZoneOption(String id, String displayName) {

}
