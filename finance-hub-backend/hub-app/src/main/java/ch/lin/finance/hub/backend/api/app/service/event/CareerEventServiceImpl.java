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

import ch.lin.finance.hub.backend.api.app.repository.user.CareerEventRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.CareerEventResult;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
import ch.lin.finance.hub.backend.api.domain.model.event.CareerEventType;

@Service
public class CareerEventServiceImpl implements CareerEventService {

    private final CareerEventRepository careerEventRepository;
    private final OwnershipGuard ownershipGuard;

    public CareerEventServiceImpl(CareerEventRepository careerEventRepository, OwnershipGuard ownershipGuard) {
        this.careerEventRepository = careerEventRepository;
        this.ownershipGuard = ownershipGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareerEventResult> getAllEvents() {
        return careerEventRepository.findAllByOrderByEventYearMonthAsc().stream()
                .map(CareerEventResult::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public CareerEventResult createEvent(YearMonth eventYearMonth, String eventName, CareerEventType eventType,
            Double eventValue) {

        if (eventType == CareerEventType.RELATIVE_CHANGE) {
            if (eventValue == null || eventValue < 0.0 || eventValue > 3.0) {
                throw new IllegalArgumentException("Multiplier must be between 0.0 and 3.0");
            }
        }
        if (eventType == CareerEventType.ABSOLUTE_OVERRIDE) {
            if (eventValue == null || eventValue < 0.0) {
                throw new IllegalArgumentException("Absolute override value must be non-negative");
            }
        }

        CareerEvent event = new CareerEvent(
                eventYearMonth,
                eventName,
                eventType,
                eventValue);
        CareerEvent savedEvent = careerEventRepository.save(event);
        return CareerEventResult.fromEntity(savedEvent);
    }

    @Override
    @Transactional
    public void deleteEvent(Long id) {
        CareerEvent event = ownershipGuard.loadOwned(careerEventRepository, id);
        careerEventRepository.delete(Objects.requireNonNull(event));
    }
}
