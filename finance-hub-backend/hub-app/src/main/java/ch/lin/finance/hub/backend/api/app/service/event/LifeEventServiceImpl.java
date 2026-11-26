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
package ch.lin.finance.hub.backend.api.app.service.event;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.user.LifeEventRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.LifeEventResult;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;

@Service
public class LifeEventServiceImpl implements LifeEventService {

    private final LifeEventRepository lifeEventRepository;

    public LifeEventServiceImpl(LifeEventRepository lifeEventRepository) {
        this.lifeEventRepository = lifeEventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LifeEventResult> getAllEvents() {
        return lifeEventRepository.findAllByOrderByStartYearMonthAsc().stream()
                .map(LifeEventResult::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public LifeEventResult createEvent(YearMonth startYearMonth, YearMonth endYearMonth, LifeEventFrequency frequency,
            String eventName, Long costPerOccurrence) {
        LifeEvent event = new LifeEvent(
                startYearMonth,
                endYearMonth,
                frequency,
                eventName,
                costPerOccurrence);
        LifeEvent savedEvent = lifeEventRepository.save(event);
        return LifeEventResult.fromEntity(savedEvent);
    }

    @Override
    @Transactional
    public void deleteEvent(Long id) {
        lifeEventRepository.deleteById(Objects.requireNonNull(id));
    }
}
