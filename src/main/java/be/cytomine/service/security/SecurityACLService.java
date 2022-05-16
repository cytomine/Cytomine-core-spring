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
import be.cytomine.domain.GenericCytomineDomainContainer;
import be.cytomine.domain.image.AbstractImage;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.server.Storage;
import be.cytomine.domain.ontology.Ontology;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.domain.security.UserJob;
import be.cytomine.exceptions.*;
import be.cytomine.repository.image.ImageInstanceRepository;
import be.cytomine.repository.ontology.OntologyRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.repository.security.AclRepository;
import be.cytomine.service.CurrentRoleService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.PermissionService;
import be.cytomine.service.ontology.GenericAnnotationService;
import be.cytomine.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.model.Permission;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.security.acls.domain.BasePermission.ADMINISTRATION;
import static org.springframework.security.acls.domain.BasePermission.READ;

@Slf4j
@Service
public class SecurityACLService {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private CurrentRoleService currentRoleService;

    @Autowired
    private OntologyRepository ontologyRepository;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private ImageInstanceRepository imageInstanceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AclRepository aclRepository;

    public void check(Long id, String className, Permission permission) {
        try {
            check(id, Class.forName(className),permission);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot check ACL for class " + className);
        }
    }

    public void check(Long id, Class className, Permission permission) {
        try {
            CytomineDomain domain = (CytomineDomain)entityManager.find(className, id);
            if (domain!=null) {
                check(domain,permission);
            } else {
                throw notFoundACLError(className.toString(), id);
            }
        } catch(IllegalArgumentException ex) {
            throw notFoundACLError(className.toString(), id);
        }

    }

    public void checkIsAdminContainer(CytomineDomain domain) {
        checkIsAdminContainer(domain, currentUserService.getCurrentUser());
    }

    public void checkIsAdminContainer(CytomineDomain domain, SecUser currentUser) {
        if (domain!=null) {
            if (!hasPermission(retrieveContainer(domain), ADMINISTRATION, currentRoleService.isAdminByNow(currentUser))) {
                throw new ForbiddenException("You don't have the right to do this. You must be the creator or the container admin");
            }
        } else {
            throw new ObjectNotFoundException("ACL error: domain is null! Unable to process project auth checking", ErrorCode.NOT_FOUND_ACL_ERROR_NULL_DOMAIN.getValue());
        }

    }


    public void check(CytomineDomain domain, Permission permission, SecUser currentUser) {
        if (domain!=null) {
            if (!hasPermission(retrieveContainer(domain), permission, currentRoleService.isAdminByNow(currentUser))) {
                throw new ForbiddenException("You don't have the right to read or modify this resource! "  + domain.getClass() + " " + domain.getId());
            }
        } else {
            throw new ObjectNotFoundException("ACL error: domain is null! Unable to process project auth checking", ErrorCode.NOT_FOUND_ACL_ERROR_NULL_DOMAIN.getValue());
        }
    }

    public void check(CytomineDomain domain, Permission permission) {
        check(domain, permission, currentUserService.getCurrentUser());

    }

    /**
     * Check if user has permission on the curret domain
     * @param permission Permission to check (READ,...)
     * @return true if user has this permission on current domain
     */
    public boolean hasPermission(CytomineDomain domain, Permission permission, boolean isAdmin) {
        boolean right = permissionService.hasACLPermission(domain, permission) || isAdmin;
        return right;
    }

    public boolean hasPermission(CytomineDomain domain, Permission permission) {
        boolean right = permissionService.hasACLPermission(domain, permission) || currentRoleService.isAdminByNow(currentUserService.getCurrentUser());
        return right;
    }

    public boolean hasRightToReadAbstractImageWithProject(AbstractImage image) {
        if(currentRoleService.isAdminByNow(currentUserService.getCurrentUser())) {
            return true;
        }
        List<ImageInstance> imageInstances = imageInstanceRepository.findAllByBaseImage(image);
        Set<Project> projects = imageInstances.stream().map(ImageInstance::getProject).collect(Collectors.toSet());
        for(Project project : projects) {
            if(permissionService.hasACLPermission(project,READ)) {
                return true;
            }
        }
        return false;
    }

