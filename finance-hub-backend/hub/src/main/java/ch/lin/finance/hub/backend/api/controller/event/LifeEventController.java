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

import ch.lin.finance.hub.backend.api.app.service.event.LifeEventService;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.result.LifeEventResult;
import ch.lin.finance.hub.backend.api.controller.event.dto.LifeEventMapper;
import ch.lin.finance.hub.backend.api.controller.event.dto.LifeEventRequest;
import ch.lin.finance.hub.backend.api.controller.event.dto.LifeEventResponse;
import ch.lin.platform.api.ApiResponse;

@RestController
@RequestMapping("/simulation/life-events")
public class LifeEventController {

    private final LifeEventService lifeEventService;
    private final LifeEventMapper lifeEventMapper;

    @Autowired
    public LifeEventController(LifeEventService lifeEventService, LifeEventMapper lifeEventMapper) {
        this.lifeEventService = lifeEventService;
        this.lifeEventMapper = lifeEventMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LifeEventResponse>>> getAllEvents() {
        List<LifeEventResponse> events = lifeEventService.getAllEvents()
                .stream()
                .map(lifeEventMapper::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LifeEventResponse>> createEvent(
            @RequestBody @NonNull LifeEventRequest request) {
        LifeEventResult savedEvent = lifeEventService.createEvent(
                request.startYearMonth(),
                request.endYearMonth(),
                request.frequency(),
                request.eventName(),
                request.costPerOccurrence());
        return ResponseEntity.ok(ApiResponse.success(lifeEventMapper.toDto(savedEvent)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable @NonNull Long id) {
        lifeEventService.deleteEvent(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
