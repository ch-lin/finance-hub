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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import ch.lin.finance.hub.backend.api.app.config.system.DefaultConfigFactory;
import ch.lin.finance.hub.backend.api.app.config.system.FinanceHubDefaultProperties;
import ch.lin.finance.hub.backend.api.app.repository.config.SchedulingConfigRepository;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.CreateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.command.UpdateSchedulingConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.scheduler.result.TimeZoneOption;
import ch.lin.finance.hub.backend.api.app.service.config.system.ConfigUpdatedEvent;
import ch.lin.finance.hub.backend.api.app.service.config.system.result.AllConfigsData;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.SchedulingConfig;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.platform.exception.ConfigCreationException;
import ch.lin.platform.exception.ConfigNotFoundException;
import ch.lin.platform.exception.InvalidRequestException;

/**
 * Service implementation for managing hub configurations.
 * <p>
 * This class provides the concrete logic for operations defined in the
 * {@link SchedulingConfigService} interface. It handles interactions
 * with the {@link SchedulingConfigRepository} and uses a
 * {@link DefaultConfigFactory} to manage a system-level default configuration.
 */
@Service
public class SchedulingConfigServiceImpl implements SchedulingConfigService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(SchedulingConfigServiceImpl.class);

    /**
     * Repository for managing {@link SchedulingConfig} entities.
     */
    private final SchedulingConfigRepository schedulingConfigRepository;

    /**
     * Externalized properties for the default configurations.
     */
    private final FinanceHubDefaultProperties defaultProperties;

    /**
     * Factory for creating default config instances.
     */
    private final DefaultConfigFactory defaultConfigFactory;

    /**
     * Event publisher used to notify listeners of configuration changes.
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param schedulingConfigRepository The repository for
     *                                   {@link SchedulingConfig}
     *                                   entities.
     * @param defaultProperties          The externalized properties for the
     *                                   default
     *                                   config.
     * @param defaultConfigFactory       The factory for creating default
     *                                   config
     *                                   instances.
     */
    public SchedulingConfigServiceImpl(SchedulingConfigRepository schedulingConfigRepository,
            FinanceHubDefaultProperties defaultProperties,
            DefaultConfigFactory defaultConfigFactory,
            ApplicationEventPublisher eventPublisher) {
        this.schedulingConfigRepository = schedulingConfigRepository;
        this.defaultProperties = defaultProperties;
        this.defaultConfigFactory = defaultConfigFactory;
        this.eventPublisher = eventPublisher;
    }

    /**
     * This avoids creating it within a read-only transaction later.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeDefaultConfigOnStartup() {
        for (TransactionSource source : TransactionSource.values()) {
            if (schedulingConfigRepository.findAllByTransactionSource(source).isEmpty()) {
                SchedulingConfig defaultConfig = findOrCreateDefaultConfig(source);
                if (!Boolean.TRUE.equals(defaultConfig.getEnabled())) {
                    defaultConfig.setEnabled(true);
                    schedulingConfigRepository.save(defaultConfig);
                    logger.info("Enabled '{}' configuration for source '{}' on startup.", defaultConfig.getName(),
                            source);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation ensures that a 'default' configuration exists by
     * creating one if the database is empty. It also provides 'default' as a
     * fallback name if no other configuration is explicitly enabled.
     */
    @Override
    public AllConfigsData getAllConfigs(TransactionSource transactionSource) {
        List<SchedulingConfig> configs = schedulingConfigRepository
                .findAllByTransactionSource(transactionSource);
        if (configs.isEmpty()) {
            configs = List.of(findOrCreateDefaultConfig(transactionSource));
        }
        List<String> allNames = configs.stream().map(c -> c.getName()).collect(Collectors.toList());

        String enabledConfigName = schedulingConfigRepository
                .findFirstByTransactionSourceAndEnabledTrue(transactionSource)
                .map(c -> c.getName())
                .orElseGet(() -> getDefaultConfigProperties(transactionSource).getName());

        return new AllConfigsData(enabledConfigName, allNames);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation prevents the creation of a configuration named
     * 'default' as it is system-reserved. If the new configuration is set to be
     * enabled, it will automatically disable any other currently enabled
     * configuration to ensure only one is active at a time.
     */
    @Override
    @Transactional
    public SchedulingConfig createConfig(CreateSchedulingConfigCommand command) {
        String configName = command.getName();
        TransactionSource transactionSource = command.getTransactionSource();

        if (isDefaultConfigName(configName)) {
            throw new InvalidRequestException(
                    "The name '" + configName + "' is system-reserved and cannot be created manually.");
        }
        schedulingConfigRepository.findByTransactionSourceAndName(transactionSource, configName).ifPresent(c -> {
            throw new InvalidRequestException("Configuration with name '" + configName + "' already exists.");
        });

        // If this new config is being enabled, disable all others for the same
        // transaction source first.
        if (Boolean.TRUE.equals(command.getEnabled())) {
            List<SchedulingConfig> enabledConfigs = schedulingConfigRepository
                    .findAllByTransactionSource(transactionSource).stream()
                    .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                    .collect(Collectors.toList());
            enabledConfigs.forEach(config -> config.setEnabled(false));
            schedulingConfigRepository.saveAll(enabledConfigs);
        }

        SchedulingConfig newConfig = new SchedulingConfig(configName, transactionSource);
        newConfig.setEnabled(command.getEnabled());
        newConfig.setAutoStartImportScheduler(command.getAutoStartImportScheduler());
        newConfig.setSchedulerType(command.getSchedulerType());
        newConfig.setFixedRate(command.getFixedRate());
        validateCronExpression(command.getCronExpression());
        newConfig.setCronExpression(command.getCronExpression());
        validateCronTimeZone(command.getCronTimeZone());
        newConfig.setCronTimeZone(command.getCronTimeZone());

        SchedulingConfig saved = schedulingConfigRepository.save(newConfig);
        eventPublisher.publishEvent(new ConfigUpdatedEvent(this));
        return saved;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses a bulk delete operation for efficiency.
     */
    @Override
    public void deleteAllConfigs() {
        logger.info("Deleting all configurations.");
        schedulingConfigRepository.cleanTable();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation prevents the deletion of the system-reserved
     * 'default' configuration. After deleting a configuration, it checks if any
     * other configuration remains enabled. If not, it automatically enables the
     * 'default' configuration as a fallback. It may also create the default
     * config if it does not exist.
     */
    @Override
    @Transactional
    public void deleteConfig(TransactionSource transactionSource, String name) {
        if (isDefaultConfigName(name)) {
            throw new InvalidRequestException(
                    "The configuration '" + name + "' is system-reserved and cannot be deleted.");
        }
        SchedulingConfig config = schedulingConfigRepository
                .findByTransactionSourceAndName(transactionSource, name)
                .orElseThrow(() -> new ConfigNotFoundException("Configuration with name '" + name + "' not found."));

        schedulingConfigRepository.delete(Objects.requireNonNull(config));
        logger.info("Deleted configuration: {}", name);

        // After deleting, if no other configuration is enabled, enable the default one.
        if (schedulingConfigRepository.findFirstByTransactionSourceAndEnabledTrue(transactionSource).isEmpty()) {
            SchedulingConfig defaultConfig = findOrCreateDefaultConfig(transactionSource);
            if (!Boolean.TRUE.equals(defaultConfig.getEnabled())) {
                defaultConfig.setEnabled(true);
                schedulingConfigRepository.save(defaultConfig);
                logger.info("Enabled '{}' configuration as no other configuration was active for {}.",
                        defaultConfig.getName(), transactionSource);
            }
        }
        eventPublisher.publishEvent(new ConfigUpdatedEvent(this));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation provides special handling for the 'default'
     * configuration, creating it if it doesn't exist.
     */
    @Override
    public SchedulingConfig getConfig(TransactionSource transactionSource, String name) {
        return schedulingConfigRepository.findByTransactionSourceAndName(transactionSource, name)
                .or(() -> {
                    for (Map.Entry<TransactionSource, FinanceHubDefaultProperties.SourceConfig> entry : defaultProperties
                            .getTransactionSourceConfigs().entrySet()) {
                        if (entry.getKey() == transactionSource && entry.getValue().getName().equalsIgnoreCase(name)) {
                            return Optional.of(findOrCreateDefaultConfig(entry.getKey()));
                        }
                    }
                    return Optional.empty();
                })
                .orElseThrow(() -> new ConfigNotFoundException("Configuration with name '" + name + "' not found."));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation performs an "upsert" operation: it updates the
     * configuration if it exists, or creates a new one if it does not. If a
     * configuration is being enabled, it ensures all others are disabled first.
     * It also guarantees that the 'default' configuration is enabled if no
     * other configuration is active after the save operation.
     */
    @Override
    @Transactional
    public SchedulingConfig saveConfig(TransactionSource transactionSource,
            UpdateSchedulingConfigCommand command) {
        String configName = command.getName();

        SchedulingConfig config = schedulingConfigRepository
                .findByTransactionSourceAndName(transactionSource, configName)
                .orElseThrow(
                        () -> new ConfigNotFoundException("Configuration with name '" + configName + "' not found."));

        // If this config is being enabled, disable all others for the same transaction
        // source first.
        if (command.getEnabled().orElse(false)) {
            schedulingConfigRepository.findAllByTransactionSource(transactionSource).stream()
                    .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                    .filter(c -> !c.getName().equals(configName))
                    .forEach(c -> {
                        c.setEnabled(false);
                        schedulingConfigRepository.save(c);
                    });
        }

        // Apply updates from the command
        command.getEnabled().ifPresent(config::setEnabled);
        command.getAutoStartImportScheduler().ifPresent(config::setAutoStartImportScheduler);
        command.getSchedulerType().ifPresent(config::setSchedulerType);
        command.getFixedRate().ifPresent(config::setFixedRate);
        command.getCronExpression().ifPresent(this::validateCronExpression);
        command.getCronExpression().ifPresent(config::setCronExpression);
        command.getCronTimeZone().ifPresent(this::validateCronTimeZone);
        command.getCronTimeZone().ifPresent(config::setCronTimeZone);

        SchedulingConfig savedConfig = schedulingConfigRepository.save(config);

        // After saving, if no configuration is enabled for this transaction source,
        // enable the default one.
        if (schedulingConfigRepository.findFirstByTransactionSourceAndEnabledTrue(transactionSource).isEmpty()) {
            SchedulingConfig defaultConfig = findOrCreateDefaultConfig(transactionSource);
            defaultConfig.setEnabled(true);
            schedulingConfigRepository.save(defaultConfig);
        }

        // Publish an event to notify that the configuration has been updated
        eventPublisher.publishEvent(new ConfigUpdatedEvent(this));

        return savedConfig;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation follows a specific resolution order:
     * <ol>
     * <li>A configuration matching the provided {@code configName}.</li>
     * <li>The first currently enabled configuration found in the database.</li>
     * <li>The 'default' configuration (creating it if it doesn't exist).</li>
     * </ol>
     * When a database-stored configuration is resolved, any of its null or
     * empty fields are populated with values from the default application
     * properties.
     */
    @Override
    @Transactional
    public SchedulingConfig getResolvedConfig(TransactionSource transactionSource, String configName) {
        Optional<SchedulingConfig> configOpt = Optional.empty();

        if (StringUtils.hasText(configName)) {
            configOpt = schedulingConfigRepository.findByTransactionSourceAndName(transactionSource, configName);
        }

        if (configOpt.isEmpty()) {
            logger.debug("Config '{}' not found. Searching for default enabled config for transaction source {}.",
                    configName, transactionSource);
            configOpt = schedulingConfigRepository.findFirstByTransactionSourceAndEnabledTrue(transactionSource);
        }

        // If still no config, create the 'default' one.
        if (configOpt.isEmpty()
                && schedulingConfigRepository.findAllByTransactionSource(transactionSource).isEmpty()) {
            return findOrCreateDefaultConfig(transactionSource);
        }

        // Use the found config or fall back to application properties if no config is
        // active.
        return configOpt.map(dbConfig -> {
            logger.debug("Using config '{}' from database and resolving with defaults.", dbConfig.getName());
            FinanceHubDefaultProperties.SourceConfig defaultProps = getDefaultConfigProperties(transactionSource);

            if (dbConfig.getEnabled() == null) {
                dbConfig.setEnabled(defaultProps.getEnabled());
            }
            if (dbConfig.getAutoStartImportScheduler() == null) {
                dbConfig.setAutoStartImportScheduler(defaultProps.getAutoStartImportScheduler());
            }
            if (dbConfig.getSchedulerType() == null) {
                dbConfig.setSchedulerType(defaultProps.getSchedulerType());
            }
            if (dbConfig.getFixedRate() == null) {
                dbConfig.setFixedRate(defaultProps.getFixedRate());
            }
            if (!StringUtils.hasText(dbConfig.getCronExpression())) {
                dbConfig.setCronExpression(defaultProps.getCronExpression());
            }
            if (!StringUtils.hasText(dbConfig.getCronTimeZone())) {
                dbConfig.setCronTimeZone(defaultProps.getCronTimeZone());
            }
            return dbConfig;
        }).orElseGet(() -> findOrCreateDefaultConfig(transactionSource));
    }

    /**
     * Finds the 'default' configuration in the database or creates it if it
     * does not exist. This method is idempotent and ensures a default
     * configuration is always available.
     *
     * @param transactionSource The transaction source.
     * @return The 'default' {@link SchedulingConfig} entity.
     * @throws ConfigCreationException if the default configuration cannot be
     *                                 created from application properties.
     */
    private SchedulingConfig findOrCreateDefaultConfig(TransactionSource transactionSource) {
        FinanceHubDefaultProperties.SourceConfig defaultProps = getDefaultConfigProperties(transactionSource);
        String defaultName = defaultProps.getName();

        return schedulingConfigRepository.findByTransactionSourceAndName(transactionSource, defaultName)
                .orElseGet(() -> {
                    logger.info("No '{}' configuration found. Creating one with application properties.", defaultName);
                    SchedulingConfig defaultConfig = defaultConfigFactory.create(transactionSource,
                            defaultProps);
                    try {
                        return schedulingConfigRepository.save(Objects.requireNonNull(defaultConfig));
                    } catch (Exception e) {
                        logger.error("Failed to create default config for transaction source {}", transactionSource, e);
                        throw new ConfigCreationException("Cannot create default config for transaction source "
                                + transactionSource + ". Application properties might be invalid.");
                    }
                });
    }

    /**
     * Checks if the given name matches any of the reserved default
     * configuration names.
     */
    private boolean isDefaultConfigName(String name) {
        return defaultProperties.getTransactionSourceConfigs().values().stream()
                .anyMatch(dc -> dc.getName().equalsIgnoreCase(name));
    }

    /**
     * Safely retrieves the default configuration properties for a given
     * transaction source.
     *
     * @param transactionSource The transaction source.
     * @return The default configuration properties.
     * @throws ConfigCreationException if no default configuration is defined.
     */
    private FinanceHubDefaultProperties.SourceConfig getDefaultConfigProperties(TransactionSource transactionSource) {
        FinanceHubDefaultProperties.SourceConfig defaultProps = defaultProperties.getTransactionSourceConfigs()
                .get(transactionSource);
        if (defaultProps == null) {
            throw new ConfigCreationException(
                    "No default configuration defined for transaction source: " + transactionSource);
        }
        return defaultProps;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Retrieves a list of available time zones, filtered and sorted for better
     * user experience.
     * <ul>
     * <li>Filters out technical or deprecated IDs (e.g., "Etc/",
     * "SystemV/").</li>
     * <li>Formats the display name to include the current UTC offset (e.g.,
     * "(UTC+08:00) Asia/Taipei").</li>
     * <li>Sorts the list with specific priority: System Default > Asia/Tokyo >
     * Asia/Taipei > Alphabetical order.</li>
     * </ul>
     */
    @Override
    public List<TimeZoneOption> getTimeZones() {
        String systemZoneId = ZoneId.systemDefault().getId();
        return ZoneId.getAvailableZoneIds().stream()
                // Filter out technical or deprecated IDs (e.g., Etc/, SystemV/, 3-letter codes
                // without slash)
                .filter(id -> id.contains("/") && !id.startsWith("Etc/") && !id.startsWith("SystemV/"))
                .map(id -> {
                    ZoneId zoneId = ZoneId.of(id);
                    String offset = zoneId.getRules().getOffset(Instant.now()).getId();
                    String displayName = String.format("(UTC%s) %s", offset.equals("Z") ? "+00:00" : offset, id);
                    return new TimeZoneOption(id, displayName);
                })
                .sorted(Comparator.comparingInt((TimeZoneOption o) -> {
                    if (systemZoneId.equals(o.id())) {
                        return 0;
                    }
                    if ("Asia/Tokyo".equals(o.id())) {
                        return 1;
                    }
                    if ("Asia/Taipei".equals(o.id())) {
                        return 2;
                    }
                    return 3;
                }).thenComparing(t -> t.id()))
                .collect(Collectors.toList());
    }

    /**
     * Validates the format of a Cron expression.
     *
     * @param cronExpression The Cron expression string to validate.
     * @throws InvalidRequestException if the expression is not null/empty and
     *                                 is invalid.
     */
    private void validateCronExpression(String cronExpression) {
        if (StringUtils.hasText(cronExpression) && !CronExpression.isValidExpression(cronExpression)) {
            throw new InvalidRequestException("Invalid cron expression: " + cronExpression);
        }
    }

    /**
     * Validates that a given string is a valid time zone ID.
     *
     * @param cronTimeZone The time zone ID to validate (e.g., "Asia/Taipei").
     * @throws InvalidRequestException if the time zone ID is not null/empty and
     *                                 is invalid.
     */
    private void validateCronTimeZone(String cronTimeZone) {
        if (StringUtils.hasText(cronTimeZone)) {
            try {
                ZoneId.of(cronTimeZone);
            } catch (DateTimeException e) {
                throw new InvalidRequestException("Invalid cron time zone: " + cronTimeZone);
            }
        }
    }
}