    public List<Storage> getStorageList(SecUser user, boolean adminByPass) {
        return getStorageList(user, adminByPass, null);
    }

    public List<Storage> getStorageList(SecUser user, boolean adminByPass, String searchString) {
        Query query;
        if (adminByPass && currentRoleService.isAdminByNow(user)) {
            query = entityManager.createQuery("select storage from Storage as storage");
        } else {
            while (user instanceof UserJob) {
                user = ((UserJob) user).getUser();
            }
            query = entityManager.createQuery(
                    "select distinct storage "+
                            "from AclObjectIdentity as aclObjectId, AclEntry as aclEntry, AclSid as aclSid,  Storage as storage "+
                            "where aclObjectId.objectId = storage.id " +
                            "and aclEntry.aclObjectIdentity = aclObjectId "+
                            "and aclEntry.sid = aclSid and aclSid.sid like '"+user.getUsername() +"'" + (StringUtils.isNotBlank(searchString)? " and lower(storage.name) like '%" + searchString.toLowerCase() + "%'" : ""));

        }
        return (List<Storage>) query.getResultList();
    }

    public List<Project> getProjectList(SecUser user, Ontology ontology) {
        //faster method
        if (currentRoleService.isAdminByNow(user)) {
            return projectRepository.findAllByOntology(ontology);
        }
        else {
            Query query = entityManager.createQuery(
                    "select distinct project "+
                            "from AclObjectIdentity as aclObjectId, AclEntry as aclEntry, AclSid as aclSid,  Project as project "+
                            "where aclObjectId.objectId = project.id " +
                            "and aclEntry.aclObjectIdentity = aclObjectId "+
                            (ontology!=null? "and project.ontology.id = " + ontology.getId() : " ") +
                            "and aclEntry.sid = aclSid and aclSid.sid like '"+user.getUsername() +"'");
            return query.getResultList();
        }
    }


    public List<String> getProjectUsers(Project project) {
        // adminByPass TODO
        // userjob.user TODO

        Query query = entityManager.createQuery(
                "select distinct aclSid.sid "+
                        "from AclObjectIdentity as aclObjectId, AclEntry as aclEntry, AclSid as aclSid,  Project as project "+
                        "where aclObjectId.objectId = project.id " +
                        "and aclEntry.aclObjectIdentity = aclObjectId "+
                        "and aclEntry.sid = aclSid and project.id = " + project.getId());
        List<String> usernames = query.getResultList();
        return usernames;
    }


    public List<Ontology> getOntologyList(SecUser user) {
        if (currentRoleService.isAdminByNow(user)) return ontologyRepository.findAll();
        Query query = entityManager.createQuery(
                "select distinct ontology "+
                        "from AclObjectIdentity as aclObjectId, AclEntry as aclEntry, AclSid as aclSid,  Ontology as ontology "+
                        "where aclObjectId.objectId = ontology.id " +
                        "and aclEntry.aclObjectIdentity = aclObjectId "+
                        "and aclEntry.sid = aclSid and aclSid.sid like '"+user.getUsername() +"'");
        List<Ontology> ontologies = query.getResultList();
        return ontologies;
    }

    public void checkIsSameUser(SecUser user,SecUser currentUser) {
        boolean sameUser = (Objects.equals(user.getId(), currentUser.getId()));
        sameUser |= currentRoleService.isAdminByNow(currentUser);
        sameUser |= (currentUser instanceof UserJob && Objects.equals(user.getId(), ((UserJob) currentUser).getUser().getId()));
        if (!sameUser) {
            throw new ForbiddenException("You don't have the right to read this resource! You must be the same user!");
        }
    }

    public void checkCurrentUserIsAdmin() {
        checkAdmin(currentUserService.getCurrentUser());
    }


    public void checkAdmin(SecUser user) {
        if (!currentRoleService.isAdminByNow(user)) {
            throw new ForbiddenException("You don't have the right to perform this action! You must be admin!");
        }
    }

    public void checkCurrentUserIsUser() {
        checkUser(currentUserService.getCurrentUser());
    }

