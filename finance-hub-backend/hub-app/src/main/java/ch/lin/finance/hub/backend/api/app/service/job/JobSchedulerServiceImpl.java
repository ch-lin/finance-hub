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
package ch.lin.finance.hub.backend.api.app.service.job;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ch.lin.finance.hub.backend.api.app.service.config.scheduler.SchedulingConfigService;
import ch.lin.finance.hub.backend.api.app.service.config.system.ConfigUpdatedEvent;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.history.TriggerType;
import jakarta.annotation.PostConstruct;

/**
 * Concrete implementation of {@link JobSchedulerService} using Spring's
 * {@link TaskScheduler}.
 * <p>
 * Supports dynamically scheduling tasks using either a fixed-rate interval or
 * a CRON expression, depending on the configuration defined for each specific
 * {@link TransactionSource}.
 */
@Service
public class JobSchedulerServiceImpl implements JobSchedulerService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(JobSchedulerServiceImpl.class);

    /**
     * Orchestrator to execute the actual jobs.
     */
    private final JobOrchestrator orchestrator;

    /**
     * Spring's TaskScheduler used for scheduling jobs.
     */
    private final TaskScheduler taskScheduler;

    /**
     * Service to retrieve transaction source configurations.
     */
    private final SchedulingConfigService configsService;

    /**
     * In-memory map keeping track of running scheduled tasks.
     */
    private final Map<TransactionSource, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * Constructs a new JobSchedulerServiceImpl.
     *
     * @param orchestrator   The job orchestrator to execute the jobs.
     * @param taskScheduler  The task scheduler used for scheduling.
     * @param configsService The service to provide transaction source
     *                       configurations.
     */
    public JobSchedulerServiceImpl(JobOrchestrator orchestrator, TaskScheduler taskScheduler,
            SchedulingConfigService configsService) {
        this.orchestrator = orchestrator;
        this.taskScheduler = taskScheduler;
        this.configsService = configsService;
    }

    /**
     * Initializes the service post-construction.
     * Automatically kicks off schedulers for any data sources that have
     * {@code autoStartImportScheduler} enabled in their configuration.
     */
    @PostConstruct
    public void init() {
        // Only start schedulers for sources that actually have a registered executor
        // strategy
        for (TransactionSource transactionSource : orchestrator.getSupportedSources()) {
            SchedulingConfig config = configsService.getResolvedConfig(transactionSource, null);
            if (Boolean.TRUE.equals(config.getAutoStartImportScheduler())) {
                logger.info("Auto-start job scheduler is enabled for {}. Starting...", transactionSource);
                startTransactionSource(transactionSource, config);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() {
        for (TransactionSource transactionSource : orchestrator.getSupportedSources()) {
            SchedulingConfig config = configsService.getResolvedConfig(transactionSource, null);
            startTransactionSource(transactionSource, config);
        }
    }

    /**
     * Starts the scheduler for a specific transaction source based on its
     * configuration.
     *
     * @param transactionSource The transaction source to schedule.
     * @param config            The configuration containing scheduling details.
     */
    @SuppressWarnings("null")
    private synchronized void startTransactionSource(TransactionSource transactionSource,
            SchedulingConfig config) {
        if (isTransactionSourceSchedulerRunning(transactionSource)) {
            logger.warn("Job scheduler for {} is already running.", transactionSource);
            return;
        }

        if (!Boolean.TRUE.equals(config.getEnabled())) {
            logger.info("Configuration for {} is not enabled. Skipping scheduler start.", transactionSource);
            return;
        }

        SchedulerType type = config.getSchedulerType() != null ? config.getSchedulerType() : SchedulerType.FIXED_RATE;
        ScheduledFuture<?> scheduledTask = null;

        if (type == SchedulerType.CRON) {
            String expression = config.getCronExpression();
            String timeZone = config.getCronTimeZone();

            if (StringUtils.hasText(expression)) {
                try {
                    CronTrigger trigger = StringUtils.hasText(timeZone)
                            ? new CronTrigger(expression, ZoneId.of(timeZone))
                            : new CronTrigger(expression);
                    scheduledTask = taskScheduler.schedule(() -> executeJob(transactionSource), trigger);
                    logger.info("Job scheduler for {} started with CRON expression: {} (TimeZone: {})",
                            transactionSource, expression, timeZone);
                } catch (IllegalArgumentException e) {
                    logger.error("Failed to start scheduler for {}: Invalid CRON expression '{}'", transactionSource,
                            expression, e);
                }
            } else {
                logger.warn("Cannot start job scheduler for {}: CRON expression is empty.", transactionSource);
            }
        } else {
            Long rate = config.getFixedRate();
            if (rate != null && rate > 0) {
                scheduledTask = taskScheduler.scheduleAtFixedRate(() -> executeJob(transactionSource),
                        Duration.ofMillis(rate));
                logger.info("Job scheduler for {} started with fixed rate of {} ms.", transactionSource, rate);
            } else {
                logger.warn("Cannot start job scheduler for {}: Fixed rate is invalid ({}).", transactionSource, rate);
            }
        }

        if (scheduledTask != null) {
            scheduledTasks.put(transactionSource, scheduledTask);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop() {
        for (TransactionSource transactionSource : orchestrator.getSupportedSources()) {
            stopTransactionSource(transactionSource);
        }
    }

    /**
     * Handles configuration update events. Re-evaluates each transaction source's
     * scheduler based on its updated configuration.
     *
     * @param event The configuration updated event.
     */
    @EventListener
    public synchronized void handleConfigUpdatedEvent(ConfigUpdatedEvent event) {
        logger.info("Configuration updated event received. Re-evaluating schedulers...");
        for (TransactionSource transactionSource : orchestrator.getSupportedSources()) {
            SchedulingConfig config = configsService.getResolvedConfig(transactionSource, null);
            stopTransactionSource(transactionSource);
            if (Boolean.TRUE.equals(config.getAutoStartImportScheduler())) {
                startTransactionSource(transactionSource, config);
            }
        }
    }

    /**
     * Stops the running scheduler for a specific transaction source.
     *
     * @param transactionSource The transaction source to stop.
     */
    private synchronized void stopTransactionSource(TransactionSource transactionSource) {
        ScheduledFuture<?> task = scheduledTasks.get(transactionSource);
        if (task != null && !task.isDone()) {
            task.cancel(false);
            logger.info("Job scheduler for {} stopped.", transactionSource);
        }
        scheduledTasks.remove(transactionSource);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSchedulerRunning() {
        return scheduledTasks.values().stream()
                .anyMatch(task -> task != null && !task.isCancelled() && !task.isDone());
    }

    /**
     * Checks if a scheduler for the given transaction source is currently running.
     *
     * @param transactionSource The transaction source to check.
     * @return True if it is running, false otherwise.
     */
    private boolean isTransactionSourceSchedulerRunning(TransactionSource transactionSource) {
        ScheduledFuture<?> task = scheduledTasks.get(transactionSource);
        return task != null && !task.isCancelled() && !task.isDone();
    }

    /**
     * Executes the background job for the given transaction source.
     *
     * @param transactionSource The transaction source to process.
     */
    private void executeJob(TransactionSource transactionSource) {
        logger.info("Executing scheduled job for {}...", transactionSource);
        orchestrator.executeJob(transactionSource, TriggerType.SYSTEM, false);
    }
}
