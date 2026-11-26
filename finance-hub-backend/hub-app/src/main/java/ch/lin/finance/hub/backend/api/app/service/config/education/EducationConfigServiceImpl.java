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
package ch.lin.finance.hub.backend.api.app.service.config.education;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigFactory;
import ch.lin.finance.hub.backend.api.app.repository.config.education.EducationConfigRepository;
import ch.lin.finance.hub.backend.api.app.service.config.education.command.CreateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.command.UpdateEducationConfigCommand;
import ch.lin.finance.hub.backend.api.app.service.config.education.result.EducationConfigResult;
import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EducationConfigServiceImpl implements EducationConfigService {

    private final EducationConfigDefaultProperties defaultProperties;
    private final EducationConfigFactory configFactory;
    private final EducationConfigRepository repository;

    @Override
    @Transactional(readOnly = true)
    public EducationConfigResult getDefaultConfig() {
        // First check DB for a config named "default" (if name matches defaultProperties.getName())
        return repository.findByName(defaultProperties.getName())
                .map(EducationConfigResult::fromEntity)
                .orElseGet(() -> {
                    // Fallback to properties
                    EducationConfig config = configFactory.create(defaultProperties);
                    return EducationConfigResult.fromEntity(config);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public EducationConfig getDefaultDomainConfig() {
        return repository.findByName(defaultProperties.getName())
                .orElseGet(() -> configFactory.create(defaultProperties));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EducationConfigResult> getAllConfigs() {
        return repository.findAll().stream()
                .map(EducationConfigResult::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EducationConfigResult getConfig(String name) {
        return repository.findByName(name)
                .map(EducationConfigResult::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("EducationConfig not found with name: " + name));
    }

    @Override
    public EducationConfigResult createConfig(CreateEducationConfigCommand command) {
        if (repository.findByName(command.getName()).isPresent()) {
            throw new IllegalArgumentException("EducationConfig already exists with name: " + command.getName());
        }

        // We use the properties factory pattern to construct from command properties
        EducationConfigDefaultProperties tempProps = new EducationConfigDefaultProperties();
        tempProps.setName(command.getName());
        tempProps.setPreschool(command.getPreschool());
        tempProps.setPrimary(command.getPrimary());
        tempProps.setMiddle(command.getMiddle());
        tempProps.setHigh(command.getHigh());
        tempProps.setUniversity(command.getUniversity());

        EducationConfig config = configFactory.create(tempProps);
        config = repository.save(Objects.requireNonNull(config));

        return EducationConfigResult.fromEntity(config);
    }

    @Override
    public EducationConfigResult updateConfig(String name, UpdateEducationConfigCommand command) {
        EducationConfig existing = repository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("EducationConfig not found with name: " + name));

        EducationConfigDefaultProperties tempProps = new EducationConfigDefaultProperties();
        tempProps.setName(name);
        tempProps.setPreschool(command.getPreschool());
        tempProps.setPrimary(command.getPrimary());
        tempProps.setMiddle(command.getMiddle());
        tempProps.setHigh(command.getHigh());
        tempProps.setUniversity(command.getUniversity());

        EducationConfig updatedConfig = configFactory.create(tempProps);

        // Map updated fields over existing entity
        existing.update(
                updatedConfig.getPreschool(),
                updatedConfig.getPrimary(),
                updatedConfig.getMiddle(),
                updatedConfig.getHigh(),
                updatedConfig.getUniversity());

        existing = repository.save(existing);
        return EducationConfigResult.fromEntity(existing);
    }

    @Override
    public void deleteConfig(String name) {
        if ("default".equals(name) || defaultProperties.getName().equals(name)) {
            throw new IllegalArgumentException("Cannot delete the default configuration");
        }
        repository.deleteByName(name);
    }
}