    public void checkUser(SecUser user) {
        if (!currentRoleService.isAdminByNow(user) && !currentRoleService.isUserByNow(user)) {
            throw new ForbiddenException("You don't have the right to perform this action! You must be user!");
        }
    }


    public void checkGuest() {
        checkGuest(currentUserService.getCurrentUser());
    }

    public void checkGuest(SecUser user) {
        // TODO: optimize this
        if (!currentRoleService.isAdminByNow(user) && !currentRoleService.isUserByNow(user) && !currentRoleService.isGuestByNow(user)) {
            throw new ForbiddenException("You don't have the right to perform this action! You must be guest!");
        }
    }

    public void checkIsNotReadOnly(Long id, Class className) {
        checkIsNotReadOnly(id,className.getName());
    }


    public void checkIsNotReadOnly(Long id, String className) {
        try {
            CytomineDomain domain = (CytomineDomain)entityManager.find(Class.forName(className), id);
            if (domain!=null) {
                checkIsNotReadOnly(domain);
            } else {
                throw notFoundACLError(className, id);
            }
        } catch(IllegalArgumentException | ClassNotFoundException ex) {
            throw notFoundACLError(className, id);
        }

    }


    //check if the container (e.g. Project) is not in readonly. If in readonly, only admins can edit this.
    public void checkIsNotReadOnly(CytomineDomain domain) {
        if (domain!=null) {
            CytomineDomain container = retrieveContainer(domain);
            log.debug("container " + container);
            boolean readOnly = !retrieveContainer(domain).canUpdateContent();
            if(readOnly && !permissionService.hasACLPermission(retrieveContainer(domain),ADMINISTRATION)) {
                throw new ForbiddenException("The project for this data is in readonly mode! You must be project manager to add, edit or delete this resource in a readonly project.");
            }
        } else {
            throw new ObjectNotFoundException("ACL error: domain is null! Unable to process project auth checking", ErrorCode.NOT_FOUND_ACL_ERROR_NULL_DOMAIN.getValue());
        }
    }

    public void checkIsSameUserOrAdminContainer(CytomineDomain domain,SecUser user,SecUser currentUser) {
        boolean isNotSameUser = (!currentRoleService.isAdminByNow(currentUser) && (!Objects.equals(user.getId(), currentUser.getId())));
        if (isNotSameUser) {
            if (domain!=null) {
                if (!hasPermission(retrieveContainer(domain), ADMINISTRATION,currentRoleService.isAdminByNow(currentUserService.getCurrentUser()))) {
                    throw new ForbiddenException("You don't have the right to do this. You must be the creator or the container admin");
                }
            } else {
                throw new ObjectNotFoundException("ACL error: domain is null! Unable to process project auth checking", ErrorCode.NOT_FOUND_ACL_ERROR_NULL_DOMAIN.getValue());
            }
        }

    }


    public void checkFullOrRestrictedForOwner(Long id, Class className, String owner) {
        checkFullOrRestrictedForOwner(id,className.getName(), owner);
    }


    public void checkFullOrRestrictedForOwner(Long id, String className, String owner) {
        try {
            CytomineDomain domain = (CytomineDomain)entityManager.find(Class.forName(className), id);
            if (domain!=null) {
                checkFullOrRestrictedForOwner(domain, (owner!=null && objectHasProperty(domain, owner) ? (SecUser) fieldValue(domain.getClass(), domain, owner) : null));
            } else {
                throw notFoundACLError(className, id);
            }
        } catch(IllegalArgumentException | ClassNotFoundException ex) {
            throw notFoundACLError(className, id);
        }
    }

