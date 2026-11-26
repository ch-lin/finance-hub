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
package ch.lin.finance.hub.backend.api.controller.job.dto;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * Data Transfer Object (DTO) representing job execution history.
 * Ensures that sensitive domain and ownership fields are not leaked.
 */
@Data
public class JobHistoryResponse {

    private Long id;
    private String transactionSource;
    private String triggerType;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer totalFiles;
    private Integer successCount;
    private Integer failedCount;
    private Integer needsReviewCount;
    private String errorMessage;
}
