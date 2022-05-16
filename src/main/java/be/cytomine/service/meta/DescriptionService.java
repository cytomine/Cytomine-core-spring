package be.cytomine.service.meta;

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
import be.cytomine.domain.image.CompanionFile;
import be.cytomine.domain.meta.Description;
import be.cytomine.domain.ontology.AnnotationDomain;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.meta.DescriptionRepository;
import be.cytomine.repository.ontology.AnnotationDomainRepository;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.security.acls.domain.BasePermission.*;

@Slf4j
@Service
@Transactional
public class DescriptionService extends ModelService {

    @Autowired
    private DescriptionRepository descriptionRepository;

    @Autowired
    private SecurityACLService securityACLService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private AnnotationDomainRepository annotationDomainRepository;

    @Override
    public Class currentDomain() {
        return Description.class;
    }

    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new Description().buildDomainFromJson(json, getEntityManager());
    }

    public List<Description> list() {
        securityACLService.checkAdmin(currentUserService.getCurrentUser());
        return descriptionRepository.findAll();
    }


    public Optional<Description> findByDomain(CytomineDomain domain) {
        securityACLService.check(domain.container() ,READ);
        return descriptionRepository.findByDomainIdentAndDomainClassName(domain.getId(), domain.getClass().getName());
    }

    /**
     * Get a description thanks to its domain info (id and class)
     */
    public Optional<Description> findByDomain(String domainClassName, Long domainIdent) {
        if (domainClassName.contains("AnnotationDomain")) {
            AnnotationDomain annotation = annotationDomainRepository.findById(domainIdent)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("AnnotationDomain", domainIdent));
            domainClassName = annotation.getClass().getName();
        }
        securityACLService.check(domainIdent, domainClassName, READ);
        return descriptionRepository.findByDomainIdentAndDomainClassName(domainIdent, domainClassName);
    }

    public CommandResponse add(JsonObject jsonObject) {
        securityACLService.checkCurrentUserIsUser();
        if(jsonObject.getJSONAttrStr("domainClassName").equals(Project.class.getName())){
            securityACLService.check(jsonObject.getJSONAttrLong("domainIdent"),jsonObject.getJSONAttrStr("domainClassName"),READ);
            securityACLService.checkIsNotReadOnly(jsonObject.getJSONAttrLong("domainIdent"),jsonObject.getJSONAttrStr("domainClassName"));
        } else if(jsonObject.getJSONAttrStr("domainClassName").contains("AnnotationDomain")){
            AnnotationDomain annotation = annotationDomainRepository.findById(jsonObject.getJSONAttrLong("domainIdent"))
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("AnnotationDomain", jsonObject.getJSONAttrLong("domainIdent")));
            jsonObject.put("domainClassName", annotation.getClass().getName());
            securityACLService.check(jsonObject.getJSONAttrLong("domainIdent"),annotation.getClass().getName(),READ);
            securityACLService.checkFullOrRestrictedForOwner(jsonObject.getJSONAttrLong("domainIdent"),annotation.getClass().getName(), "user");
        } else if (!jsonObject.getJSONAttrStr("domainClassName").contains("AbstractImage")){
            securityACLService.check(jsonObject.getJSONAttrLong("domainIdent"),jsonObject.getJSONAttrStr("domainClassName"),READ);
            securityACLService.checkFullOrRestrictedForOwner(jsonObject.getJSONAttrLong("domainIdent"),jsonObject.getJSONAttrStr("domainClassName"), "user");
        }

        SecUser currentUser = currentUserService.getCurrentUser();
        Command command = new AddCommand(currentUser,null);
        return executeCommand(command,null, jsonObject);
    }

    @Override
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        securityACLService.checkCurrentUserIsUser();
        Description description = (Description)domain;
        securityACLService.check(description.container(),READ);

        if(description.getDomainClassName().equals(Project.class.getName())){
            securityACLService.checkIsNotReadOnly(description);
        } else {
            securityACLService.checkFullOrRestrictedForOwner(description.getDomainIdent(),description.getDomainClassName(), "user");
        }
        SecUser currentUser = currentUserService.getCurrentUser();
        return executeCommand(new EditCommand(currentUser, transaction), domain,jsonNewData);
    }

    @Override
    public CommandResponse delete(CytomineDomain domain, Transaction transaction, Task task, boolean printMessage) {
        securityACLService.checkCurrentUserIsUser();
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.check(domain.container(),READ);
        if (domain.userDomainCreator()!=null) {
            securityACLService.checkFullOrRestrictedForOwner(domain,domain.userDomainCreator());
        } else {
            securityACLService.checkIsNotReadOnly(domain);
        }
        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c,domain, null);
    }


    @Override
    public List<Object> getStringParamsI18n(CytomineDomain domain) {
        return List.of(((Description)domain).getDomainIdent(), ((Description)domain).getDomainClassName());
    }


    /**
     * Retrieve domain thanks to a JSON object
     * @return domain retrieve thanks to json
     */
    public CytomineDomain retrieve(JsonObject json) {

        CytomineDomain domain = null;
        try {
            domain = (CytomineDomain) getEntityManager().find(Class.forName(json.getJSONAttrStr("domainClassName")), json.getJSONAttrLong("domainIdent"));
            Description description = null;
            if (domain!=null) {
                description = findByDomain(domain).orElse(null);
            }
            if (description!=null) {
                return description;
            } else {
                throw new ObjectNotFoundException(
                        "Description not found for domain "+json.getJSONAttrStr("domainClassName") +" " + json.getJSONAttrLong("domainIdent"),
                        ErrorCode.NOT_FOUND_DESCRIPTION.getValue(),
                        Map.of("domainClass", json.getJSONAttrStr("domainClassName"), "id", json.getJSONAttrLong("domainIdent")));
            }
        } catch (ClassNotFoundException e) {
            throw new ObjectNotFoundException(
                    "Description not found for domain "+json.getJSONAttrStr("domainClassName") +" " + json.getJSONAttrLong("domainIdent"),
                    ErrorCode.NOT_FOUND_DESCRIPTION.getValue(),
                    Map.of("domainClass", json.getJSONAttrStr("domainClassName"), "id", json.getJSONAttrLong("domainIdent")));        }
    }


    @Override
    public void checkDoNotAlreadyExist(CytomineDomain domain) {
        Optional<Description> description = descriptionRepository.findByDomainIdentAndDomainClassName(((Description)domain).getDomainIdent(), ((Description)domain).getDomainClassName());
        if (description.isPresent() && !Objects.equals(description.get().getId(), domain.getId())) {
            throw new AlreadyExistException("Description already exists for " +((Description)domain).getDomainClassName() + " " + ((Description)domain).getDomainIdent());
        }
    }

}
