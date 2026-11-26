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
package ch.lin.finance.hub.backend.api.controller.event;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.event.CareerEventService;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.CareerEventResult;
import ch.lin.finance.hub.backend.api.controller.event.dto.CareerEventMapper;
import ch.lin.finance.hub.backend.api.controller.event.dto.CareerEventRequest;
import ch.lin.finance.hub.backend.api.controller.event.dto.CareerEventResponse;
import ch.lin.platform.api.ApiResponse;

@RestController
@RequestMapping("/simulation/career-events")
public class CareerEventController {

    private final CareerEventService careerEventService;
    private final CareerEventMapper careerEventMapper;

    @Autowired
    public CareerEventController(CareerEventService careerEventService, CareerEventMapper careerEventMapper) {
        this.careerEventService = careerEventService;
        this.careerEventMapper = careerEventMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CareerEventResponse>>> getAllEvents() {
        List<CareerEventResponse> events = careerEventService.getAllEvents()
                .stream()
                .map(careerEventMapper::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CareerEventResponse>> createEvent(
            @RequestBody @NonNull CareerEventRequest request) {
        CareerEventResult savedEvent = careerEventService.createEvent(
                request.eventYearMonth(),
                request.eventName(),
                request.eventType(),
                request.eventValue());
        return ResponseEntity.ok(ApiResponse.success(careerEventMapper.toDto(savedEvent)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable @NonNull Long id) {
        careerEventService.deleteEvent(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