    //check if the container (e.g. Project) has the minimal editing mode or is Admin. If not, exception will be thown
    public void checkFullOrRestrictedForOwner(CytomineDomain domain, SecUser owner) {
        if (domain!=null) {
            if(permissionService.hasACLPermission(retrieveContainer(domain),ADMINISTRATION)
                    || currentRoleService.isAdminByNow(currentUserService.getCurrentUser())) return;

            CytomineDomain container = retrieveContainer(domain);
            switch (((Project) retrieveContainer(domain)).getMode()) {
                case CLASSIC :
                    return;
                case RESTRICTED :
                    log.debug("Owner is " + (owner!=null? owner.getUsername() : "null"));
                    if(owner!=null) {
                        if (!Objects.equals(owner.getId(), currentUserService.getCurrentUser().getId())) {
                            throw new ForbiddenException("You don't have the right to do this. You must be the creator or the container admin");
                        }
                    } else {
                        throw new ForbiddenException("The project for this data is in "+((Project) retrieveContainer(domain)).getMode().name() +" mode! You must be project manager to add, edit or delete this resource.");
                    }
                    break;
                case READ_ONLY :
                    throw new ForbiddenException("The project for this data is in "+((Project) retrieveContainer(domain)).getMode().name()+" mode! You must be project manager to add, edit or delete this resource.");
                default :
                    throw new ObjectNotFoundException("ACL error: project editing mode is unknown! Unable to process project auth checking", ErrorCode.NOT_FOUND_ACL_ERROR_EDITING_MODE.getValue());

            }
        } else {
            throw new ObjectNotFoundException("ACL error: domain is null! Unable to process project auth checking", ErrorCode.NOT_FOUND_ACL_ERROR_NULL_DOMAIN.getValue());
        }
    }

    private Object fieldValue(Class<?> type, Object object, String propertyName){
        Field field = null;
        try {
            field = type.getDeclaredField(propertyName);
            field.setAccessible(true);
            return field.get(object);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ForbiddenException("Cannot extract owner from class " + type + " => " + e);
        }

    }


    private Boolean objectHasProperty(Object obj, String propertyName){
        List<Field> properties = getAllFields(obj);
        for(Field field : properties){
            if(field.getName().equalsIgnoreCase(propertyName)){
                return true;
            }
        }
        return false;
    }

    private static List<Field> getAllFields(Object obj){
        List<Field> fields = new ArrayList<Field>();
        getAllFieldsRecursive(fields, obj.getClass());
        return fields;
    }

    private static List<Field> getAllFieldsRecursive(List<Field> fields, Class<?> type) {
        for (Field field: type.getDeclaredFields()) {
            fields.add(field);
        }

        if (type.getSuperclass() != null) {
            fields = getAllFieldsRecursive(fields, type.getSuperclass());
        }

        return fields;
    }

    public void checkIsCreator(CytomineDomain domain, SecUser currentUser) {
        if (!currentRoleService.isAdminByNow(currentUser) && (!Objects.equals(currentUser.getId(), domain.userDomainCreator().getId()))) {
            throw new ForbiddenException("You don't have the right to read this resource! You must be the same user!");
        }
    }

    public boolean isUserInProject(User user, Project project) {
        this.check(project,READ);
        return (aclRepository.countEntries(project.getId(), user.getId()) > 0);
    }

    public void checkIsUserInProject(User user, Project project) {
        boolean result = isUserInProject(user, project);
        if(!result) {
            throw new ConstraintException("Error: the user "+user.getId()+" is not into the project "+project.getId());
        }
    }

    private CytomineDomain retrieveContainer(CytomineDomain domain) {
        if (domain.container() instanceof GenericCytomineDomainContainer) {
            try {
                Class className = Class.forName(((GenericCytomineDomainContainer) domain.container()).getContainerClass());
                CytomineDomain parent = ((CytomineDomain)entityManager.find(className, domain.container().getId()));
                if (parent==null) {
                    throw new ForbiddenException("Parent " + className + " " + domain.container().getId() + " cannot be found in database, cannot check authorization");
                }
                return parent.container();
            } catch (ClassNotFoundException e) {
                throw new WrongArgumentException("Cannot load " + domain);
            }
        }
        return domain.container();
    }

    private ObjectNotFoundException notFoundACLError(String className, Long id){
        return new ObjectNotFoundException(
                "ACL error: " + className + " with id "+ id + " was not found! Unable to process auth checking",
                ErrorCode.NOT_FOUND_ACL_ERROR.getValue(),
                Map.of("className", className, "id", id));
    }
}
