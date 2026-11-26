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
package ch.lin.finance.hub.backend.api.app.service.config.pension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.config.pension.PensionRuleConfigFactory;
import ch.lin.finance.hub.backend.api.app.config.pension.PensionRuleDefaultProperties;
import ch.lin.finance.hub.backend.api.app.repository.config.PensionRuleConfigRepository;
import ch.lin.finance.hub.backend.api.app.service.config.pension.command.CreatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensionRuleConfigsData;
import ch.lin.finance.hub.backend.api.domain.model.config.pension.PensionRuleConfig;
import ch.lin.platform.exception.ConfigNotFoundException;
import ch.lin.platform.exception.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class PensionRuleConfigServiceImplTest {

    @Mock
    private PensionRuleConfigRepository repository;
    @Mock
    private PensionRuleDefaultProperties defaultProperties;
    @Mock
    private PensionRuleConfigFactory factory;

    @InjectMocks
    private PensionRuleConfigServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
    }

    @Test
    void testGetAllConfigs_EmptyRepo() {
        when(repository.count()).thenReturn(0L);
        when(defaultProperties.getName()).thenReturn("default");

        PensionRuleConfig config = new PensionRuleConfig("default", true, 831700L, 480L, new BigDecimal("5.481"),
                650000L, 1500000L);
        when(factory.create(defaultProperties)).thenReturn(config);

        when(repository.findAll()).thenReturn(List.of(config));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));

        AllPensionRuleConfigsData data = service.getAllConfigs();

        assertEquals("default", data.getEnabledConfigName());
        assertEquals(1, data.getAllConfigNames().size());
        verify(repository).save(config);
    }

    @Test
    void testGetAllConfigs() {
        when(repository.count()).thenReturn(1L);
        when(defaultProperties.getName()).thenReturn("default");

        PensionRuleConfig config = new PensionRuleConfig("default", true, 831700L, 480L, new BigDecimal("5.481"),
                650000L, 1500000L);
        when(repository.findAll()).thenReturn(List.of(config));
        when(repository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));

        AllPensionRuleConfigsData data = service.getAllConfigs();

        assertEquals("default", data.getEnabledConfigName());
        assertEquals(1, data.getAllConfigNames().size());
    }

    @Test
    void testGetConfig_NotFound() {
        when(repository.findByName("custom")).thenReturn(Optional.empty());

        ConfigNotFoundException exception = assertThrows(ConfigNotFoundException.class,
                () -> service.getConfig("custom"));
        assertEquals("PensionRuleConfig 'custom' not found.", exception.getMessage());
    }

    @Test
    void testCreateConfig_SystemReservedName() {
        when(defaultProperties.getName()).thenReturn("default");

        CreatePensionRuleConfigCommand cmd = CreatePensionRuleConfigCommand.builder()
                .name("default")
                .enabled(true)
                .maxBasicPensionAmount(831700L)
                .maxBasicPensionMonths(480L)
                .kouseiMultiplierPerMille(new BigDecimal("5.481"))
                .kouseiSalaryCapMonthly(650000L)
                .kouseiBonusCapPerPayment(1500000L)
                .build();

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> service.createConfig(cmd));
        assertEquals(
                "The 'default' configuration is system-reserved and cannot be created manually.",
                exception.getMessage());
    }

    @Test
    void testCreateConfig_AlreadyExists() {
        when(defaultProperties.getName()).thenReturn("default");
        when(repository.findByName("custom"))
                .thenReturn(Optional.of(new PensionRuleConfig("custom", true, 831700L, 480L, new BigDecimal("5.481"),
                        650000L, 1500000L)));

        CreatePensionRuleConfigCommand cmd = CreatePensionRuleConfigCommand.builder()
                .name("custom")
                .enabled(true)
                .maxBasicPensionAmount(831700L)
                .maxBasicPensionMonths(480L)
                .kouseiMultiplierPerMille(new BigDecimal("5.481"))
                .kouseiSalaryCapMonthly(650000L)
                .kouseiBonusCapPerPayment(1500000L)
                .build();

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> service.createConfig(cmd));
        assertEquals("Configuration with name 'custom' already exists.", exception.getMessage());
    }
}
