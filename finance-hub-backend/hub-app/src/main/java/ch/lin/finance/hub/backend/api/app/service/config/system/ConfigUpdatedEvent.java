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
package ch.lin.finance.hub.backend.api.app.service.config.system;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when a configuration (either FinanceHubConfig or
 * TransactionSourceConfig) is updated.
 */
public class ConfigUpdatedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ConfigUpdatedEvent.
     *
     * @param source the object on which the Event initially occurred (never
     *               {@code null})
     */
    public ConfigUpdatedEvent(Object source) {
        super(source);
    }
}
