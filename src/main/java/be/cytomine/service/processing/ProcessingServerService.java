package be.cytomine.service.processing;

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
import be.cytomine.domain.processing.ProcessingServer;
import be.cytomine.domain.security.SecUser;
import be.cytomine.repository.processing.ProcessingServerRepository;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProcessingServerService extends ModelService {

    @Autowired
    private ProcessingServerRepository processingServerRepository;

    @Autowired
    private SecurityACLService securityACLService;

    @Autowired
    private CurrentUserService currentUserService;

    @Override
    public Class currentDomain() {
        return ProcessingServer.class;
    }

    public ProcessingServer get(Long id) {
        return find(id).orElse(null);
    }

    public Optional<ProcessingServer> find(Long id) {
        Optional<ProcessingServer> optionalProcessingServer = processingServerRepository.findById(id);
        optionalProcessingServer.ifPresent(processingServer -> securityACLService.checkUser(currentUserService.getCurrentUser()));
        return optionalProcessingServer;
    }

    public List<ProcessingServer> list() {
        securityACLService.checkUser(currentUserService.getCurrentUser());
        return processingServerRepository.findAll();
    }

    @Override
    public CommandResponse add(JsonObject jsonObject) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkAdmin(currentUser);
        jsonObject.put("user", currentUser.getId());
        return executeCommand(new AddCommand(currentUser),null,jsonObject);
    }

    @Override
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkAdmin(currentUser);
        return executeCommand(new EditCommand(currentUser, transaction), domain,jsonNewData);
    }

    @Override
    public CommandResponse delete(CytomineDomain domain, Transaction transaction, Task task, boolean printMessage) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkAdmin(currentUser);
        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c,domain, null);
    }    


    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new ProcessingServer().buildDomainFromJson(json, getEntityManager());
    }

    public List<Object> getStringParamsI18n(CytomineDomain domain) {
        return List.of(((ProcessingServer)domain).getName());
    }

}
