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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.config.pension.PensionRuleConfigFactory;
import ch.lin.finance.hub.backend.api.app.config.pension.PensionRuleDefaultProperties;
import ch.lin.finance.hub.backend.api.app.repository.config.PensionRuleConfigRepository;
import ch.lin.finance.hub.backend.api.app.service.config.pension.command.CreatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.command.UpdatePensionRuleConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.AllPensionRuleConfigsData;
import ch.lin.finance.hub.backend.api.app.service.config.pension.result.PensionRuleConfigResult;
import ch.lin.finance.hub.backend.api.domain.model.config.pension.PensionRuleConfig;
import ch.lin.platform.exception.ConfigNotFoundException;
import ch.lin.platform.exception.InvalidRequestException;

@Service
public class PensionRuleConfigServiceImpl implements PensionRuleConfigService {

    private static final Logger logger = LoggerFactory.getLogger(PensionRuleConfigServiceImpl.class);

    private final PensionRuleConfigRepository repository;
    private final PensionRuleDefaultProperties defaultProperties;
    private final PensionRuleConfigFactory factory;

    public PensionRuleConfigServiceImpl(PensionRuleConfigRepository repository,
            PensionRuleDefaultProperties defaultProperties,
            PensionRuleConfigFactory factory) {
        this.repository = repository;
        this.defaultProperties = defaultProperties;
        this.factory = factory;
    }

    /**
     * Initializes the default configuration on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeDefaultConfigOnStartup() {
        if (repository.count() == 0) {
            PensionRuleConfig defaultConfig = factory.create(defaultProperties);
            if (!Boolean.TRUE.equals(defaultConfig.getEnabled())) {
                defaultConfig.setEnabled(true);
            }
            repository.save(defaultConfig);
            logger.info("Enabled '{}' pension rule configuration on startup.", defaultConfig.getName());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AllPensionRuleConfigsData getAllConfigs() {
        if (repository.count() == 0) {
            PensionRuleConfig defaultConfig = factory.create(defaultProperties);
            defaultConfig.setEnabled(true);
            repository.save(defaultConfig);
        }

        List<String> allNames = repository.findAll().stream()
                .map(c -> c.getName())
                .collect(Collectors.toList());

        String enabledConfigName = repository.findFirstByEnabledTrue()
                .map(c -> c.getName())
                .orElse(defaultProperties.getName());

        return new AllPensionRuleConfigsData(enabledConfigName, allNames);
    }

    @Override
    @Transactional(readOnly = true)
    public PensionRuleConfigResult getConfig(String name) {
        PensionRuleConfig config = repository.findByName(name)
                .orElseThrow(() -> new ConfigNotFoundException("PensionRuleConfig '" + name + "' not found."));
        return PensionRuleConfigResult.fromEntity(config);
    }

    @Override
    @Transactional
    public PensionRuleConfigResult createConfig(CreatePensionRuleConfigCommand command) {
        String configName = command.getName();
        String defaultName = defaultProperties.getName();

        if (defaultName.equalsIgnoreCase(configName)) {
            throw new InvalidRequestException(
                    "The '" + defaultName + "' configuration is system-reserved and cannot be created manually.");
        }
        repository.findByName(configName).ifPresent(c -> {
            throw new InvalidRequestException("Configuration with name '" + configName + "' already exists.");
        });

        if (Boolean.TRUE.equals(command.getEnabled())) {
            disableAllConfigs();
        }

        PensionRuleConfig newConfig = new PensionRuleConfig(
                configName,
                command.getEnabled(),
                command.getMaxBasicPensionAmount(),
                command.getMaxBasicPensionMonths(),
                command.getKouseiMultiplierPerMille(),
                command.getKouseiSalaryCapMonthly(),
                command.getKouseiBonusCapPerPayment());

        repository.save(newConfig);
        logger.info("Created PensionRuleConfig: {}", configName);
        return PensionRuleConfigResult.fromEntity(newConfig);
    }

    @Override
    @Transactional
    public PensionRuleConfigResult updateConfig(String name, UpdatePensionRuleConfigCommand command) {
        PensionRuleConfig config = repository.findByName(name)
                .orElseThrow(() -> new ConfigNotFoundException("PensionRuleConfig '" + name + "' not found."));

        if (Boolean.TRUE.equals(command.getEnabled()) && !Boolean.TRUE.equals(config.getEnabled())) {
            disableAllConfigs();
        }

        if (command.getEnabled() != null) {
            config.setEnabled(command.getEnabled());
        }
        if (command.getMaxBasicPensionAmount() != null) {
            config.setMaxBasicPensionAmount(command.getMaxBasicPensionAmount());
        }
        if (command.getMaxBasicPensionMonths() != null) {
            config.setMaxBasicPensionMonths(command.getMaxBasicPensionMonths());
        }
        if (command.getKouseiMultiplierPerMille() != null) {
            config.setKouseiMultiplierPerMille(command.getKouseiMultiplierPerMille());
        }
        if (command.getKouseiSalaryCapMonthly() != null) {
            config.setKouseiSalaryCapMonthly(command.getKouseiSalaryCapMonthly());
        }
        if (command.getKouseiBonusCapPerPayment() != null) {
            config.setKouseiBonusCapPerPayment(command.getKouseiBonusCapPerPayment());
        }

        repository.save(Objects.requireNonNull(config));
        logger.info("Updated PensionRuleConfig: {}", name);
        return PensionRuleConfigResult.fromEntity(config);
    }

    @Override
    @Transactional
    public void deleteConfig(String name) {
        if (defaultProperties.getName().equalsIgnoreCase(name)) {
            throw new InvalidRequestException(
                    "The '" + defaultProperties.getName()
                            + "' configuration is system-reserved and cannot be deleted.");
        }
        PensionRuleConfig config = repository.findByName(name)
                .orElseThrow(() -> new ConfigNotFoundException("PensionRuleConfig '" + name + "' not found."));
        repository.delete(Objects.requireNonNull(config));
        logger.info("Deleted PensionRuleConfig: {}", name);
    }

    private void disableAllConfigs() {
        List<PensionRuleConfig> enabledConfigs = repository.findAllByEnabledTrue();
        enabledConfigs.forEach(c -> c.setEnabled(false));
        repository.saveAll(enabledConfigs);
    }
}
