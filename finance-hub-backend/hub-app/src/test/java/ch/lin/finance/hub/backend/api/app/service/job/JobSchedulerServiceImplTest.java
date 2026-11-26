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
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import ch.lin.finance.hub.backend.api.app.service.config.scheduler.SchedulingConfigService;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "null", "unused" })
class JobSchedulerServiceImplTest {

    @Mock
    private JobOrchestrator orchestrator;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SchedulingConfigService configsService;

    private JobSchedulerServiceImpl schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new JobSchedulerServiceImpl(orchestrator, taskScheduler, configsService);
    }

    @Test
    void testInit_AutoStart() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getAutoStartImportScheduler()).thenReturn(true);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.FIXED_RATE);
        when(config.getFixedRate()).thenReturn(5000L);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(5000L)));

        schedulerService.init();

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(5000L)));
        assertTrue(schedulerService.isSchedulerRunning());
    }

    @Test
    void testInit_NoAutoStart() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getAutoStartImportScheduler()).thenReturn(false);
        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);

        schedulerService.init();

        verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any());
        assertFalse(schedulerService.isSchedulerRunning());
    }

    @Test
    void testStart_CronType() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.CREDIT_CARD));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.CRON);
        when(config.getCronExpression()).thenReturn("0 0 12 * * ?");
        when(config.getCronTimeZone()).thenReturn("UTC");

        when(configsService.getResolvedConfig(TransactionSource.CREDIT_CARD, null)).thenReturn(config);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        schedulerService.start();

        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        assertTrue(schedulerService.isSchedulerRunning());
    }

    @Test
    void testStart_CronType_EmptyExpression() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.CREDIT_CARD));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.CRON);
        when(config.getCronExpression()).thenReturn("");

        when(configsService.getResolvedConfig(TransactionSource.CREDIT_CARD, null)).thenReturn(config);

        schedulerService.start();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
        assertFalse(schedulerService.isSchedulerRunning());
    }

    @Test
    void testStart_Disabled() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(false);
        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);

        schedulerService.start();

        verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any());
    }

    @Test
    void testStart_AlreadyRunning_TaskCancelled() {
        testInit_AutoStart();

        // Mock the future to be cancelled so it starts a new one
        Object mapObj = org.springframework.test.util.ReflectionTestUtils.getField(schedulerService, "scheduledTasks");
        if (mapObj instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
            ScheduledFuture<?> future = (ScheduledFuture<?>) map.values().iterator().next();
            when(future.isCancelled()).thenReturn(true);
        }

        schedulerService.start();

        // Should have called schedule again
        verify(taskScheduler, org.mockito.Mockito.times(2)).scheduleAtFixedRate(any(Runnable.class),
                eq(Duration.ofMillis(5000L)));
    }

    @Test
    void testStart_AlreadyRunning_TaskDone() {
        testInit_AutoStart();

        Object mapObj = org.springframework.test.util.ReflectionTestUtils.getField(schedulerService, "scheduledTasks");
        if (mapObj instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
            ScheduledFuture<?> future = (ScheduledFuture<?>) map.values().iterator().next();
            when(future.isCancelled()).thenReturn(false);
            when(future.isDone()).thenReturn(true);
        }

        schedulerService.start();

        // Should have called schedule again
        verify(taskScheduler, org.mockito.Mockito.times(2)).scheduleAtFixedRate(any(Runnable.class),
                eq(Duration.ofMillis(5000L)));
    }

    @Test
    void testExecuteJob_CronType() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.CREDIT_CARD));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.CRON);
        when(config.getCronExpression()).thenReturn("0 0 12 * * ?");

        when(configsService.getResolvedConfig(TransactionSource.CREDIT_CARD, null)).thenReturn(config);

        org.mockito.ArgumentCaptor<Runnable> runnableCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(runnableCaptor.capture(), any(CronTrigger.class));

        schedulerService.start();

        Runnable task = runnableCaptor.getValue();
        task.run();

        verify(orchestrator).executeJob(eq(TransactionSource.CREDIT_CARD),
                eq(ch.lin.finance.hub.backend.api.domain.model.history.TriggerType.SYSTEM), eq(false));
    }

    @Test
    void testStart_FixedRate_NullRate() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.FIXED_RATE);
        when(config.getFixedRate()).thenReturn(null);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);

        schedulerService.start();

        verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any());
    }

    @Test
    void testStop() {
        // Start it first
        testInit_AutoStart();

        schedulerService.stop();

        assertFalse(schedulerService.isSchedulerRunning());
    }

    @Test
    void testStart_AlreadyRunning() {
        testInit_AutoStart();

        schedulerService.start();

        // Initial init calls it once. The start() should do nothing.
        verify(taskScheduler, org.mockito.Mockito.times(1)).scheduleAtFixedRate(any(Runnable.class),
                eq(Duration.ofMillis(5000L)));
    }

    @Test
    void testStart_NullSchedulerType() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(null);
        when(config.getFixedRate()).thenReturn(5000L);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(5000L)));

        schedulerService.start();

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(5000L)));
    }

    @Test
    void testStart_CronType_WithoutTimeZone() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.CREDIT_CARD));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.CRON);
        when(config.getCronExpression()).thenReturn("0 0 12 * * ?");
        when(config.getCronTimeZone()).thenReturn(null);

        when(configsService.getResolvedConfig(TransactionSource.CREDIT_CARD, null)).thenReturn(config);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        schedulerService.start();

        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    void testStart_CronType_InvalidExpression() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.CREDIT_CARD));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.CRON);
        when(config.getCronExpression()).thenReturn("invalid");

        when(configsService.getResolvedConfig(TransactionSource.CREDIT_CARD, null)).thenReturn(config);

        schedulerService.start();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    void testStart_FixedRate_InvalidRate() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.FIXED_RATE);
        when(config.getFixedRate()).thenReturn(0L);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);

        schedulerService.start();

        verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any());
    }

    @Test
    void testStop_TaskDone() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getAutoStartImportScheduler()).thenReturn(true);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.FIXED_RATE);
        when(config.getFixedRate()).thenReturn(5000L);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(true);
        doReturn(future).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(5000L)));

        schedulerService.init();

        schedulerService.stop();

        verify(future, never()).cancel(any(Boolean.class));
    }

    @Test
    void testExecuteJob() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getAutoStartImportScheduler()).thenReturn(true);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.FIXED_RATE);
        when(config.getFixedRate()).thenReturn(5000L);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);

        org.mockito.ArgumentCaptor<Runnable> runnableCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleAtFixedRate(runnableCaptor.capture(),
                eq(Duration.ofMillis(5000L)));

        schedulerService.init();

        Runnable task = runnableCaptor.getValue();
        task.run();

        verify(orchestrator).executeJob(eq(TransactionSource.AMAZON),
                eq(ch.lin.finance.hub.backend.api.domain.model.history.TriggerType.SYSTEM), eq(false));
    }

    @Test
    void testIsSchedulerRunning_WithNullTask() {
        java.util.Map<TransactionSource, ScheduledFuture<?>> hashMap = new java.util.HashMap<>();
        hashMap.put(TransactionSource.AMAZON, null);
        org.springframework.test.util.ReflectionTestUtils.setField(schedulerService, "scheduledTasks", hashMap);

        assertFalse(schedulerService.isSchedulerRunning());
    }

    @Test
    void testIsSchedulerRunning_WithCancelledTask() {
        java.util.Map<TransactionSource, ScheduledFuture<?>> hashMap = new java.util.HashMap<>();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isCancelled()).thenReturn(true);
        hashMap.put(TransactionSource.AMAZON, future);
        org.springframework.test.util.ReflectionTestUtils.setField(schedulerService, "scheduledTasks", hashMap);

        assertFalse(schedulerService.isSchedulerRunning());
    }

    @Test
    void testIsSchedulerRunning_WithDoneTask() {
        java.util.Map<TransactionSource, ScheduledFuture<?>> hashMap = new java.util.HashMap<>();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isCancelled()).thenReturn(false);
        when(future.isDone()).thenReturn(true);
        hashMap.put(TransactionSource.AMAZON, future);
        org.springframework.test.util.ReflectionTestUtils.setField(schedulerService, "scheduledTasks", hashMap);

        assertFalse(schedulerService.isSchedulerRunning());
    }

    @Test
    void testStop_TaskNull() {
        when(orchestrator.getSupportedSources())
                .thenReturn(Set.of(TransactionSource.AMAZON, TransactionSource.CREDIT_CARD));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getAutoStartImportScheduler()).thenReturn(true);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.FIXED_RATE);
        when(config.getFixedRate()).thenReturn(5000L);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);

        SchedulingConfig configCC = mock(SchedulingConfig.class);
        when(configsService.getResolvedConfig(TransactionSource.CREDIT_CARD, null)).thenReturn(configCC);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        org.mockito.Mockito.lenient().doReturn(future).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class),
                eq(Duration.ofMillis(5000L)));

        schedulerService.init();
        schedulerService.stop();

        assertFalse(schedulerService.isSchedulerRunning());
    }

    @Test
    void testHandleConfigUpdatedEvent() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getAutoStartImportScheduler()).thenReturn(true);
        when(config.getEnabled()).thenReturn(true);
        when(config.getSchedulerType()).thenReturn(SchedulerType.FIXED_RATE);
        when(config.getFixedRate()).thenReturn(5000L);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class),
                eq(Duration.ofMillis(5000L)));

        ch.lin.finance.hub.backend.api.app.service.config.system.ConfigUpdatedEvent event = new ch.lin.finance.hub.backend.api.app.service.config.system.ConfigUpdatedEvent(
                this);
        schedulerService.handleConfigUpdatedEvent(event);

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(5000L)));
        assertTrue(schedulerService.isSchedulerRunning());
    }

    @Test
    void testHandleConfigUpdatedEvent_NoAutoStart() {
        when(orchestrator.getSupportedSources()).thenReturn(Set.of(TransactionSource.AMAZON));

        SchedulingConfig config = mock(SchedulingConfig.class);
        when(config.getAutoStartImportScheduler()).thenReturn(false);

        when(configsService.getResolvedConfig(TransactionSource.AMAZON, null)).thenReturn(config);

        ch.lin.finance.hub.backend.api.app.service.config.system.ConfigUpdatedEvent event = new ch.lin.finance.hub.backend.api.app.service.config.system.ConfigUpdatedEvent(
                this);
        schedulerService.handleConfigUpdatedEvent(event);

        verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
        assertFalse(schedulerService.isSchedulerRunning());
    }
}
