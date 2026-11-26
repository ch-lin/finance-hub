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

import java.util.List;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import ch.lin.finance.hub.backend.api.app.config.system.DefaultConfigFactory;
import ch.lin.finance.hub.backend.api.app.config.system.FinanceHubDefaultProperties;
import ch.lin.finance.hub.backend.api.app.repository.config.FinanceHubConfigRepository;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.CreateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.UpdateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.RetirementMonthMode;
import ch.lin.platform.exception.ConfigNotFoundException;
import ch.lin.platform.exception.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class FinanceHubConfigServiceImplTest {

    @Mock
    private FinanceHubConfigRepository repository;
    @Mock
    private FinanceHubDefaultProperties defaultProperties;
    @Mock
    private DefaultConfigFactory factory;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FinanceHubConfigServiceImpl service;

    private FinanceHubDefaultProperties.SystemConfig defaultSysConfig;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        defaultSysConfig = new FinanceHubDefaultProperties.SystemConfig();
        defaultSysConfig.setName("default");
    }

    @Test
    @SuppressWarnings("null")
    void testGetAllConfigs_EmptyRepo() {
        when(repository.count()).thenReturn(0L);
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("default")).thenReturn(Optional.empty());
        FinanceHubConfig defaultConfig = new FinanceHubConfig("default");
        when(factory.create(defaultSysConfig)).thenReturn(defaultConfig);
        when(repository.save(any(FinanceHubConfig.class))).thenReturn(defaultConfig);

        when(repository.findAll()).thenReturn(List.of(defaultConfig));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(defaultConfig));

        AllConfigsData data = service.getAllConfigs();

        assertEquals("default", data.getEnabledConfigName());
        assertEquals(1, data.getAllConfigNames().size());
        verify(repository).save(any(FinanceHubConfig.class));
    }

    @Test
    void testCreateConfig_SystemReservedName() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        CreateFinanceHubConfigCommand command = CreateFinanceHubConfigCommand.builder()
                .name("default")
                .build();

        assertNotNull(assertThrows(InvalidRequestException.class, () -> service.createConfig(command)));
    }

    @Test
    void testCreateConfig_AlreadyExists() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("test")).thenReturn(Optional.of(new FinanceHubConfig("test")));

        CreateFinanceHubConfigCommand command = CreateFinanceHubConfigCommand.builder()
                .name("test")
                .build();

        assertNotNull(assertThrows(InvalidRequestException.class, () -> service.createConfig(command)));
    }

    @Test
    @SuppressWarnings("null")
    void testCreateConfig_Success() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("test")).thenReturn(Optional.empty());

        CreateFinanceHubConfigCommand command = CreateFinanceHubConfigCommand.builder()
                .name("test")
                .enabled(true)
                .fileProcessingTimeoutSeconds(20)
                .build();

        FinanceHubConfig activeConfig = new FinanceHubConfig("active");
        activeConfig.setEnabled(true);
        when(repository.findAllByEnabledTrue()).thenReturn(List.of(activeConfig));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(factory.create(any(String.class), any())).thenReturn(new FinanceHubConfig("test"));

        FinanceHubConfig result = service.createConfig(command);

        assertEquals("test", result.getName());
        assertTrue(result.getEnabled());
        assertEquals(20, result.getFileProcessingTimeoutSeconds());

        assertFalse(activeConfig.getEnabled());
        verify(repository).saveAll(any());
        verify(repository).save(any(FinanceHubConfig.class));
        verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
    }

    @Test
    void testDeleteAllConfigs() {
        service.deleteAllConfigs();
        verify(repository).deleteAll();
    }

    @Test
    void testDeleteConfig_SystemReserved() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        assertNotNull(assertThrows(InvalidRequestException.class, () -> service.deleteConfig("default")));
    }

    @Test
    void testDeleteConfig_Success() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        FinanceHubConfig config = new FinanceHubConfig("test");
        when(repository.findByName("test")).thenReturn(Optional.of(config));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty()); // No other enabled

        FinanceHubConfig defaultConfig = new FinanceHubConfig("default");
        when(repository.findByName("default")).thenReturn(Optional.of(defaultConfig));

        service.deleteConfig("test");

        verify(repository).delete(config);
        verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
        assertTrue(defaultConfig.getEnabled());
        verify(repository).save(defaultConfig);
    }

    @Test
    void testGetConfig_Success() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        FinanceHubConfig config = new FinanceHubConfig("test");
        when(repository.findByName("test")).thenReturn(Optional.of(config));

        FinanceHubConfig result = service.getConfig("test");
        assertEquals(config, result);
    }

    @Test
    void testGetConfig_NotFound() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("test")).thenReturn(Optional.empty());

        assertNotNull(assertThrows(ConfigNotFoundException.class, () -> service.getConfig("test")));
    }

    @Test
    @SuppressWarnings("null")
    void testSaveConfig_Success() {
        FinanceHubConfig config = new FinanceHubConfig("test");
        when(repository.findByName("test")).thenReturn(Optional.of(config));

        FinanceHubConfig otherEnabled = new FinanceHubConfig("other");
        otherEnabled.setEnabled(true);
        when(repository.findAllByEnabledTrue()).thenReturn(List.of(otherEnabled));

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));

        UpdateFinanceHubConfigCommand command = UpdateFinanceHubConfigCommand.builder()
                .name("test")
                .enabled(Optional.of(true))
                .fileProcessingTimeoutSeconds(Optional.of(20))
                .nisaExpectedReturnRate(Optional.empty())
                .idecoExpectedReturnRate(Optional.empty())
                .pensionExpectedMonthlySalary(Optional.empty())
                .simulationEarlyRepaymentMonth(Optional.of(6))
                .build();

        FinanceHubConfig result = service.saveConfig(command);

        assertTrue(result.getEnabled());
        assertEquals(20, result.getFileProcessingTimeoutSeconds());
        assertEquals(6, result.getSimulationEarlyRepaymentMonth());
        assertFalse(otherEnabled.getEnabled());
        verify(eventPublisher).publishEvent(any(ConfigUpdatedEvent.class));
    }

    @Test
    void testGetResolvedConfig() {
        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setFileProcessingTimeoutSeconds(null);
        config.setRetirementMonthMode(null);
        when(repository.findByName("test")).thenReturn(Optional.of(config));
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        defaultSysConfig.setFileProcessingTimeoutSeconds(30);
        defaultSysConfig.setRetirementMonthMode(RetirementMonthMode.DECEMBER);

        FinanceHubConfig result = service.getResolvedConfig("test");

        assertEquals("test", result.getName());
        assertEquals(30, result.getFileProcessingTimeoutSeconds());
        assertEquals(RetirementMonthMode.DECEMBER, result.getRetirementMonthMode());
    }

    @Test
    @SuppressWarnings("null")
    void testGetAllConfigs_NonEmptyRepo() {
        when(repository.count()).thenReturn(1L);
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findAll()).thenReturn(List.of(new FinanceHubConfig("test")));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty());

        AllConfigsData data = service.getAllConfigs();

        assertEquals("default", data.getEnabledConfigName());
        assertEquals(1, data.getAllConfigNames().size());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void testCreateConfig_Success_NotEnabled() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("test")).thenReturn(Optional.empty());

        CreateFinanceHubConfigCommand command = CreateFinanceHubConfigCommand.builder()
                .name("test")
                .enabled(false)
                .fileProcessingTimeoutSeconds(10)
                .build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(factory.create(any(String.class), any())).thenReturn(new FinanceHubConfig("test"));

        FinanceHubConfig result = service.createConfig(command);

        assertEquals("test", result.getName());
        assertFalse(result.getEnabled());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findAllByEnabledTrue();
        org.mockito.Mockito.verify(repository).save(any(FinanceHubConfig.class));
    }

    @Test
    void testDeleteConfig_NotFound() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("test")).thenReturn(Optional.empty());

        assertNotNull(assertThrows(ConfigNotFoundException.class, () -> service.deleteConfig("test")));
    }

    @Test
    @SuppressWarnings("null")
    void testDeleteConfig_Success_OtherConfigStillEnabled() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        FinanceHubConfig config = new FinanceHubConfig("test");
        when(repository.findByName("test")).thenReturn(Optional.of(config));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(new FinanceHubConfig("other")));

        service.deleteConfig("test");

        org.mockito.Mockito.verify(repository).delete(config);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void testDeleteConfig_Success_DefaultConfigAlreadyEnabled() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        FinanceHubConfig config = new FinanceHubConfig("test");
        when(repository.findByName("test")).thenReturn(Optional.of(config));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty());

        FinanceHubConfig defaultConfig = new FinanceHubConfig("default");
        defaultConfig.setEnabled(true);
        when(repository.findByName("default")).thenReturn(Optional.of(defaultConfig));

        service.deleteConfig("test");

        org.mockito.Mockito.verify(repository).delete(config);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void testGetConfig_DefaultConfigFallback() {
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("default")).thenReturn(Optional.empty());
        FinanceHubConfig defaultConfig = new FinanceHubConfig("default");
        when(factory.create(defaultSysConfig)).thenReturn(defaultConfig);
        when(repository.save(any(FinanceHubConfig.class))).thenReturn(defaultConfig);

        FinanceHubConfig result = service.getConfig("default");

        assertEquals(defaultConfig, result);
        org.mockito.Mockito.verify(repository).save(any(FinanceHubConfig.class));
    }

    @Test
    @SuppressWarnings("null")
    void testSaveConfig_ConfigNotFound_EnabledFalse() {
        when(repository.findByName("new_config")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(new FinanceHubConfig("active")));
        when(factory.create(any(String.class), any())).thenReturn(new FinanceHubConfig("new_config"));

        UpdateFinanceHubConfigCommand command = UpdateFinanceHubConfigCommand.builder()
                .name("new_config")
                .enabled(Optional.empty())
                .fileProcessingTimeoutSeconds(Optional.of(15))
                .nisaExpectedReturnRate(Optional.empty())
                .idecoExpectedReturnRate(Optional.empty())
                .pensionExpectedMonthlySalary(Optional.empty())
                .build();

        FinanceHubConfig result = service.saveConfig(command);

        assertEquals("new_config", result.getName());
        assertEquals(15, result.getFileProcessingTimeoutSeconds());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findAllByEnabledTrue();
    }

    @Test
    @SuppressWarnings("null")
    void testSaveConfig_EnableConfig_NoOtherActiveAtEnd() {
        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setEnabled(true);
        when(repository.findByName("test")).thenReturn(Optional.of(config));

        when(repository.findAllByEnabledTrue()).thenReturn(List.of(config));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty());

        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("default")).thenReturn(Optional.empty());
        FinanceHubConfig defaultConfig = new FinanceHubConfig("default");
        when(factory.create(defaultSysConfig)).thenReturn(defaultConfig);

        UpdateFinanceHubConfigCommand command = UpdateFinanceHubConfigCommand.builder()
                .name("test")
                .enabled(Optional.of(true))
                .fileProcessingTimeoutSeconds(Optional.empty())
                .nisaExpectedReturnRate(Optional.empty())
                .idecoExpectedReturnRate(Optional.empty())
                .pensionExpectedMonthlySalary(Optional.empty())
                .build();

        FinanceHubConfig result = service.saveConfig(command);

        assertEquals("test", result.getName());
        assertTrue(defaultConfig.getEnabled());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    @Test
    void testGetResolvedConfig_EmptyConfigName_FindsEnabled() {
        FinanceHubConfig activeConfig = new FinanceHubConfig("active");
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(activeConfig));

        FinanceHubConfig result = service.getResolvedConfig(null);

        assertEquals("active", result.getName());
    }

    @Test
    void testGetResolvedConfig_NotFoundAndCountZero() {
        when(repository.findByName("missing")).thenReturn(Optional.empty());
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(0L);
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("default")).thenReturn(Optional.empty());

        FinanceHubConfig defaultConfig = new FinanceHubConfig("default");
        when(factory.create(defaultSysConfig)).thenReturn(defaultConfig);
        when(repository.save(defaultConfig)).thenReturn(defaultConfig);

        FinanceHubConfig result = service.getResolvedConfig("missing");

        assertEquals("default", result.getName());
    }

    @Test
    void testGetResolvedConfig_NotFoundAndCountGreaterThanZero() {
        when(repository.findByName("missing")).thenReturn(Optional.empty());
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(1L);
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("default")).thenReturn(Optional.empty());

        FinanceHubConfig defaultConfig = new FinanceHubConfig("default");
        when(factory.create(defaultSysConfig)).thenReturn(defaultConfig);
        when(repository.save(defaultConfig)).thenReturn(defaultConfig);

        FinanceHubConfig result = service.getResolvedConfig("missing");

        assertEquals("default", result.getName());
    }

    @Test
    void testGetResolvedConfig_TimeoutNotNull() {
        FinanceHubConfig config = new FinanceHubConfig("test");
        config.setFileProcessingTimeoutSeconds(42);
        when(repository.findByName("test")).thenReturn(Optional.of(config));

        FinanceHubConfig result = service.getResolvedConfig("test");

        assertEquals(42, result.getFileProcessingTimeoutSeconds());
    }

    @Test
    void testFindOrCreateDefaultConfig_Exception() {
        when(repository.count()).thenReturn(0L);
        when(defaultProperties.getSystemConfig()).thenReturn(defaultSysConfig);
        when(repository.findByName("default")).thenReturn(Optional.empty());

        FinanceHubConfig defaultConfig = new FinanceHubConfig("default");
        when(factory.create(defaultSysConfig)).thenReturn(defaultConfig);
        when(repository.save(defaultConfig)).thenThrow(new RuntimeException("DB error"));

        ch.lin.platform.exception.ConfigCreationException exception = assertThrows(
                ch.lin.platform.exception.ConfigCreationException.class,
                () -> service.getAllConfigs());
        assertTrue(exception.getMessage().contains("Cannot create default system config"));
    }
}
