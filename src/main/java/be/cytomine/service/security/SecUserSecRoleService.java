package be.cytomine.service.security;

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

import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.command.*;
import be.cytomine.domain.ontology.Ontology;
import be.cytomine.domain.security.SecRole;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.SecUserSecRole;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ForbiddenException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.security.SecRoleRepository;
import be.cytomine.repository.security.SecUserRepository;
import be.cytomine.repository.security.SecUserSecRoleRepository;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@Transactional
public class SecUserSecRoleService extends ModelService {


    @Autowired
    SecurityACLService securityACLService;

    @Autowired
    SecUserSecRoleRepository secUserSecRoleRepository;

    @Autowired
    CurrentUserService currentUserService;

    @Autowired
    SecRoleRepository secRoleRepository;

    @Autowired
    SecUserRepository secUserRepository;

    @Override
    public Class currentDomain() {
        return SecUserSecRoleService.class;
    }

    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new SecUserSecRole().buildDomainFromJson(json, getEntityManager());
    }

    public List<SecUserSecRole> list(User user) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        return secUserSecRoleRepository.findAllBySecUser(user);
    }

    public SecRole getHighest(User user) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());

        List<SecUserSecRole> secUserSecRoles = secUserSecRoleRepository.findAllBySecUser(user);

        Optional<SecUserSecRole> role_super_admin = secUserSecRoles.stream()
                .filter(x -> x.getSecRole().getAuthority().equals("ROLE_SUPER_ADMIN")).findFirst();
        if (role_super_admin.isPresent()) {
            return role_super_admin.get().getSecRole();
        }

        Optional<SecUserSecRole> role_admin = secUserSecRoles.stream()
                .filter(x -> x.getSecRole().getAuthority().equals("ROLE_ADMIN")).findFirst();
        if (role_admin.isPresent()) {
            return role_admin.get().getSecRole();
        }

        Optional<SecUserSecRole> role_user = secUserSecRoles.stream()
                .filter(x -> x.getSecRole().getAuthority().equals("ROLE_USER")).findFirst();
        if (role_user.isPresent()) {
            return role_user.get().getSecRole();
        }

        Optional<SecUserSecRole> role_guest = secUserSecRoles.stream()
                .filter(x -> x.getSecRole().getAuthority().equals("ROLE_GUEST")).findFirst();
        if (role_guest.isPresent()) {
            return role_guest.get().getSecRole();
        }
        return null;
    }


    public Optional<SecUserSecRole> find(SecUser user, SecRole role) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        return secUserSecRoleRepository.findBySecUserAndSecRole(user, role);
    }

    @Override
    public CommandResponse add(JsonObject jsonObject) {
        SecUser currentUser = currentUserService.getCurrentUser();
        SecRole role = secRoleRepository.findById(jsonObject.getJSONAttrLong("role"))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Role", jsonObject.getJSONAttrStr("role")));

        SecUser user = secUserRepository.findById(jsonObject.getJSONAttrLong("user"))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", jsonObject.getJSONAttrStr("user")));

        Set<String> userRoles = secUserSecRoleRepository.findAllBySecUser(user).stream().map(x -> x.getSecRole().getAuthority())
                .collect(Collectors.toSet());

        if (role.getAuthority().equals("ROLE_ADMIN") || role.getAuthority().equals("ROLE_SUPER_ADMIN")) {
            securityACLService.checkAdmin(currentUser);
        } else if (userRoles.size()==1 && userRoles.contains("ROLE_GUEST")) {
            securityACLService.checkAdmin(currentUser);
        }else {
            securityACLService.checkUser(currentUser);
        }




        return executeCommand(new AddCommand(currentUser),null,jsonObject);
    }

    @Override
    public CommandResponse delete(CytomineDomain domain, Transaction transaction, Task task, boolean printMessage) {
        SecUser currentUser = currentUserService.getCurrentUser();
        SecUserSecRole secUserSecRole = (SecUserSecRole)domain;
        if(Objects.equals(secUserSecRole.getSecUser().getId(), currentUser.getId()) && !secUserSecRole.getSecRole().getAuthority().equals("ROLE_SUPER_ADMIN")) {
            throw new ForbiddenException("You cannot remove you a role");
        }
        if(secUserSecRole.getSecUser().isAlgo()) {
            //TODO
//            Job job = ((UserJob)domain.secUser).job
//            securityACLService.check(job?.container(),READ)
        } else {
            securityACLService.checkAdmin(currentUser);
        }
        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c,domain, null);
    }



    public List<Object> getStringParamsI18n(CytomineDomain domain) {
        SecUserSecRole secUserSecRole = (SecUserSecRole)domain;
        return List.of(secUserSecRole.getSecUser().getId(), secUserSecRole.getSecRole().getId());
    }


    /**
     * Define a role for a user. If admin is defined, user will have admin,user,guest. If user is defined, user will have user,guest, etc
     */
    public void define(SecUser user, SecRole role) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkAdmin(currentUser);

        SecRole roleGuest = secRoleRepository.getByAuthority("ROLE_GUEST");
        SecRole roleUser = secRoleRepository.getByAuthority("ROLE_USER");
        SecRole roleAdmin = secRoleRepository.getByAuthority("ROLE_ADMIN");
        SecRole roleSuperAdmin = secRoleRepository.getByAuthority("ROLE_SUPER_ADMIN");

        switch (role.getAuthority()) {
            case "ROLE_SUPER_ADMIN":
                addRole(user, roleGuest);
                addRole(user, roleUser);
                addRole(user, roleAdmin);
                addRole(user, roleSuperAdmin);
                break;
            case "ROLE_ADMIN":
                addRole(user, roleGuest);
                addRole(user, roleUser);
                addRole(user, roleAdmin);
                removeRole(user, roleSuperAdmin);
                break;
            case "ROLE_USER":
                addRole(user, roleGuest);
                addRole(user, roleUser);
                removeRole(user, roleAdmin);
                removeRole(user, roleSuperAdmin);
                break;
            case "ROLE_GUEST":
                addRole(user, roleGuest);
                removeRole(user, roleUser);
                removeRole(user, roleAdmin);
                removeRole(user, roleSuperAdmin);
                break;
        }
    }


    private void addRole(SecUser user,SecRole role) {
        Optional<SecUserSecRole> linked = secUserSecRoleRepository.findBySecUserAndSecRole(user,role);
        if(linked.isEmpty()) {
            SecUserSecRole susr = new SecUserSecRole();
            susr.setSecRole(role);
            susr.setSecUser(user);
            super.saveDomain(susr);
        }
    }
    private void removeRole(SecUser user,SecRole role) {
        Optional<SecUserSecRole> linked = secUserSecRoleRepository.findBySecUserAndSecRole(user,role);
        if(linked.isPresent()) {
            if(Objects.equals(user.getId(), currentUserService.getCurrentUser().getId()) && !role.getAuthority().equals("ROLE_SUPER_ADMIN")) {
                throw new ForbiddenException("You cannot remove you a role");
            }
            super.removeDomain(linked.get());
        }
    }

    /**
     * Retrieve domain thanks to a JSON object
     * @return domain retrieve thanks to json
     */
    @Override
    public CytomineDomain retrieve(JsonObject json) {

       SecUser secUser = secUserRepository.getById(json.getJSONAttrLong("user"));
       SecRole secRole = secRoleRepository.getById(json.getJSONAttrLong("role"));
       return secUserSecRoleRepository.findBySecUserAndSecRole(secUser,secRole)
               .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecUserSecRole", json.toJsonString()));
    }


    public void checkDoNotAlreadyExist(CytomineDomain domain){
        SecUserSecRole secUserSecRole = (SecUserSecRole)domain;
        if(domain!=null) {
            if(secUserSecRoleRepository.findBySecUserAndSecRole(secUserSecRole.getSecUser(), secUserSecRole.getSecRole())
                    .stream().anyMatch(x -> !Objects.equals(x.getId(), secUserSecRole.getId())))  {
                throw new AlreadyExistException("User " + secUserSecRole.getSecUser().getUsername() + " has already role " + secUserSecRole.getSecRole().getAuthority());
            }
        }
    }

}
