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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import ch.lin.finance.hub.backend.api.app.config.system.DefaultConfigFactory;
import ch.lin.finance.hub.backend.api.app.config.system.FinanceHubDefaultProperties;
import ch.lin.finance.hub.backend.api.app.repository.config.FinanceHubConfigRepository;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.CreateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.command.UpdateFinanceHubConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
import ch.lin.platform.exception.ConfigCreationException;
import ch.lin.platform.exception.ConfigNotFoundException;
import ch.lin.platform.exception.InvalidRequestException;

/**
 * Core implementation of {@link FinanceHubConfigService}.
 * <p>
 * Handles the creation, retrieval, updating, and deletion of global system
 * configurations, ensuring that exactly one configuration remains active at any
 * given time. Falls back to a default properties-based configuration if none
 * exists in the database.
 */
@Service
public class FinanceHubConfigServiceImpl implements FinanceHubConfigService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(FinanceHubConfigServiceImpl.class);

    /**
     * Repository for managing {@link FinanceHubConfig} entities.
     */
    private final FinanceHubConfigRepository repository;

    /**
     * Externalized properties for the default configuration.
     */
    private final FinanceHubDefaultProperties defaultProperties;

    /**
     * Factory for creating default config instances.
     */
    private final DefaultConfigFactory factory;

    /**
     * Event publisher used to notify listeners of configuration changes.
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param repository        The repository for {@link FinanceHubConfig}
     *                          entities.
     * @param defaultProperties The externalized properties for the default
     *                          config.
     * @param factory           The factory for creating default config instances.
     * @param eventPublisher    The event publisher used to notify listeners of
     *                          changes.
     */
    public FinanceHubConfigServiceImpl(FinanceHubConfigRepository repository,
            FinanceHubDefaultProperties defaultProperties,
            DefaultConfigFactory factory,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.defaultProperties = defaultProperties;
        this.factory = factory;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Initializes the default configuration on application startup. This avoids
     * creating it within a read-only transaction later.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeDefaultConfigOnStartup() {
        if (repository.count() == 0) {
            FinanceHubConfig defaultConfig = findOrCreateDefaultConfig();
            if (!Boolean.TRUE.equals(defaultConfig.getEnabled())) {
                defaultConfig.setEnabled(true);
                repository.save(defaultConfig);
                logger.info("Enabled '{}' configuration on startup.", defaultConfig.getName());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AllConfigsData getAllConfigs() {
        if (repository.count() == 0) {
            findOrCreateDefaultConfig();
        }
        List<String> allNames = repository.findAll().stream()
                .map(c -> c.getName())
                .collect(Collectors.toList());

        String enabledConfigName = repository.findFirstByEnabledTrue()
                .map(c -> c.getName())
                .orElseGet(() -> defaultProperties.getSystemConfig().getName());

        return new AllConfigsData(enabledConfigName, allNames);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public FinanceHubConfig createConfig(CreateFinanceHubConfigCommand command) {
        String configName = command.getName();
        String defaultName = defaultProperties.getSystemConfig().getName();

        if (defaultName.equalsIgnoreCase(configName)) {
            throw new InvalidRequestException(
                    "The '" + defaultName + "' configuration is system-reserved and cannot be created manually.");
        }
        repository.findByName(configName).ifPresent(c -> {
            throw new InvalidRequestException("Configuration with name '" + configName + "' already exists.");
        });

        if (Boolean.TRUE.equals(command.getEnabled())) {
            List<FinanceHubConfig> enabledConfigs = repository.findAllByEnabledTrue();
            enabledConfigs.forEach(c -> c.setEnabled(false));
            repository.saveAll(enabledConfigs);
        }

        FinanceHubConfig newConfig = factory.create(configName, defaultProperties.getSystemConfig());
        newConfig.setEnabled(command.getEnabled());
        if (command.getDefaultCurrency() != null) {
            newConfig.setDefaultCurrency(command.getDefaultCurrency());
        }
        newConfig.setFileProcessingTimeoutSeconds(command.getFileProcessingTimeoutSeconds());
        if (command.getNisaExpectedReturnRate() != null) {
            newConfig.setNisaExpectedReturnRate(command.getNisaExpectedReturnRate());
        }
        if (command.getIdecoExpectedReturnRate() != null) {
            newConfig.setIdecoExpectedReturnRate(command.getIdecoExpectedReturnRate());
        }
        if (command.getPensionExpectedMonthlySalary() != null) {
            newConfig.setPensionExpectedMonthlySalary(command.getPensionExpectedMonthlySalary());
        }
        if (command.getLifeExpectancy() != null) {
            newConfig.setLifeExpectancy(command.getLifeExpectancy());
        }

        if (command.getIdecoUnlockAge() != null) {
            newConfig.setIdecoUnlockAge(command.getIdecoUnlockAge());
        }
        if (command.getTargetRetirementAge() != null) {
            newConfig.setTargetRetirementAge(command.getTargetRetirementAge());
        }
        if (command.getPensionStartAge() != null) {
            newConfig.setPensionStartAge(command.getPensionStartAge());
        }
        if (command.getDcUnlockAge() != null) {
            newConfig.setDcUnlockAge(command.getDcUnlockAge());
        }

        if (command.getGaPopulationSize() != null) {
            newConfig.setGaPopulationSize(command.getGaPopulationSize());
        }
        if (command.getGaMaxGenerations() != null) {
            newConfig.setGaMaxGenerations(command.getGaMaxGenerations());
        }
        if (command.getGaMutationRate() != null) {
            newConfig.setGaMutationRate(command.getGaMutationRate());
        }
        if (command.getGaEliteRatio() != null) {
            newConfig.setGaEliteRatio(command.getGaEliteRatio());
        }
        if (command.getSimulationEarlyRepaymentMonth() != null) {
            newConfig.setSimulationEarlyRepaymentMonth(command.getSimulationEarlyRepaymentMonth());
        }
        if (command.getRetirementMonthMode() != null) {
            newConfig.setRetirementMonthMode(command.getRetirementMonthMode());
        }

        FinanceHubConfig saved = repository.save(newConfig);
        eventPublisher.publishEvent(new ConfigUpdatedEvent(this));
        return saved;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAllConfigs() {
        logger.info("Deleting all global configurations.");
        repository.deleteAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteConfig(String name) {
        String defaultName = defaultProperties.getSystemConfig().getName();
        if (defaultName.equalsIgnoreCase(name)) {
            throw new InvalidRequestException(
                    "The '" + defaultName + "' configuration is system-reserved and cannot be deleted.");
        }

        FinanceHubConfig config = repository.findByName(name)
                .orElseThrow(() -> new ConfigNotFoundException("Configuration with name '" + name + "' not found."));

        repository.delete(Objects.requireNonNull(config));
        logger.info("Deleted global configuration: {}", name);
        eventPublisher.publishEvent(new ConfigUpdatedEvent(this));

        if (repository.findFirstByEnabledTrue().isEmpty()) {
            FinanceHubConfig defaultConfig = findOrCreateDefaultConfig();
            if (!Boolean.TRUE.equals(defaultConfig.getEnabled())) {
                defaultConfig.setEnabled(true);
                repository.save(defaultConfig);
                logger.info("Enabled '{}' configuration as no other configuration was active.",
                        defaultConfig.getName());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FinanceHubConfig getConfig(String name) {
        String defaultName = defaultProperties.getSystemConfig().getName();
        return repository.findByName(name)
                .or(() -> defaultName.equalsIgnoreCase(name) ? Optional.of(findOrCreateDefaultConfig())
                        : Optional.empty())
                .orElseThrow(() -> new ConfigNotFoundException("Configuration with name '" + name + "' not found."));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public FinanceHubConfig saveConfig(UpdateFinanceHubConfigCommand command) {
        String configName = command.getName();

        if (command.getEnabled().orElse(false)) {
            repository.findAllByEnabledTrue().stream()
                    .filter(c -> !c.getName().equals(configName))
                    .forEach(c -> {
                        c.setEnabled(false);
                        repository.save(c);
                    });
        }

        FinanceHubConfig config = repository.findByName(configName)
                .orElseGet(() -> {
                    logger.info("No global configuration found with name '{}'. Creating a new one.", configName);
                    return factory.create(configName, defaultProperties.getSystemConfig());
                });

        command.getEnabled().ifPresent(config::setEnabled);
        command.getDefaultCurrency().ifPresent(config::setDefaultCurrency);
        command.getFileProcessingTimeoutSeconds().ifPresent(config::setFileProcessingTimeoutSeconds);
        command.getNisaExpectedReturnRate().ifPresent(config::setNisaExpectedReturnRate);
        command.getIdecoExpectedReturnRate().ifPresent(config::setIdecoExpectedReturnRate);
        command.getPensionExpectedMonthlySalary().ifPresent(config::setPensionExpectedMonthlySalary);
        command.getLifeExpectancy().ifPresent(config::setLifeExpectancy);

        command.getIdecoUnlockAge().ifPresent(config::setIdecoUnlockAge);
        command.getTargetRetirementAge().ifPresent(config::setTargetRetirementAge);
        command.getPensionStartAge().ifPresent(config::setPensionStartAge);
        command.getDcUnlockAge().ifPresent(config::setDcUnlockAge);
        command.getGaPopulationSize().ifPresent(config::setGaPopulationSize);
        command.getGaMaxGenerations().ifPresent(config::setGaMaxGenerations);
        command.getGaMutationRate().ifPresent(config::setGaMutationRate);
        command.getGaEliteRatio().ifPresent(config::setGaEliteRatio);
        command.getSimulationEarlyRepaymentMonth().ifPresent(config::setSimulationEarlyRepaymentMonth);
        command.getRetirementMonthMode().ifPresent(config::setRetirementMonthMode);

        FinanceHubConfig savedConfig = repository.save(config);

        // After saving, if no configuration is enabled, enable the default one.
        if (repository.findFirstByEnabledTrue().isEmpty()) {
            FinanceHubConfig defaultConfig = findOrCreateDefaultConfig();
            defaultConfig.setEnabled(true);
            repository.save(defaultConfig);
        }

        // Publish an event to notify that the configuration has been updated
        eventPublisher.publishEvent(new ConfigUpdatedEvent(this));

        return savedConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public FinanceHubConfig getResolvedConfig(String configName) {
        Optional<FinanceHubConfig> configOpt = Optional.empty();

        if (StringUtils.hasText(configName)) {
            configOpt = repository.findByName(configName);
        }

        if (configOpt.isEmpty()) {
            logger.debug("Config '{}' not found or not specified. Searching for default enabled config.", configName);
            configOpt = repository.findFirstByEnabledTrue();
        }

        if (configOpt.isEmpty() && repository.count() == 0) {
            return findOrCreateDefaultConfig();
        }

        return configOpt.map(dbConfig -> {
            logger.debug("Using config '{}' from database and resolving with defaults.", dbConfig.getName());
            FinanceHubDefaultProperties.SystemConfig defaultProps = defaultProperties.getSystemConfig();

            if (dbConfig.getDefaultCurrency() == null) {
                dbConfig.setDefaultCurrency(defaultProps.getDefaultCurrency());
            }
            if (dbConfig.getFileProcessingTimeoutSeconds() == null) {
                dbConfig.setFileProcessingTimeoutSeconds(defaultProps.getFileProcessingTimeoutSeconds());
            }
            if (dbConfig.getNisaExpectedReturnRate() == null) {
                dbConfig.setNisaExpectedReturnRate(defaultProps.getNisaExpectedReturnRate());
            }
            if (dbConfig.getIdecoExpectedReturnRate() == null) {
                dbConfig.setIdecoExpectedReturnRate(defaultProps.getIdecoExpectedReturnRate());
            }
            if (dbConfig.getPensionExpectedMonthlySalary() == null) {
                dbConfig.setPensionExpectedMonthlySalary(defaultProps.getPensionExpectedMonthlySalary());
            }
            if (dbConfig.getIdecoUnlockAge() == null) {
                dbConfig.setIdecoUnlockAge(defaultProps.getIdecoUnlockAge());
            }
            if (dbConfig.getTargetRetirementAge() == null) {
                dbConfig.setTargetRetirementAge(defaultProps.getTargetRetirementAge());
            }
            if (dbConfig.getPensionStartAge() == null) {
                dbConfig.setPensionStartAge(defaultProps.getPensionStartAge());
            }
            if (dbConfig.getDcUnlockAge() == null) {
                dbConfig.setDcUnlockAge(defaultProps.getDcUnlockAge());
            }
            if (dbConfig.getGaPopulationSize() == null) {
                dbConfig.setGaPopulationSize(defaultProps.getGaPopulationSize());
            }
            if (dbConfig.getGaMaxGenerations() == null) {
                dbConfig.setGaMaxGenerations(defaultProps.getGaMaxGenerations());
            }
            if (dbConfig.getGaMutationRate() == null) {
                dbConfig.setGaMutationRate(defaultProps.getGaMutationRate());
            }
            if (dbConfig.getGaEliteRatio() == null) {
                dbConfig.setGaEliteRatio(defaultProps.getGaEliteRatio());
            }
            if (dbConfig.getSimulationEarlyRepaymentMonth() == null) {
                dbConfig.setSimulationEarlyRepaymentMonth(defaultProps.getSimulationEarlyRepaymentMonth());
            }
            if (dbConfig.getRetirementMonthMode() == null) {
                dbConfig.setRetirementMonthMode(defaultProps.getRetirementMonthMode());
            }
            return dbConfig;
        }).orElseGet(this::findOrCreateDefaultConfig);
    }

    /**
     * Attempts to find a default system configuration in the database; if not
     * found, creates one based on {@link FinanceHubDefaultProperties} and
     * persists it.
     *
     * @return The fallback default {@link FinanceHubConfig}.
     * @throws ConfigCreationException If creating the default config fails.
     */
    private FinanceHubConfig findOrCreateDefaultConfig() {
        FinanceHubDefaultProperties.SystemConfig defaultProps = defaultProperties.getSystemConfig();
        String configName = defaultProps.getName();

        return repository.findByName(configName).orElseGet(() -> {
            logger.info("No system '{}' configuration found. Creating one with application properties.", configName);
            FinanceHubConfig defaultConfig = factory.create(defaultProps);
            try {
                return repository.save(Objects.requireNonNull(defaultConfig));
            } catch (Exception e) {
                logger.error("Failed to create default system config", e);
                throw new ConfigCreationException(
                        "Cannot create default system config. Application properties might be invalid.");
            }
        });
    }
}
