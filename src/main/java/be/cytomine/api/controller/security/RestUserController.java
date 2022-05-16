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
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.server.Storage;
import be.cytomine.domain.ontology.Ontology;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.project.ProjectRepresentativeUser;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.dto.AuthInformation;
import be.cytomine.exceptions.AuthenticationException;
import be.cytomine.exceptions.ForbiddenException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.image.server.StorageService;
import be.cytomine.service.ontology.OntologyService;
import be.cytomine.service.project.ProjectRepresentativeUserService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.report.ReportService;
import be.cytomine.service.search.UserSearchExtension;
import be.cytomine.service.security.SecUserService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.SecurityUtils;
import be.cytomine.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.security.acls.domain.BasePermission.ADMINISTRATION;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestUserController extends RestCytomineController {

    private final SecUserService secUserService;

    private final ProjectService projectService;

    private final CurrentUserService currentUserService;

    private final ImageInstanceService imageInstanceService;

    private final SecurityACLService securityACLService;

    private final ProjectRepresentativeUserService projectRepresentativeUserService;

    private final OntologyService ontologyService;

    private final StorageService storageService;

    private final ApplicationContext applicationContext;

    private final ReportService reportService;


    @GetMapping("/project/{id}/admin.json")
    public ResponseEntity<String> showAdminByProject(
            @PathVariable Long id
    ) {
        log.debug("REST request to list admins from project {}", id);
        Project project = projectService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , id));
        return responseSuccess(secUserService.listAdmins(project), isFilterRequired());
    }

    @GetMapping("/project/{id}/users/representative.json")
    public ResponseEntity<String> showRepresentativeByProject(
            @PathVariable Long id
    ) {
        log.debug("REST request to list representatives from project {}", id);
        Project project = projectService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , id));
        return responseSuccess(projectRepresentativeUserService.listByProject(project).stream().map(ProjectRepresentativeUser::getUser)
                .collect(Collectors.toList()), isFilterRequired());
    }

    @GetMapping("/project/{id}/creator.json")
    public ResponseEntity<String> showCreatorByProject(
            @PathVariable Long id
    ) {
        log.debug("REST request to list creator from project {}", id);
        Project project = projectService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , id));
        return responseSuccess(List.of(secUserService.findCreator(project).orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , "CREATOR"))));
    }


    @GetMapping("/ontology/{id}/user.json")
    public ResponseEntity<String> showUserByOntology(
            @PathVariable Long id
    ) {
        log.debug("REST request to list user from ontology {}", id);
        Ontology ontology = ontologyService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Ontology", id));
        return responseSuccess(secUserService.listUsers(ontology), isFilterRequired());
    }

    @GetMapping("/project/{id}/userlayer.json")
    public ResponseEntity<String> showLayerByProject(
            @PathVariable Long id,
            @RequestParam(value = "image", required = false, defaultValue = "0") Long idImage
    ) {
        log.debug("REST request to list user layers from project {}", id);
        Project project = projectService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , id));
        ImageInstance image  = imageInstanceService.find(idImage).orElse(null);

        return responseSuccess(secUserService.listLayers(project, image), isFilterRequired());

    }

    @GetMapping("/storage/{id}/user.json")
    public ResponseEntity<String> showUserByStorage(
            @PathVariable Long id
    ) {
        log.debug("REST request to list user from storage {}", id);
        Storage storage = storageService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Storage", id));
        return responseSuccess(secUserService.listUsers(storage), isFilterRequired());
    }


    @GetMapping("/user.json")
    public ResponseEntity<String> list(
            @RequestParam(value = "publicKey", required = false) String publicKey,
            @RequestParam(value = "withRoles", defaultValue = "false", required = false) Boolean withRoles,
            @RequestParam(value = "withLastConsultation", defaultValue = "false", required = false) Boolean withLastConsultation,
            @RequestParam(value = "withNumberConsultations", defaultValue = "false", required = false) Boolean withNumberConsultations,
            @RequestParam(value = "sort", defaultValue = "created", required = false) String sortColumn,
            @RequestParam(value = "order", defaultValue = "desc", required = false) String sortDirection,
            @RequestParam(value = "offset", defaultValue = "0", required = false) Long offset,
            @RequestParam(value = "max", defaultValue = "0", required = false) Long max
    ) {
        log.debug("REST request to list user");
        if (publicKey != null) {
            return responseSuccess(secUserService.findByPublicKey(publicKey)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", JsonObject.of("publicKey", publicKey).toJsonString())));
        }

        UserSearchExtension userSearchExtension = new UserSearchExtension();
        userSearchExtension.setWithRoles(withRoles);
        return responseSuccess(
                secUserService.list(userSearchExtension, retrieveSearchParameters(), sortColumn, sortDirection, max, offset)
                , isFilterRequired()
        );
    }

    @GetMapping("/user/{id}.json")
    public ResponseEntity<String> getUser(
            @PathVariable String id
    ) {
        log.debug("REST request to get User : {}", id);

        Optional<SecUser> user = secUserService.find(id);
        if (user.isEmpty()) {
            user = secUserService.findByUsername(id);
        }

        return user.map( secUser -> {
            JsonObject object = User.getDataFromDomain(secUser);
            AuthInformation authMaps = secUserService.getAuthenticationRoles(secUser);
            object.put("admin", authMaps.getAdmin());
            object.put("user", authMaps.getUser());
            object.put("guest", authMaps.getGuest());
            return responseSuccess(object, isFilterRequired());
        }).orElseGet(() -> responseNotFound("User", id));
    }

    @GetMapping("/userkey/{publicKey}/keys.json")
    public ResponseEntity<String> keys(@PathVariable String publicKey) {
        SecUser user = secUserService.findByPublicKey(publicKey)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", Map.of("publicKey", publicKey).toString()));
        securityACLService.checkIsSameUser(user, currentUserService.getCurrentUser());
        return responseSuccess(JsonObject.of("privateKey", user.getPrivateKey(), "publicKey", user.getPublicKey()));
    }

    @GetMapping("/user/{id}/keys.json")
    public ResponseEntity<String> keysById(@PathVariable String id) {
        SecUser user = secUserService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", Map.of("id or username", id).toString()));
        securityACLService.checkIsSameUser(user, currentUserService.getCurrentUser());
        return responseSuccess(JsonObject.of("privateKey", user.getPrivateKey(), "publicKey", user.getPublicKey()));
    }

    @GetMapping("/signature.json")
    public ResponseEntity<String> signature(
            @RequestParam(defaultValue = "GET") String method,
            @RequestParam(value = "content-MD5", required = false, defaultValue = "") String contentMD5,
            @RequestParam(value = "content-type", required = false, defaultValue = "") String contenttype,
            @RequestParam(value = "content-Type", required = false, defaultValue = "") String contentType,
            @RequestParam(value = "date", required = false, defaultValue = "") String date,
            @RequestParam(value = "queryString", required = false, defaultValue = "") String queryString,
            @RequestParam(value = "forwardURI", required = false, defaultValue = "") String forwardURI
    ) throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        SecUser user = currentUserService.getCurrentUser();
        if (!queryString.isEmpty()) {
            queryString = "?" + queryString;
        }
        String signature = SecurityUtils.generateKeys(method,contentMD5,contenttype.isEmpty()?contentType:contenttype,date,queryString,forwardURI,user);

        return responseSuccess(JsonObject.of("signature", signature, "publicKey", user.getPublicKey()));
    }



    @GetMapping("/user/current.json")
    public ResponseEntity<String> getCurrentUser(
    ) {
        log.debug("REST request to get current User");

        SecUser secUser = secUserService.getCurrentUser();

        JsonObject object = User.getDataFromDomain(secUser);
        if(!secUser.isAlgo()){
            AuthInformation authMaps = secUserService.getAuthenticationRoles(secUser);
            object.put("admin", authMaps.getAdmin());
            object.put("user", authMaps.getUser());
            object.put("guest", authMaps.getGuest());
            object.put("adminByNow", authMaps.getAdminByNow());
            object.put("userByNow", authMaps.getUserByNow());
            object.put("guestByNow", authMaps.getGuestByNow());
            object.put("isSwitched", SecurityUtils.isSwitched(applicationContext));
            if(object.getJSONAttrBoolean("isSwitched", false)) {
                object.put("realUser", SecurityUtils.getSwitchedUserOriginalUsername(applicationContext));
            }
        }
        return responseSuccess(object);
    }


    @PostMapping("/user.json")
    public ResponseEntity<String> createUser(@RequestBody String json) {
        log.debug("REST request to save User : " + json);
        return add(secUserService, json);
    }

    @PutMapping("/user/{id}.json")
    public ResponseEntity<String> updateUser(@PathVariable String id, @RequestBody JsonObject json) {
        log.debug("REST request to update User : {}", id);
        return update(secUserService, json);
    }

    @DeleteMapping("/user/{id}.json")
    public ResponseEntity<String> deleteUser(@PathVariable String id) {
        log.debug("REST request to delete User: {}", id);
        return delete(secUserService, JsonObject.of("id", Long.parseLong(id)), null);
    }


    @PostMapping("/user/{id}/lock.json")
    public ResponseEntity<String> lock(@PathVariable Long id) {
        log.debug("REST request to lock User : {}", id);
        SecUser user = secUserService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", id));
        return responseSuccess(secUserService.lock(user));
    }

    @DeleteMapping("/user/{id}/lock.json")
    public ResponseEntity<String> unlock(@PathVariable Long id) {
        log.debug("REST request to lock User : {}", id);
        SecUser user = secUserService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", id));
        return responseSuccess(secUserService.unlock(user));
    }



    @GetMapping("/project/{id}/user.json")
    public ResponseEntity<String> showByProject(
            @PathVariable Long id,
            @RequestParam(value = "withUserJob", defaultValue = "false", required = false) Boolean withUserJob,
            @RequestParam(value = "withLastImage", defaultValue = "false", required = false) Boolean withLastImage,
            @RequestParam(value = "withLastConsultation", defaultValue = "false", required = false) Boolean withLastConsultation,
            @RequestParam(value = "withNumberConsultations", defaultValue = "false", required = false) Boolean withNumberConsultations,
            @RequestParam(value = "sortColumn", defaultValue = "created", required = false) String sortColumn,
            @RequestParam(value = "sortDirection", defaultValue = "desc", required = false) String sortDirection,
            @RequestParam(value = "offset", defaultValue = "0", required = false) Long offset,
            @RequestParam(value = "max", defaultValue = "0", required = false) Long max
    ) {
        log.debug("REST request to list user from project {}", id);
        Project project = projectService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , id));

        UserSearchExtension userSearchExtension = new UserSearchExtension();
        userSearchExtension.setWithLastImage(withLastImage);
        userSearchExtension.setWithLastConnection(withLastConsultation);
        userSearchExtension.setWithNumberConnections(withNumberConsultations);
        userSearchExtension.setWithUserJob(withUserJob);

        return responseSuccess(
                secUserService.listUsersExtendedByProject(project, userSearchExtension, retrieveSearchParameters(), sortColumn, sortDirection, max, offset)
                , isFilterRequired()
        );

        //return responseSuccess(projectService.list(user, projectSearchExtension, new ArrayList<>(), "created", "desc", 0L, 0L), allParams);
    }


    @PostMapping("/project/{project}/user/{user}.json")
    public ResponseEntity<String> addUserToProject(@PathVariable("project") Long projectId, @PathVariable("user") Long userId) {
        log.debug("REST request to add User {} to project {}", userId, projectId);
        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        secUserService.addUserToProject(user, project, false);
        return responseSuccess(JsonObject.of("data", JsonObject.of("message", "OK")).toJsonString());
    }

    @PostMapping("/project/{project}/user.json")
    public ResponseEntity<String> addUsersToProject(@PathVariable("project") Long projectId, @RequestParam("users") String users) {
        log.debug("REST request to add Users {} to project {}", users, projectId);
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));

        List<String> usersIds = Arrays.stream(users.split(",")).collect(Collectors.toList());

        String errorMessage = "";
        List<String> errors = new ArrayList<>();
        List<String> wrongIds = new ArrayList<>();
        List<Long> usersValidIds = new ArrayList<>();
        for (String userId : usersIds) {
            try{
                usersValidIds.add(Long.parseLong(userId));
            } catch(NumberFormatException e){
                wrongIds.add(userId);
            }
        }

        List<SecUser> secUsers = secUserService.list(usersValidIds);

        wrongIds.addAll(usersIds);
        wrongIds.removeAll(secUsers.stream().map(x -> String.valueOf(x.getId())).collect(Collectors.toList()));

        for (SecUser user : secUsers) {
            try {
                secUserService.addUserToProject(user, project, false);
            } catch(Exception e) {
                errors.add(user.getId().toString());
            }
        }
        if(!errors.isEmpty()) {
            errorMessage += "Cannot add theses users to the project ${project.id} : "+ String.join(",", errors)+". ";
        }
        if(!wrongIds.isEmpty()) {
            errorMessage += String.join(",", wrongIds) +" are not well formatted ids";
        }

        JsonObject response = new JsonObject();
        response.put("data", JsonObject.of("message", "OK"));
        response.put("status", 200);

        if (!errors.isEmpty()  || !wrongIds.isEmpty()) {
            response.put("data", JsonObject.of("message", errorMessage));
            return ResponseEntity.status(206).body(response.toJsonString());
        } else {
            response.put("data", JsonObject.of("message", "OK"));
            response.put("status", 200);
            return ResponseEntity.status(200).body(response.toJsonString());
        }
    }

    @DeleteMapping("/project/{project}/user/{user}.json")
    public ResponseEntity<String> deleteUserFromProject(@PathVariable("project") Long projectId, @PathVariable("user") Long userId) {
        log.debug("REST request to remove User {} from project {}", userId, projectId);
        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        secUserService.deleteUserFromProject(user, project, false);
        return responseSuccess(JsonObject.of("data", JsonObject.of("message", "OK")).toJsonString());
    }

    @DeleteMapping("/project/{project}/user.json")
    public ResponseEntity<String> deleteUsersFromProject(@PathVariable("project") Long projectId, @RequestParam("users") String users) {
        log.debug("REST request to add Users {} to project {}", users, projectId);
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));

        List<String> usersIds = Arrays.stream(users.split(",")).collect(Collectors.toList());

        String errorMessage = "";
        List<String> errors = new ArrayList<>();
        List<String> wrongIds = new ArrayList<>();
        List<Long> usersValidIds = new ArrayList<>();
        for (String userId : usersIds) {
            try{
                usersValidIds.add(Long.parseLong(userId));
            } catch(NumberFormatException e){
                wrongIds.add(userId);
            }
        }

        List<SecUser> secUsers = secUserService.list(usersValidIds);

        wrongIds.addAll(usersIds);
        wrongIds.removeAll(secUsers.stream().map(x -> String.valueOf(x.getId())).collect(Collectors.toList()));

        for (SecUser user : secUsers) {
            try {
                secUserService.deleteUserFromProject(user, project, false);
            } catch(Exception e) {
                errors.add(user.getId().toString());
            }
        }
        if(!errors.isEmpty()) {
            errorMessage += "Cannot add theses users to the project ${project.id} : "+ String.join(",", errors)+". ";
        }
        if(!wrongIds.isEmpty()) {
            errorMessage += String.join(",", wrongIds) +" are not well formatted ids";
        }

        JsonObject response = new JsonObject();
        response.put("data", JsonObject.of("message", "OK"));
        response.put("status", 200);

        if (!errors.isEmpty()  || !wrongIds.isEmpty()) {
            response.put("data", JsonObject.of("message", errorMessage));
            return ResponseEntity.status(206).body(response.toJsonString());
        } else {
            response.put("data", JsonObject.of("message", "OK"));
            response.put("status", 200);
            return ResponseEntity.status(200).body(response.toJsonString());
        }
    }

    @PostMapping("/project/{project}/user/{user}/admin.json")
    public ResponseEntity<String> addUserAdminToProject(@PathVariable("project") Long projectId, @PathVariable("user") Long userId) {
        log.debug("REST request to add User {} to project {}", userId, projectId);
        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        secUserService.addUserToProject(user, project, true);
        return responseSuccess(JsonObject.of("data", JsonObject.of("message", "OK")).toJsonString());
    }

    @DeleteMapping("/project/{project}/user/{user}/admin.json")
    public ResponseEntity<String> deleteUserAdminFromProject(@PathVariable("project") Long projectId, @PathVariable("user") Long userId) {
        log.debug("REST request to remove User {} from project {}", userId, projectId);
        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        if (!Objects.equals(currentUserService.getCurrentUser().getId(), user.getId())) {
            securityACLService.check(project,ADMINISTRATION);
        }
        secUserService.deleteUserFromProject(user, project, true);
        return responseSuccess(JsonObject.of("data", JsonObject.of("message", "OK")).toJsonString());
    }

    @PostMapping("/storage/{storage}/user/{user}.json")
    public ResponseEntity<String> addUserToStorage(@PathVariable("storage") Long storageId, @PathVariable("user") Long userId) {
        log.debug("REST request to add User {} to storage {}", userId, storageId);
        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        Storage storage = storageService.find(storageId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Storage", storageId));
        secUserService.addUserToStorage(user, storage);
        return responseSuccess(JsonObject.of("data", JsonObject.of("message", "OK")).toJsonString());
    }

    @DeleteMapping("/storage/{storage}/user/{user}.json")
    public ResponseEntity<String> deleteUserFromStorage(@PathVariable("storage") Long storageId, @PathVariable("user") Long userId) {
        log.debug("REST request to remove User {} from storage {}", userId, storageId);
        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        Storage storage = storageService.find(storageId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Storage", storageId));
        secUserService.deleteUserFromStorage(user, storage);
        return responseSuccess(JsonObject.of("data", JsonObject.of("message", "OK")).toJsonString());
    }


    @PutMapping("/user/{user}/password.json")
    public ResponseEntity<String> resetPassword(@PathVariable("user") Long userId,
                                                @RequestBody JsonObject json) {
        log.debug("REST request to resetpassword for User {}", userId);
        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        String newPassword = json.getJSONAttrStr("password");
        if (StringUtils.isNotBlank(newPassword)) {
            secUserService.changeUserPassword((User) user,newPassword);
            return responseSuccess(user, isFilterRequired());
        } else {
            throw new WrongArgumentException("Password is missing in JSON");
        }

    }

    @RequestMapping(path = {"/user/security_check.json"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> checkPassword(@RequestBody JsonObject json) {
        log.debug("REST request to check password for current user");
        User user = (User)secUserService.getCurrentUser();
        String newPassword = json.getJSONAttrStr("password");
        if(secUserService.isUserPassword(user, newPassword)) {
            return responseSuccess(JsonObject.of(), isFilterRequired());
        } else {
            throw new AuthenticationException("No matching password");
        }
    }

    @GetMapping("/user/{id}/friends.json")
    public ResponseEntity<String> listFriends(
            @PathVariable Long id,
            @RequestParam(value = "project", required = false) Long projectId,
            @RequestParam(value = "offline", required = false, defaultValue = "false") Boolean offlineToo
    ) {
        log.debug("REST request to list user layers from project {}", id);

        User user = secUserService.findUser(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", id));

        List<SecUser> friends = new ArrayList<>();

        if (offlineToo) {
            if (projectId!=null) {
                //get all user project list
                Project project = projectService.find(projectId)
                        .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
                friends = secUserService.listUsers(project);
            } else {
                //get all people that share common project with user
                friends = secUserService.getAllFriendsUsers(user);
            }
        } else {
            if (projectId!=null) {
                //get user project online
                Project project = projectService.find(projectId)
                        .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
                friends = secUserService.getAllFriendsUsersOnline(user, project);
            } else {
                //get friends online
                friends = secUserService.getAllFriendsUsersOnline(user);
            }
        }
        return responseSuccess(friends, isFilterRequired());
    }


    @GetMapping("/project/{project}/online/user.json")
    public ResponseEntity<String> listOnlineFriendsWithPosition(
            @PathVariable(value = "project") Long projectId
    ) {
        log.debug("REST request to list online user for project {}", projectId);

        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        return responseSuccess(secUserService.getAllOnlineUserWithTheirPositions(project), isFilterRequired());
    }

    @GetMapping("/project/{project}/usersActivity.json")
    public ResponseEntity<String> usersActivity(
            @PathVariable(value = "project") Long projectId
    ) {
        log.debug("REST request to list online user for project {}", projectId);

        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        return responseSuccess(secUserService.getUsersWithLastActivities(project), isFilterRequired());
    }


    @GetMapping("/project/{project}/user/download")
    public void download(
            @PathVariable(value = "project") Long projectId,
            @RequestParam String format
    ) throws IOException {
        log.debug("REST request to download user listing from a specific project : {}", projectId);

        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));

        List<SecUser> projectUsers = secUserService.listUsers(project);
        List<Map<String, Object>> users = new ArrayList<>();

        for (SecUser user : projectUsers) {
            if (user instanceof User) {
                users.add(Map.of(
                        "username", user.getUsername(),
                        "firstname", (((User) user).getFirstname()),
                        "lastname", (((User) user).getLastname())
                ));
            }

            byte[] report = reportService.generateUsersReport(project.getName(), users, format);
            responseReportFile(reportService.getUsersReportFileName(format, projectId), report, format);
        }
    }

    @GetMapping("/project/{project}/resumeActivity/{user}.json")
    public ResponseEntity<String> resumeActivity(
            @PathVariable(value = "project") Long projectId,
            @PathVariable(value = "user") Long userId
    ) {
        log.debug("REST request to list activities for user {} and for project {}", userId, projectId);

        User user = secUserService.findUser(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));

        return responseSuccess(secUserService.getResumeActivities(project, user), isFilterRequired());
    }


    boolean isFilterRequired() {
        try{
            securityACLService.checkAdmin(currentUserService.getCurrentUser());
            return false;
        } catch(ForbiddenException e){}
        return true;
    }

    @Override
    protected void filterOneElement(Map<String, Object> element) {
        if (element.get("id")!=null && !element.get("id").equals(currentUserService.getCurrentUser().getId())) {
            element.remove("email");
        }
    }
}
