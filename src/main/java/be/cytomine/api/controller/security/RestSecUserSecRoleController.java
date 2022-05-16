package be.cytomine.api.controller.security;

/*
* Copyright (c) 2009-2022. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import be.cytomine.api.controller.RestCytomineController;
import be.cytomine.domain.security.SecRole;
import be.cytomine.domain.security.SecUserSecRole;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.security.SecRoleRepository;
import be.cytomine.repository.security.UserRepository;
import be.cytomine.service.CurrentRoleService;
import be.cytomine.service.security.SecRoleService;
import be.cytomine.service.security.SecUserSecRoleService;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestSecUserSecRoleController extends RestCytomineController {

    private final SecUserSecRoleService secUserSecRoleService;

    private final UserRepository userRepository;

    private  final SecRoleRepository secRoleRepository;

    private final CurrentRoleService currentRoleService;

    @GetMapping("/user/{user}/role.json")
    public ResponseEntity<String> list(
            @PathVariable("user") Long userId,
            @RequestParam(defaultValue = "false") Boolean highest)
    {
        log.debug("REST request to list roles for user {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        return responseSuccess(highest ? List.of(secUserSecRoleService.getHighest(user)) : secUserSecRoleService.list(user));
    }

    @GetMapping("/user/{user}/role/{role}.json")
    public ResponseEntity<String> get(
            @PathVariable("user") Long userId,
            @PathVariable("role") Long roleId
    ) {
        log.debug("REST request to list roles for user {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        SecRole role = secRoleRepository.findById(roleId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecRole", roleId));
        return responseSuccess(secUserSecRoleService.find(user, role)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecUserSecRole", JsonObject.of("user", user.getId(),"role", role.getId()).toJsonString())));
    }

    @PostMapping("/user/{user}/role.json")
    public ResponseEntity<String> add(
            @PathVariable("user") Long userId,
            @RequestBody JsonObject json
    ) {
        log.debug("REST add role for {}", json.toJsonString());
        return responseSuccess(secUserSecRoleService.add(json));
    }

    @DeleteMapping("/user/{user}/role/{role}.json")
    public ResponseEntity<String> delete(
            @PathVariable("user") Long userId,
            @PathVariable("role") Long roleId
    ) {
        log.debug("REST request to list roles for user {} role {}", userId, roleId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        SecRole role = secRoleRepository.findById(roleId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecRole", roleId));
        SecUserSecRole secUserSecRole = secUserSecRoleService.find(user, role)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecUserSecRole", JsonObject.of("user", user.getId(), "role", role.getId()).toJsonString()));
        return delete(secUserSecRoleService, secUserSecRole.toJsonObject(), null);
    }



    @PutMapping("/user/{user}/role/{role}/define.json")
    public ResponseEntity<String> define(
            @PathVariable("user") Long userId,
            @PathVariable("role") Long roleId
    ) {
        log.debug("REST define role {} for user {}", roleId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        SecRole role = secRoleRepository.findById(roleId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecRole", roleId));
        secUserSecRoleService.define(user, role);
        return responseSuccess(new ArrayList(currentRoleService.findCurrentRole(user)));
    }
}
