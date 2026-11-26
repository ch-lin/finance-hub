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
package ch.lin.finance.hub.backend.api.controller.profile.child;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.profile.child.ChildProfileService;
import ch.lin.finance.hub.backend.api.app.service.profile.child.command.CreateChildProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.command.UpdateChildProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.result.ChildProfileResult;
import ch.lin.finance.hub.backend.api.controller.profile.child.dto.ChildProfileDto;
import ch.lin.finance.hub.backend.api.controller.profile.child.dto.ChildProfileMapper;
import ch.lin.finance.hub.backend.api.controller.profile.child.dto.CreateChildProfileRequest;
import ch.lin.finance.hub.backend.api.controller.profile.child.dto.EducationProjectionDto;
import ch.lin.finance.hub.backend.api.controller.profile.child.dto.UpdateChildProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/profile/children")
@RequiredArgsConstructor
public class ChildProfileController {

    private final ChildProfileService childProfileService;
    private final ChildProfileMapper childProfileMapper;

    @GetMapping
    public List<ChildProfileDto> getChildProfiles() {
        return childProfileService.getChildProfiles().stream()
                .map(childProfileMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChildProfileDto createChildProfile(@Valid @RequestBody CreateChildProfileRequest request) {
        CreateChildProfileCommand command = childProfileMapper.toCommand(request);
        ChildProfileResult created = childProfileService.createChildProfile(command);
        return childProfileMapper.toDto(created);
    }

    @PatchMapping("/{id}")
    public ChildProfileDto updateChildProfile(@PathVariable Long id,
            @Valid @RequestBody UpdateChildProfileRequest request) {
        UpdateChildProfileCommand command = childProfileMapper.toCommand(request);
        ChildProfileResult updated = childProfileService.updateChildProfile(id, command);
        return childProfileMapper.toDto(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChildProfile(@PathVariable Long id) {
        childProfileService.deleteChildProfile(id);
    }

    @GetMapping("/{id}/projection")
    public List<EducationProjectionDto> getChildProjection(@PathVariable Long id) {
        return childProfileService.getEducationProjections(id).stream()
                .map(childProfileMapper::toDto)
                .collect(Collectors.toList());
    }

}
