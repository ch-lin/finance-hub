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
package ch.lin.finance.hub.backend.api.app.service.config.scheduler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import ch.lin.finance.hub.backend.api.app.config.system.DefaultConfigFactory;
import ch.lin.finance.hub.backend.api.app.config.system.FinanceHubDefaultProperties;
import ch.lin.finance.hub.backend.api.app.repository.config.SchedulingConfigRepository;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.CreateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.UpdateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.ConfigUpdatedEvent;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulerType;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.platform.exception.ConfigNotFoundException;
import ch.lin.platform.exception.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SchedulingConfigServiceImplTest {

    @Mock
    private SchedulingConfigRepository repository;
    @Mock
    private FinanceHubDefaultProperties defaultProperties;
    @Mock
    private DefaultConfigFactory factory;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SchedulingConfigServiceImpl service;

    private FinanceHubDefaultProperties.SourceConfig defaultSourceConfig;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        defaultSourceConfig = new FinanceHubDefaultProperties.SourceConfig();
        defaultSourceConfig.setName("default");
        defaultSourceConfig.setEnabled(false);
    }

    @Test
    @SuppressWarnings("null")
    void testGetAllConfigs_EmptyRepo() {
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON)).thenReturn(List.of());
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig defaultConfig = new SchedulingConfig("default",
                TransactionSource.AMAZON);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "default"))
                .thenReturn(Optional.empty());
        when(factory.create(TransactionSource.AMAZON, defaultSourceConfig)).thenReturn(defaultConfig);
        when(repository.save(any())).thenReturn(defaultConfig);

        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.empty());

        AllConfigsData data = service.getAllConfigs(TransactionSource.AMAZON);

        assertEquals("default", data.getEnabledConfigName());
        assertEquals(1, data.getAllConfigNames().size());
        verify(repository).save(any(SchedulingConfig.class));
    }

    @Test
    void testCreateConfig_SystemReservedName() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        CreateSchedulingConfigCommand command = new CreateSchedulingConfigCommand(
                "default", TransactionSource.AMAZON, null, null, null, null, null, null);

        assertNotNull(assertThrows(InvalidRequestException.class, () -> service.createConfig(command)));
    }

    @Test
    void testCreateConfig_InvalidCronExpression() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        when(repository.findByTransactionSourceAndName(any(), any())).thenReturn(Optional.empty());

        CreateSchedulingConfigCommand command = new CreateSchedulingConfigCommand(
                "test", TransactionSource.AMAZON, null, null, null, null, "invalid", null);

        assertNotNull(assertThrows(InvalidRequestException.class, () -> service.createConfig(command)));
    }

    @Test
    void testCreateConfig_InvalidCronTimeZone() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        when(repository.findByTransactionSourceAndName(any(), any())).thenReturn(Optional.empty());

        CreateSchedulingConfigCommand command = new CreateSchedulingConfigCommand(
                "test", TransactionSource.AMAZON, null, null, null, null, null, "Invalid/Zone");

        assertNotNull(assertThrows(InvalidRequestException.class, () -> service.createConfig(command)));
    }

    @Test
    @SuppressWarnings("null")
    void testCreateConfig_Success() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        when(repository.findByTransactionSourceAndName(any(), any())).thenReturn(Optional.empty());

        SchedulingConfig activeConfig = new SchedulingConfig("active", TransactionSource.AMAZON);
        activeConfig.setEnabled(true);
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON)).thenReturn(List.of(activeConfig));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSchedulingConfigCommand command = new CreateSchedulingConfigCommand(
                "test", TransactionSource.AMAZON, true, null, SchedulerType.CRON, null, null, null);

        SchedulingConfig result = service.createConfig(command);

        assertEquals("test", result.getName());
        assertTrue(result.getEnabled());
        assertFalse(activeConfig.getEnabled());

        verify(repository).saveAll(any());
        verify(repository).save(any(SchedulingConfig.class));
        verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
    }

    @Test
    void testDeleteAllConfigs() {
        service.deleteAllConfigs();
        verify(repository).cleanTable();
    }

    @Test
    void testDeleteConfig_Success() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        SchedulingConfig config = new SchedulingConfig("test", TransactionSource.AMAZON);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(config));
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.empty());

        SchedulingConfig defaultConfig = new SchedulingConfig("default",
                TransactionSource.AMAZON);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "default"))
                .thenReturn(Optional.of(defaultConfig));

        service.deleteConfig(TransactionSource.AMAZON, "test");

        verify(repository).delete(config);
        verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
        assertTrue(defaultConfig.getEnabled());
        verify(repository).save(defaultConfig);
    }

    @Test
    void testGetConfig_NotFound() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        when(repository.findByTransactionSourceAndName(any(), any())).thenReturn(Optional.empty());

        assertNotNull(
                assertThrows(ConfigNotFoundException.class,
                        () -> service.getConfig(TransactionSource.AMAZON, "test")));
    }

    @Test
    @SuppressWarnings("null")
    void testSaveConfig_Success() {
        SchedulingConfig config = new SchedulingConfig("test", TransactionSource.AMAZON);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(config));

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.of(config));

        UpdateSchedulingConfigCommand command = UpdateSchedulingConfigCommand.builder()
                .name("test")
                .enabled(Optional.empty())
                .autoStartImportScheduler(Optional.empty())
                .schedulerType(Optional.empty())
                .fixedRate(Optional.of(5000L))
                .cronExpression(Optional.empty())
                .cronTimeZone(Optional.empty())
                .build();

        SchedulingConfig result = service.saveConfig(TransactionSource.AMAZON, command);

        assertEquals(5000L, result.getFixedRate());
        verify(repository).save(config);
        verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
    }

    @Test
    void testGetResolvedConfig() {
        SchedulingConfig config = new SchedulingConfig("test", TransactionSource.AMAZON);
        config.setSchedulerType(null); // Missing field
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(config));

        defaultSourceConfig.setSchedulerType(SchedulerType.CRON);
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig result = service.getResolvedConfig(TransactionSource.AMAZON, "test");

        assertEquals("test", result.getName());
        assertEquals(SchedulerType.CRON, result.getSchedulerType());
    }

    @Test
    void testGetAllConfigs_NotEmptyRepo() {
        SchedulingConfig existingConfig = new SchedulingConfig("test", TransactionSource.AMAZON);
        existingConfig.setEnabled(true);
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON))
                .thenReturn(List.of(existingConfig));
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.of(existingConfig));

        AllConfigsData data = service.getAllConfigs(TransactionSource.AMAZON);

        assertEquals("test", data.getEnabledConfigName());
        assertEquals(1, data.getAllConfigNames().size());
        assertTrue(data.getAllConfigNames().contains("test"));
    }

    @Test
    void testCreateConfig_AlreadyExists() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        when(repository.findByTransactionSourceAndName(any(), any()))
                .thenReturn(Optional.of(new SchedulingConfig("test", TransactionSource.AMAZON)));

        CreateSchedulingConfigCommand command = new CreateSchedulingConfigCommand(
                "test", TransactionSource.AMAZON, null, null, null, null, null, null);

        assertNotNull(assertThrows(InvalidRequestException.class, () -> service.createConfig(command)));
    }

    @Test
    void testDeleteConfig_SystemReservedName() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        assertNotNull(assertThrows(InvalidRequestException.class,
                () -> service.deleteConfig(TransactionSource.AMAZON, "default")));
    }

    @Test
    void testDeleteConfig_NotFound() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        when(repository.findByTransactionSourceAndName(any(), any())).thenReturn(Optional.empty());
        assertNotNull(assertThrows(ConfigNotFoundException.class,
                () -> service.deleteConfig(TransactionSource.AMAZON, "test")));
    }

    @Test
    void testDeleteConfig_Success_DefaultAlreadyEnabled() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        SchedulingConfig config = new SchedulingConfig("test", TransactionSource.AMAZON);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(config));
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.empty());

        SchedulingConfig defaultConfig = new SchedulingConfig("default",
                TransactionSource.AMAZON);
        defaultConfig.setEnabled(true);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "default"))
                .thenReturn(Optional.of(defaultConfig));

        service.deleteConfig(TransactionSource.AMAZON, "test");

        verify(repository).delete(config);
        verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
        verify(repository, never()).save(defaultConfig); // Should not save if already enabled
    }

    @Test
    void testGetConfig_Success() {
        SchedulingConfig config = new SchedulingConfig("test", TransactionSource.AMAZON);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(config));

        SchedulingConfig result = service.getConfig(TransactionSource.AMAZON, "test");
        assertEquals("test", result.getName());
    }

    @Test
    @SuppressWarnings("null")
    void testGetConfig_DefaultConfigCreated() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "default"))
                .thenReturn(Optional.empty());
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig defaultConfig = new SchedulingConfig("default",
                TransactionSource.AMAZON);
        when(factory.create(TransactionSource.AMAZON, defaultSourceConfig)).thenReturn(defaultConfig);
        when(repository.save(any())).thenReturn(defaultConfig);

        SchedulingConfig result = service.getConfig(TransactionSource.AMAZON, "default");
        assertEquals("default", result.getName());
        verify(repository).save(defaultConfig);
    }

    @Test
    @SuppressWarnings("null")
    void testSaveConfig_EnableExistingConfig_DisablesOthers() {
        SchedulingConfig configToUpdate = new SchedulingConfig("test", TransactionSource.AMAZON);
        configToUpdate.setEnabled(false);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(configToUpdate));

        SchedulingConfig otherConfig = new SchedulingConfig("other", TransactionSource.AMAZON);
        otherConfig.setEnabled(true);
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON))
                .thenReturn(List.of(otherConfig, configToUpdate));

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.of(configToUpdate));

        UpdateSchedulingConfigCommand command = UpdateSchedulingConfigCommand.builder()
                .name("test")
                .enabled(Optional.of(true))
                .autoStartImportScheduler(Optional.empty())
                .schedulerType(Optional.empty())
                .fixedRate(Optional.empty())
                .cronExpression(Optional.empty())
                .cronTimeZone(Optional.empty())
                .build();

        SchedulingConfig result = service.saveConfig(TransactionSource.AMAZON, command);

        assertTrue(result.getEnabled());
        assertFalse(otherConfig.getEnabled());
        verify(repository).save(otherConfig);
    }

    @Test
    @SuppressWarnings("null")
    void testSaveConfig_DisableConfig_EnablesDefault() {
        SchedulingConfig configToUpdate = new SchedulingConfig("test", TransactionSource.AMAZON);
        configToUpdate.setEnabled(true);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(configToUpdate));

        // When saving the config, we don't enable it to trigger fallback
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.empty());

        SchedulingConfig defaultConfig = new SchedulingConfig("default",
                TransactionSource.AMAZON);
        defaultConfig.setEnabled(false);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "default"))
                .thenReturn(Optional.of(defaultConfig));
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSchedulingConfigCommand command = UpdateSchedulingConfigCommand.builder()
                .name("test")
                .enabled(Optional.of(false))
                .autoStartImportScheduler(Optional.empty())
                .schedulerType(Optional.empty())
                .fixedRate(Optional.empty())
                .cronExpression(Optional.empty())
                .cronTimeZone(Optional.empty())
                .build();

        SchedulingConfig result = service.saveConfig(TransactionSource.AMAZON, command);

        assertFalse(result.getEnabled());
        assertTrue(defaultConfig.getEnabled());
        verify(repository).save(defaultConfig);
    }

    @Test
    @SuppressWarnings("null")
    void testDeleteConfig_Success_OtherConfigEnabled() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        SchedulingConfig config = new SchedulingConfig("test", TransactionSource.AMAZON);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(config));

        SchedulingConfig otherConfig = new SchedulingConfig("other", TransactionSource.AMAZON);
        otherConfig.setEnabled(true);
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.of(otherConfig));

        service.deleteConfig(TransactionSource.AMAZON, "test");
        verify(repository).delete(config);
        verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
        verify(repository, never()).save(any());
    }

    @Test
    void testGetConfig_FallbackNotFound() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "unknown"))
                .thenReturn(Optional.empty());
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        assertNotNull(assertThrows(ConfigNotFoundException.class,
                () -> service.getConfig(TransactionSource.AMAZON, "unknown")));
    }

    @Test
    void testSaveConfig_NotFound() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.empty());

        UpdateSchedulingConfigCommand command = UpdateSchedulingConfigCommand.builder()
                .name("test")
                .enabled(Optional.empty())
                .autoStartImportScheduler(Optional.empty())
                .schedulerType(Optional.empty())
                .fixedRate(Optional.empty())
                .cronExpression(Optional.empty())
                .cronTimeZone(Optional.empty())
                .build();

        assertNotNull(assertThrows(ConfigNotFoundException.class,
                () -> service.saveConfig(TransactionSource.AMAZON, command)));
    }

    @Test
    @SuppressWarnings("null")
    void testSaveConfig_EnableAlreadyEnabledConfig() {
        SchedulingConfig configToUpdate = new SchedulingConfig("test", TransactionSource.AMAZON);
        configToUpdate.setEnabled(true);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(configToUpdate));
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON))
                .thenReturn(List.of(configToUpdate));

        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.of(configToUpdate));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSchedulingConfigCommand command = UpdateSchedulingConfigCommand.builder()
                .name("test")
                .enabled(Optional.of(true))
                .autoStartImportScheduler(Optional.empty())
                .schedulerType(Optional.empty())
                .fixedRate(Optional.empty())
                .cronExpression(Optional.empty())
                .cronTimeZone(Optional.empty())
                .build();

        service.saveConfig(TransactionSource.AMAZON, command);

        verify(repository, times(1)).save(configToUpdate);
    }

    @Test
    void testGetResolvedConfig_EmptyConfigName_FindsEnabled() {
        SchedulingConfig enabledConfig = new SchedulingConfig("enabled",
                TransactionSource.AMAZON);
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.of(enabledConfig));
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig result = service.getResolvedConfig(TransactionSource.AMAZON, null);
        assertEquals("enabled", result.getName());
    }

    @Test
    void testGetResolvedConfig_NotFound_FindsEnabled() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "notfound"))
                .thenReturn(Optional.empty());

        SchedulingConfig enabledConfig = new SchedulingConfig("enabled",
                TransactionSource.AMAZON);
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.of(enabledConfig));
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig result = service.getResolvedConfig(TransactionSource.AMAZON, "notfound");
        assertEquals("enabled", result.getName());
    }

    @Test
    @SuppressWarnings("null")
    void testGetResolvedConfig_NoConfigsExist_CreatesDefault() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.empty());
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.empty());
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON)).thenReturn(List.of());

        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig defaultConfig = new SchedulingConfig("default",
                TransactionSource.AMAZON);
        when(factory.create(TransactionSource.AMAZON, defaultSourceConfig)).thenReturn(defaultConfig);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "default"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(defaultConfig);

        SchedulingConfig result = service.getResolvedConfig(TransactionSource.AMAZON, "test");
        assertEquals("default", result.getName());
    }

    @Test
    void testGetResolvedConfig_WithMissingFields_UsesDefaults() {
        SchedulingConfig config = new SchedulingConfig("test", TransactionSource.AMAZON);
        config.setEnabled(null);
        config.setAutoStartImportScheduler(null);
        config.setSchedulerType(null);
        config.setFixedRate(null);
        config.setCronExpression("");
        config.setCronTimeZone(null);

        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(config));

        defaultSourceConfig.setEnabled(true);
        defaultSourceConfig.setAutoStartImportScheduler(true);
        defaultSourceConfig.setSchedulerType(SchedulerType.FIXED_RATE);
        defaultSourceConfig.setFixedRate(1000L);
        defaultSourceConfig.setCronExpression("0 0 * * * ?");
        defaultSourceConfig.setCronTimeZone("UTC");

        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig result = service.getResolvedConfig(TransactionSource.AMAZON, "test");

        assertTrue(result.getEnabled());
        assertTrue(result.getAutoStartImportScheduler());
        assertEquals(SchedulerType.FIXED_RATE, result.getSchedulerType());
        assertEquals(1000L, result.getFixedRate());
        assertEquals("0 0 * * * ?", result.getCronExpression());
        assertEquals("UTC", result.getCronTimeZone());
    }

    @Test
    @SuppressWarnings("null")
    void testGetResolvedConfig_NotFound_NoEnabledConfig_SomeExist_CreatesDefault() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.empty());
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.empty());

        SchedulingConfig otherDisabledConfig = new SchedulingConfig("other",
                TransactionSource.AMAZON);
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON))
                .thenReturn(List.of(otherDisabledConfig));

        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig defaultConfig = new SchedulingConfig("default",
                TransactionSource.AMAZON);
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "default"))
                .thenReturn(Optional.empty());
        when(factory.create(TransactionSource.AMAZON, defaultSourceConfig)).thenReturn(defaultConfig);
        when(repository.save(any())).thenReturn(defaultConfig);

        SchedulingConfig result = service.getResolvedConfig(TransactionSource.AMAZON, "test");
        assertEquals("default", result.getName());
    }

    @Test
    @SuppressWarnings("null")
    void testGetConfig_FallbackFound() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "fallback_name"))
                .thenReturn(Optional.empty());

        FinanceHubDefaultProperties.SourceConfig fallbackSourceConfig = new FinanceHubDefaultProperties.SourceConfig();
        fallbackSourceConfig.setName("fallback_name");

        FinanceHubDefaultProperties.SourceConfig rakutenConfig = new FinanceHubDefaultProperties.SourceConfig();
        rakutenConfig.setName("fallback_name");

        when(defaultProperties.getTransactionSourceConfigs()).thenReturn(Map.of(
                TransactionSource.AMAZON, fallbackSourceConfig,
                TransactionSource.RAKUTEN, rakutenConfig));

        SchedulingConfig expectedConfig = new SchedulingConfig("fallback_name",
                TransactionSource.AMAZON);
        when(factory.create(TransactionSource.AMAZON, fallbackSourceConfig)).thenReturn(expectedConfig);
        when(repository.save(any())).thenReturn(expectedConfig);

        SchedulingConfig result = service.getConfig(TransactionSource.AMAZON, "fallback_name");
        assertEquals("fallback_name", result.getName());
    }

    @Test
    void testGetConfig_OtherSourceInProperties() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "default"))
                .thenReturn(Optional.empty());

        FinanceHubDefaultProperties.SourceConfig rakutenConfig = new FinanceHubDefaultProperties.SourceConfig();
        rakutenConfig.setName("default");

        // Provide only RAKUTEN in properties
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.RAKUTEN, rakutenConfig));

        assertNotNull(assertThrows(ConfigNotFoundException.class,
                () -> service.getConfig(TransactionSource.AMAZON, "default")));
    }

    @Test
    void testGetResolvedConfig_WithValidCronAndZone() {
        SchedulingConfig config = new SchedulingConfig("test", TransactionSource.AMAZON);
        config.setCronExpression("0 0 * * * ?");
        config.setCronTimeZone("Asia/Taipei");

        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.of(config));
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig result = service.getResolvedConfig(TransactionSource.AMAZON, "test");
        assertEquals("0 0 * * * ?", result.getCronExpression());
        assertEquals("Asia/Taipei", result.getCronTimeZone());
    }

    @Test
    @SuppressWarnings("null")
    void testGetOrCreateDefaultConfig_SaveThrowsException() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.empty());
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.empty());
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON)).thenReturn(List.of());

        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));

        SchedulingConfig defaultConfig = new SchedulingConfig("default",
                TransactionSource.AMAZON);
        when(factory.create(any(TransactionSource.class), any(FinanceHubDefaultProperties.SourceConfig.class)))
                .thenReturn(defaultConfig);

        when(repository.save(any(SchedulingConfig.class))).thenThrow(new RuntimeException("Save Error"));

        assertNotNull(assertThrows(ch.lin.platform.exception.ConfigCreationException.class,
                () -> service.getResolvedConfig(TransactionSource.AMAZON, "test")));
    }

    @Test
    void testGetOrCreateDefaultConfig_NoDefaultProperties() {
        when(repository.findByTransactionSourceAndName(TransactionSource.AMAZON, "test"))
                .thenReturn(Optional.empty());
        when(repository.findFirstByTransactionSourceAndEnabledTrue(TransactionSource.AMAZON))
                .thenReturn(Optional.empty());
        when(repository.findAllByTransactionSource(TransactionSource.AMAZON)).thenReturn(List.of());

        when(defaultProperties.getTransactionSourceConfigs()).thenReturn(Map.of()); // Empty properties

        assertNotNull(assertThrows(ch.lin.platform.exception.ConfigCreationException.class,
                () -> service.getResolvedConfig(TransactionSource.AMAZON, "test")));
    }

    @Test
    @SuppressWarnings("null")
    void testCreateConfig_ValidCronAndZone() {
        when(defaultProperties.getTransactionSourceConfigs())
                .thenReturn(Map.of(TransactionSource.AMAZON, defaultSourceConfig));
        when(repository.findByTransactionSourceAndName(any(), any())).thenReturn(Optional.empty());

        CreateSchedulingConfigCommand command = new CreateSchedulingConfigCommand(
                "test", TransactionSource.AMAZON, null, null, null, null, "0 0 * * * ?", "Asia/Tokyo");

        service.createConfig(command);
        verify(repository).save(any(SchedulingConfig.class));
    }

    @Test
    void testGetTimeZones() {
        java.util.TimeZone originalTimeZone = java.util.TimeZone.getDefault();
        try {
            // Force system default to something other than Tokyo or Taipei
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));

            var zones = service.getTimeZones();
            assertFalse(zones.isEmpty());
            // Verify system default is first
            assertEquals("America/New_York", zones.get(0).id());

        } finally {
            java.util.TimeZone.setDefault(originalTimeZone);
        }
    }
}
