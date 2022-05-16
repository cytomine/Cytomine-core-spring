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
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.server.Storage;
import be.cytomine.domain.ontology.*;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.project.ProjectDefaultLayer;
import be.cytomine.domain.project.ProjectRepresentativeUser;
import be.cytomine.domain.security.*;
import be.cytomine.domain.social.LastConnection;
import be.cytomine.domain.social.PersistentProjectConnection;
import be.cytomine.dto.AuthInformation;
import be.cytomine.dto.NamedCytomineDomain;
import be.cytomine.exceptions.*;
import be.cytomine.repository.command.CommandHistoryRepository;
import be.cytomine.repository.command.CommandRepository;
import be.cytomine.repository.command.RedoStackItemRepository;
import be.cytomine.repository.command.UndoStackItemRepository;
import be.cytomine.repository.image.ImageInstanceRepository;
import be.cytomine.repository.image.NestedImageInstanceRepository;
import be.cytomine.repository.image.UploadedFileRepository;
import be.cytomine.repository.image.server.StorageRepository;
import be.cytomine.repository.ontology.*;
import be.cytomine.repository.project.ProjectDefaultLayerRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.repository.project.ProjectRepresentativeUserRepository;
import be.cytomine.repository.security.*;
import be.cytomine.repositorynosql.social.AnnotationActionRepository;
import be.cytomine.repositorynosql.social.LastConnectionRepository;
import be.cytomine.repositorynosql.social.PersistentImageConsultationRepository;
import be.cytomine.repositorynosql.social.PersistentProjectConnectionRepository;
import be.cytomine.service.CurrentRoleService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.PermissionService;
import be.cytomine.service.dto.JobLayerDTO;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.image.server.StorageService;
import be.cytomine.service.ontology.*;
import be.cytomine.service.project.ProjectDefaultLayerService;
import be.cytomine.service.project.ProjectRepresentativeUserService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.search.UserSearchExtension;
import be.cytomine.service.social.ImageConsultationService;
import be.cytomine.service.social.ProjectConnectionService;
import be.cytomine.service.social.UserPositionService;
import be.cytomine.utils.*;
import be.cytomine.utils.filters.SearchParameterEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

import javax.persistence.Query;
import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.security.acls.domain.BasePermission.*;

@Slf4j
@Service
@Transactional
public class SecUserService extends ModelService {

    @Autowired
    private UserPositionService userPositionService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    private SecUserRepository secUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityACLService securityACLService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private CurrentRoleService currentRoleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ImageConsultationService imageConsultationService;

    @Autowired
    private ProjectConnectionService projectConnectionService;

    @Autowired
    private ProjectRepresentativeUserService projectRepresentativeUserService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LastConnectionRepository lastConnectionRepository;

    @Autowired
    private CommandHistoryRepository commandHistoryRepository;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private RedoStackItemRepository redoStackItemRepository;

    @Autowired
    private UndoStackItemRepository undoStackItemRepository;

    @Autowired
    private SecUserSecRoleService secUserSecRoleService;

    @Autowired
    private AlgoAnnotationService algoAnnotationService;

    @Autowired
    private AlgoAnnotationRepository algoAnnotationRepository;

    @Autowired
    private UserAnnotationService userAnnotationService;

    @Autowired
    private UserAnnotationRepository userAnnotationRepository;

    @Autowired
    private ReviewedAnnotationService reviewedAnnotationService;

    @Autowired
    private ReviewedAnnotationRepository reviewedAnnotationRepository;

    @Autowired
    private AlgoAnnotationTermService algoAnnotationTermService;

    @Autowired
    private AlgoAnnotationTermRepository algoAnnotationTermRepository;

    @Autowired
    private AnnotationTermService annotationTermService;

    @Autowired
    private AnnotationTermRepository annotationTermRepository;

    @Autowired
    private ImageInstanceRepository imageInstanceRepository;

    @Autowired
    private ImageInstanceService imageInstanceService;

    @Autowired
    private OntologyRepository ontologyRepository;

    @Autowired
    private OntologyService ontologyService;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private AnnotationIndexRepository annotationIndexRepository;

    @Autowired
    private NestedImageInstanceRepository nestedImageInstanceRepository;

    @Autowired
    private ProjectDefaultLayerService projectDefaultLayerService;

    @Autowired
    private ProjectDefaultLayerRepository projectDefaultLayerRepository;

    @Autowired
    private ProjectRepresentativeUserRepository projectRepresentativeUserRepository;

    @Autowired
    private SecUserSecRoleRepository secUserSecRoleRepository;

    @Autowired
    private SecRoleRepository secRoleRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private AclRepository aclRepository;

    @Autowired
    private PersistentProjectConnectionRepository persistentProjectConnectionRepository;

    @Autowired
    private PersistentImageConsultationRepository persistentImageConsultationRepository;

    @Autowired
    private AnnotationActionRepository annotationActionRepository;

    @Autowired
    private StorageRepository storageRepository;

    public Optional<SecUser> find(Long id) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        return secUserRepository.findById(id);
    }

    public Optional<SecUser> find(String id) {
        try {
            return find(Long.valueOf(id));
        } catch (NumberFormatException ex) {
            return findByUsername(id);
        }
    }

    public Optional<User> findUser(Long id) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        return userRepository.findById(id);
    }

    public boolean isUserJob(Long id) {
        return find(id).map(SecUser::isAlgo).orElse(false);
    }

    public SecUser get(Long id) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        return find(id).orElse(null);
    }

    public Optional<SecUser> findByUsername(String username) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        return secUserRepository.findByUsernameLikeIgnoreCase(username);
    }

    public Optional<User> findByEmail(String email) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        return userRepository.findByEmailLikeIgnoreCase(email);
    }

    public Optional<SecUser> findByPublicKey(String publicKey) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        return secUserRepository.findByPublicKey(publicKey);
    }

    public AuthInformation getAuthenticationRoles(SecUser user) {
        AuthInformation authInformation = new AuthInformation();
        authInformation.setAdmin(currentRoleService.isAdmin(user));
        authInformation.setUser(!authInformation.getAdmin() && currentRoleService.isUser(user));
        authInformation.setGuest(!authInformation.getAdmin() && !authInformation.getUser() && currentRoleService.isGuest(user));

        authInformation.setAdminByNow(currentRoleService.isAdminByNow(user));
        authInformation.setUserByNow(!authInformation.getAdminByNow() && currentRoleService.isUserByNow(user));
        authInformation.setGuestByNow(!authInformation.getAdminByNow() && !authInformation.getUserByNow() && currentRoleService.isGuestByNow(user));

        return authInformation;
    }

    public SecUser getCurrentUser() {
        return currentUserService.getCurrentUser();
    }

    public Page<Map<String, Object>> list(UserSearchExtension userSearchExtension, List<SearchParameterEntry> searchParameters, String sortColumn, String sortDirection, Long max, Long offset) {

        securityACLService.checkGuest(currentUserService.getCurrentUser());

        if (sortColumn == null) {
            sortColumn = "username";
        }
        if (sortDirection == null) {
            sortDirection = "asc";
        }
        if (sortColumn.equals("role") && !userSearchExtension.isWithRoles()) {
            throw new WrongArgumentException("Cannot sort on user role without argument withRoles");
        }

        if (ReflectionUtils.findField(User.class, sortColumn) == null && !(List.of("role", "fullName").contains(sortColumn))) {
            throw new CytomineMethodNotYetImplementedException("User list sorted by " + sortColumn + "is not implemented");
        }

        Optional<SearchParameterEntry> multiSearch = searchParameters.stream().filter(x -> x.getProperty().equals("fullName")).findFirst();

        String select = "SELECT u.* ";
        String from = "FROM sec_user u ";
        String where = "WHERE job_id IS NULL AND u.class LIKE 'be.cytomine.domain.security.User' " ; //TODO: change for migration
        String search = "";
        String groupBy = "";
        String sort;

        Map<String, Object> mapParams = new HashMap<>();

        if (multiSearch.isPresent()) {
            String value = ((String) multiSearch.get().getValue()).toLowerCase();
            value = "%"+value+"%";
            where += " and (u.firstname ILIKE :name OR u.lastname ILIKE :name OR u.email ILIKE :name OR u.username ILIKE :name) ";
            mapParams.put("name", value);
        }
        if (userSearchExtension.isWithRoles()) {
            select += ", MAX(x.order_number) as role ";
            from += ", sec_user_sec_role sur, sec_role r " +
                    "JOIN (VALUES ('ROLE_GUEST', 1), ('ROLE_USER' ,2), ('ROLE_ADMIN', 3), ('ROLE_SUPER_ADMIN', 4)) as x(value, order_number) ON r.authority = x.value ";
            where += "and u.id = sur.sec_user_id and sur.sec_role_id = r.id ";
            groupBy = "GROUP BY u.id ";
        }

        if (sortColumn.equals("role")) {
            sort = "ORDER BY " + sortColumn + " " + sortDirection + ", u.id ASC ";
        } else if (sortColumn.equals("fullName")) {
            sort = "ORDER BY u.firstname " + sortDirection + ", u.id ";
        } else if (!sortColumn.equals("id")) { //avoid random sort when multiple values of the
            sort = "ORDER BY u." + sortColumn + " " + sortDirection + ", u.id ";
        } else sort = "ORDER BY u." + sortColumn + " " + sortDirection + " ";

        String request = select + from + where + search + groupBy + sort;

        if (max > 0) {
            request += " LIMIT " + max;
        }
        if (offset > 0) {
            request += " OFFSET " + offset;
        }

        Query query = getEntityManager().createNativeQuery(request, Tuple.class);
//        Map<String, Object> mapParams = sqlSearchConditions.getSqlParameters();
        for (Map.Entry<String, Object> entry : mapParams.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        List<Tuple> resultList = query.getResultList();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Tuple rowResult : resultList) {
            JsonObject result = new JsonObject();
            for (TupleElement<?> element : rowResult.getElements()) {
                Object value = rowResult.get(element.getAlias());
                if (value instanceof BigInteger) {
                    value = ((BigInteger) value).longValue();
                }
                String alias = SQLUtils.toCamelCase(element.getAlias());
                result.put(alias, value);
            }

            // TODO?????
            // I mock methods and fields to pass through getDataFromDomain of SecUser
//            map["class"] = User.class
//            map.getMetaClass().algo = { return false }
//            map["language"] = User.Language.valueOf(map["language"])

            result.put("language", Language.valueOf(result.getJSONAttrStr("language")));
            JsonObject object = User.getDataFromDomain(new User().buildDomainFromJson(result, getEntityManager()));
            object.put("algo", false);

            if (userSearchExtension.isWithRoles()) {
                String role = "UNDEFINED";
                switch ((Integer) result.get("role")) {
                    case 1:
                        role = "ROLE_GUEST";
                        break;
                    case 2:
                        role = "ROLE_USER";
                        break;
                    case 3:
                        role = "ROLE_ADMIN";
                        break;
                    case 4:
                        role = "ROLE_SUPER_ADMIN";
                        break;
                }
                object.put("role", role);
            }
            results.add(object);
        }
        request = "SELECT COUNT(DISTINCT U.id) " + from + where + search;
        query = getEntityManager().createNativeQuery(request);
        for (Map.Entry<String, Object> entry : mapParams.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        long count = ((BigInteger) query.getResultList().get(0)).longValue();

        Page<Map<String, Object>> page = PageUtils.buildPageFromPageResults(results, max, offset, count);
        return page;
    }

    public Page<JsonObject> listUsersExtendedByProject(Project project, UserSearchExtension userSearchExtension, List<SearchParameterEntry> searchParameters, String sortColumn, String sortDirection, Long max, Long offset) {

        if (ReflectionUtils.findField(User.class, sortColumn) == null && !(List.of("projectRole", "fullName", "lastImageName", "lastConnection", "frequency").contains(sortColumn))) {
            throw new CytomineMethodNotYetImplementedException("User list sorted by " + sortColumn + "is not implemented");
        }
        if (sortColumn.equals("lastImageName") && !userSearchExtension.isWithLastImage()) {
            throw new WrongArgumentException("Cannot sort on lastImageName without argument withLastImage");
        }
        if (sortColumn.equals("lastConnection") && !userSearchExtension.isWithLastConnection()) {
            throw new WrongArgumentException("Cannot sort on lastConnection without argument withLastConnection");
        }
        if (sortColumn.equals("frequency") && !userSearchExtension.isWithNumberConnections()) {
            throw new WrongArgumentException("Cannot sort on frequency without argument withNumberConnections");
        }

        if (userSearchExtension == null || userSearchExtension.noExtension()) {
            return listUsersByProject(project, searchParameters, sortColumn, sortDirection, max, offset);
        } else {
            List<JsonObject> results = new ArrayList<>();
            Page<JsonObject> users;
            List<JsonObject> images;
            List<JsonObject> connections;
            List<JsonObject> frequencies;

            boolean usersFetched = false;
            boolean consultationsFetched = false;
            boolean connectionsFetched = false;
            boolean frequenciessFetched = false;

            List<Long> userIds;

            if (ReflectionUtils.findField(User.class, sortColumn) != null || sortColumn.equals("projectRole")) {
                users = this.listUsersByProject(project, searchParameters, sortColumn, sortDirection, max, offset);
                usersFetched = true;
            } else {
                users = this.listUsersByProject(project, searchParameters, "id", "asc", 0L, 0L);
            }
            userIds = users.stream().map(x -> (Long) x.get("id")).collect(Collectors.toList());
            Map<Long, JsonObject> userMap = users.stream().collect(Collectors.toMap(JsonObject::getId, Function.identity()));

            switch (sortColumn) {
                case "lastImageName":
                    images = imageConsultationService.lastImageOfGivenUsersByProject(project, userIds, "name", sortDirection, max, offset);
                    results = images.stream().map(x -> JsonObject.of("id", x.get("user"), "lastImage", x.get("image"))).collect(Collectors.toList());
                    userIds = results.stream().map(x -> (Long) x.get("id")).collect(Collectors.toList());
                    consultationsFetched = true;
                    break;
                case "lastConnection":
                    connections = projectConnectionService.lastConnectionOfGivenUsersInProject(project, userIds, "created", sortDirection, max, offset);
                    results = connections.stream().map(x -> JsonObject.of("id", x.get("user"), "lastConnection", (x.get("created")!=null? ((Date)x.get("created")).getTime() : null))).collect(Collectors.toList());
                    userIds = results.stream().map(x -> (Long) x.get("id")).collect(Collectors.toList());
                    connectionsFetched = true;
                    break;
                case "frequency":
                    frequencies = projectConnectionService.numberOfConnectionsOfGivenByProject(project, userIds, "frequency", sortDirection, max, offset);
                    results = frequencies.stream().map(x -> JsonObject.of("id", x.get("user"), "numberConnections", x.getJSONAttrInteger("frequency", 0))).collect(Collectors.toList());
                    userIds = results.stream().map(x -> (Long) x.get("id")).collect(Collectors.toList());
                    frequenciessFetched = true;
                    break;
            }

            if (!usersFetched) {
                for (JsonObject entry : results) {
                    Map<String, Object> userJson = userMap.get((Long) entry.get("id"));
                    entry.putAll(userJson);
                }
            }
            if (!consultationsFetched && userSearchExtension.isWithLastImage()) {
                images = imageConsultationService.lastImageOfUsersByProject(project, userIds, "id", "asc", 0L, 0L);
                Map<Long, JsonObject> imagesMap = images.stream().collect(Collectors.toMap(x -> x.getJSONAttrLong("user"), Function.identity()));
                for (JsonObject entry : results) {
                    Optional<Map<String, Object>> image = Optional.ofNullable(imagesMap.get(entry.getId()));
                    entry.put("lastImage", image.map(x -> x.get("image")).orElse(null));
                }
                consultationsFetched = true;
            }
            if (!connectionsFetched && userSearchExtension.isWithLastConnection()) {
                connections = projectConnectionService.lastConnectionInProject(project, userIds, "id", "asc", 0L, 0L);
                Map<Long, JsonObject> connectionsMap = connections.stream().collect(Collectors.toMap(x -> x.getJSONAttrLong("user"), Function.identity()));
                for (JsonObject user : results) {
                    Optional<Map<String, Object>> connection = Optional.ofNullable(connectionsMap.get(user.getId()));
                    user.put("lastConnection", connection.map(x -> (x.get("created")!=null ? ((Date)x.get("created")).getTime() : null)).orElse(null));
                }
                connectionsFetched = true;
            }
            if (!frequenciessFetched && userSearchExtension.isWithNumberConnections()) {
                frequencies = projectConnectionService.numberOfConnectionsByProjectAndUser(project, userIds, "id", "asc", 0L, 0L);
                Map<Long, JsonObject> frequencyMap = frequencies.stream().collect(Collectors.toMap(x -> x.getJSONAttrLong("user"), Function.identity()));
                for (JsonObject user : results) {
                    Optional<JsonObject> frequency = Optional.ofNullable(frequencyMap.get(user.getId()));
                    user.put("numberConnections", frequency.map(x -> x.getJSONAttrInteger("frequency",0)).orElse(null));
                }
                frequenciessFetched = true;
            }
            return PageUtils.buildPageFromPageResults(results, max, offset, (long)results.size());
        }

    }

    public Page<JsonObject> listUsersByProject(Project project, List<SearchParameterEntry> searchParameters, String sortColumn, String sortDirection, Long max, Long offset) {
        securityACLService.check(project, READ);
        // migration from grails: parameter boolean withProjectRole is always true
        Optional<SearchParameterEntry> onlineUserSearch = searchParameters.stream().filter(x -> x.getProperty().equals("status") && x.getValue().equals("online")).findFirst();
        Optional<SearchParameterEntry> multiSearch = searchParameters.stream().filter(x -> x.getProperty().equals("fullName")).findFirst();
        Optional<SearchParameterEntry> projectRoleSearch = searchParameters.stream().filter(x -> x.getProperty().equals("projectRole")).findFirst();

        String select = "select distinct secUser ";
        String from = "from ProjectRepresentativeUser r right outer join r.user secUser ON (r.project.id = " + project.getId() + "), " +
                "AclObjectIdentity as aclObjectId, AclEntry as aclEntry, AclSid as aclSid ";
        String where = "where aclObjectId.objectId = " + project.getId() + " " +
                "and aclEntry.aclObjectIdentity = aclObjectId.id " +
                "and aclEntry.sid = aclSid.id " +
                "and aclSid.sid = secUser.username " +
                "and secUser.class = 'be.cytomine.domain.security.User' ";
        String groupBy = "";
        String order = "";
        String having = "";

        if (multiSearch.isPresent()) {
            String value = ((String) multiSearch.get().getValue()).toLowerCase();
            where += " and (lower(secUser.firstname) like '%$value%' or lower(secUser.lastname) like '%" + value + "%' or lower(secUser.email) like '%" + value + "%') ";
        }
        if (onlineUserSearch.isPresent()) {
            List<Long> onlineUsers = getAllOnlineUserIds(project);
            if (onlineUsers.isEmpty()) {
                return Page.empty();
            }
            where += " and secUser.id in (" + onlineUsers.stream().map(String::valueOf).collect(Collectors.joining(",")) + ") ";
        }


        if (projectRoleSearch.isPresent()) {
            List<String> roles = (projectRoleSearch.get().getValue() instanceof String) ? List.of((String) projectRoleSearch.get().getValue()) : (List<String>) projectRoleSearch.get().getValue();
            having += " HAVING MAX(CASE WHEN r.id IS NOT NULL THEN 'representative' " +
                    "WHEN aclEntry.mask = 16 THEN 'manager' " +
                    "ELSE 'contributor' END) IN (" + roles.stream().map(x -> "'" + x + "'").collect(Collectors.joining(",")) + ")";
        }

        //works because 'contributor' < 'manager' < 'representative'
        select += ", MAX( CASE WHEN r.id IS NOT NULL THEN 'representative'\n" +
                "     WHEN aclEntry.mask = 16 THEN 'manager'\n" +
                "     ELSE 'contributor'\n" +
                " END) as role ";
        groupBy = "GROUP BY secUser.id ";

        if (sortColumn.equals("projectRole")) {
            sortColumn = "role";
        } else if (sortColumn.equals("fullName")) {
            sortColumn = "secUser.firstname";
        } else {
            sortColumn = "secUser." + sortColumn;
        }
        order = " order by " + sortColumn + " " + sortDirection;

        String request = select + from + where + groupBy + having + order;

//        if (max > 0) {
//            request += " LIMIT " + max;
//        }
//        if (offset > 0) {
//            request += " OFFSET " + offset;
//        }


        Query query = getEntityManager().createQuery(request, Object[].class);

        if (max>0) {
            query.setMaxResults(max.intValue());
        }
        if (offset>0) {
            query.setFirstResult(offset.intValue());
        }

        List<JsonObject> results = new ArrayList<>();
        List<Object[]> resultList = query.getResultList();
        for (Object[] row : resultList) {
            JsonObject jsonObject = ((User) row[0]).toJsonObject();
            jsonObject.put("role", (String) row[1]);
            results.add(jsonObject);
        }


//        List<Map<String, Object>> results = new ArrayList<>();
//        for (Tuple rowResult : resultList) {
//            JsonObject result = new JsonObject();
//            for (TupleElement<?> element : rowResult.getElements()) {
//                Object value = rowResult.get(element.getAlias());
//                if (value instanceof BigInteger) {
//                    value = ((BigInteger)value).longValue();
//                }
//                String alias = SQLUtils.toCamelCase(element.getAlias());
//                result.put(alias, value);
//            }
//            JsonObject object = Project.getDataFromDomain(new User().buildDomainFromJson(result, getEntityManager()));
//            object.put("role", result.get("role"));
//            results.add(object);
//        }
        request = "SELECT COUNT(DISTINCT secUser) " + from + where;
        query = getEntityManager().createQuery(request);
        long count = ((Long) query.getResultList().get(0));
        Page<JsonObject> page = PageUtils.buildPageFromPageResults(results, max, offset, count);
        return page;


    }


    public List<SecUser> listAdmins(Project project) {
        return listAdmins(project, true);
    }

    public List<SecUser> listAdmins(Project project, boolean checkPermission) {
        if (checkPermission) {
            securityACLService.check(project, READ);
        }
        return secUserRepository.findAllAdminsByProjectId(project.getId());
    }

    public Optional<SecUser> findCreator(Project project) {
        securityACLService.check(project,READ);
        return aclRepository.listCreators(project.getId()).stream().findFirst();
    }

    public List<SecUser> listUsers(Project project) {
        return listUsers(project, false, true);
    }

    public List<SecUser> listUsers(Project project, boolean showUserJob, boolean checkPermission) {
        if (checkPermission) {
            securityACLService.check(project, READ);
        }
        List<SecUser> users = secUserRepository.findAllUsersByProjectId(project.getId());

        if (showUserJob) {
            //TODO:: should be optim (see method head comment)
//            List<Job> allJobs = Job.findAllByProject(project, [sort: 'created', order: 'desc'])
//
//            allJobs.each { job ->
//                   public void userJob = UserJob.findByJob(job);
//                if (userJob) {
//                    userJob.username = job.software.name + " " + job.created
//                    users << userJob
//                }
//            }
            throw new RuntimeException("Not yet implemented (showUserJob)");
        }
        return users;
    }

    public List<SecUser> list(List<Long> ids) {
        return secUserRepository.findAllByIdIn(ids);
    }

    public List<SecUser> listUsers(Ontology ontology) {
        securityACLService.check(ontology, READ);
        //TODO:: Not optim code a single SQL request will be very faster
        List<SecUser> users = new ArrayList<>();
        List<Project> projects = projectRepository.findAllByOntology(ontology);
        for (Project project : projects) {
            users.addAll(listUsers(project));
        }
        return users.stream().distinct().collect(Collectors.toList());
    }

    public List<SecUser> listUsers(Storage storage) {
        securityACLService.check(storage, READ);
        return secUserRepository.findAllUsersByStorageId(storage.getId());
    }

    //TODO: software migration
//    /**
//     * Get all allowed user id for a specific domain instance
//     * E.g; get all user id for a project
//     */
//    List<Long> getAllowedUserIdList(CytomineDomain domain) {
//        String request = "SELECT DISTINCT sec_user.id \n" +
//                " FROM acl_object_identity, acl_entry,acl_sid, sec_user \n" +
//                " WHERE acl_object_identity.object_id_identity = $domain.id\n" +
//                " AND acl_entry.acl_object_identity=acl_object_identity.id\n" +
//                " AND acl_entry.sid = acl_sid.id " +
//                " AND acl_sid.sid = sec_user.username " +
//                " AND sec_user.class = 'be.cytomine.domain.security.User' "
//       public void data = []
//       public void sql = new Sql(dataSource)
//        sql.eachRow(request) {
//            data << it[0]
//        }
//        try {
//            sql.close()
//        }catch (Exception e) {}
//        return data
//    }
//
//
//    privatepublic void getUserJobImage(ImageInstance image) {
//
//        String request = "SELECT DISTINCT u.id as id, u.username as username, " +
//                "s.name as softwareName, s.software_version as softwareVersion, " +
//                "j.created as created, u.job_id as job " +
//                "FROM annotation_index ai " +
//                "RIGHT JOIN slice_instance si ON ai.slice_id = si.id " +
//                "RIGHT JOIN sec_user u ON ai.user_id = u.id " +
//                "RIGHT JOIN job j ON j.id = u.job_id " +
//                "RIGHT JOIN software_project sp ON sp.software_id = j.software_id " +
//                "RIGHT JOIN software s ON s.id = sp.software_id " +
//                "WHERE si.image_id = ${image.id} " +
//                "AND sp.project_id = ${image.project.id} " +
//                "ORDER BY j.created"
//       public void data = []
//       public void sql = new Sql(dataSource)
//        sql.eachRow(request) {
//           public void item = [:]
//            item.id = it.id
//            item.username = it.username
//            item.softwareName = (it.softwareVersion?.trim()) ? "${it.softwareName} (${it.softwareVersion})" : it.softwareName
//
//            item.created = it.created
//            item.algo = true
//            item.job = it.job
//            data << item
//        }
//        try {
//            sql.close()
//        }catch (Exception e) {}
//        data
//    }

    public CommandResponse lock(SecUser user) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkAdmin(currentUser);
        if (!user.getEnabled()) {
            throw new WrongArgumentException("User already locked !");
        }

        return executeCommand(new EditCommand(currentUser, null), user, user.toJsonObject().withChange("enabled", false));
    }

    public CommandResponse unlock(SecUser user) {
        SecUser currentUser = currentUserService.getCurrentUser();
        log.info("unlock user " + user.getUsername() + " triggered by " + currentUser.getUsername());
        securityACLService.checkAdmin(currentUser);
        if (user.getEnabled()) {
            throw new WrongArgumentException("User already unlocked !");
        }

        return executeCommand(new EditCommand(currentUser, null), user, user.toJsonObject().withChange("enabled", true));
    }

    public List<SecUser> listAll(Project project) {
        List<SecUser> data = new ArrayList<>();
        data.addAll(listUsers(project));
        //TODO: could be optim!!!
        // TODO: after software package
        //data.addAll(UserJob.findAllByJobInList(Job.findAllByProject(project)))
        return data;
    }
    /**
     * List all layers from a project
     * Each user has its own layer
     * If project has private layer, just get current user layer
     */
    public List<JsonObject> listLayers(Project project, ImageInstance image) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.check(project, READ, currentUser);

        List<SecUser> humanAdmins = listAdmins(project);
        List<SecUser> humanUsers = listUsers(project);

        List<JsonObject> humanUsersFormatted = humanUsers.stream().map(SecUser::toJsonObject).collect(Collectors.toList());

        List<JsonObject> layersFormatted = new ArrayList<>();

        if (permissionService.hasACLPermission(project, ADMINISTRATION, currentRoleService.isAdminByNow(currentUser))
                || (!project.isHideAdminsLayers() && !project.isHideUsersLayers())) {
            layersFormatted.addAll(humanUsersFormatted);
        } else if (project.isHideAdminsLayers() && !project.isHideUsersLayers()) {
            Set<Long> humanAdminsIds = humanAdmins.stream().map(CytomineDomain::getId).collect(Collectors.toSet());
            layersFormatted.addAll(humanUsersFormatted.stream()
                    .filter(x -> !humanAdminsIds.contains(x.getJSONAttrLong("id"))).collect(Collectors.toList()));
        } else if (!project.isHideAdminsLayers() && project.isHideUsersLayers()) {
            layersFormatted.addAll(humanAdmins.stream().map(SecUser::toJsonObject).collect(Collectors.toList()));
        }

        if (humanUsers.contains(currentUser) && layersFormatted.stream().noneMatch(x -> x.getJSONAttrLong("id").equals(currentUser.getId()))) {
            layersFormatted.add(currentUser.toJsonObject());
        }

        if (image != null) {
            layersFormatted.addAll(secUserRepository.findAllUserJob(image.getId(), image.getProject().getId()).stream().map(JobLayerDTO::getDataFromDomain).collect(Collectors.toList()));
        }
        return layersFormatted;
    }

    public List<JsonObject> getAllOnlineUserWithTheirPositions(Project project) {
//        //Get all project user online
        List<Long> usersId = this.getAllFriendsUsersOnline(currentUserService.getCurrentUser(), project).stream().map(CytomineDomain::getId)
                    .collect(Collectors.toList());
        List<JsonObject> usersWithPosition = userPositionService.findUsersPositions(project);
        usersId.removeAll(usersWithPosition.stream().map(JsonObject::getId).collect(Collectors.toList()));

        for (Long userId : usersId) {
            usersWithPosition.add(JsonObject.of("id", userId, "position", new ArrayList<>()));
        }

        return usersWithPosition;
    }



    public JsonObject getResumeActivities(Project project, User user) {
        securityACLService.checkIsSameUserOrAdminContainer(project,user, currentUserService.getCurrentUser());
        JsonObject jsonObject = new JsonObject();

        jsonObject.put("firstConnection", persistentProjectConnectionRepository
                .findAllByUserAndProject(user.getId(), project.getId(), PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "created"))).stream().findFirst().map(x -> x.getCreated()).orElse(null));
        jsonObject.put("lastConnection", persistentProjectConnectionRepository
                .findAllByUserAndProject(user.getId(), project.getId(), PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "created"))).stream().findFirst().map(x -> x.getCreated()).orElse(null));

        jsonObject.put("totalAnnotations", userAnnotationService.count(user, project));
        jsonObject.put("totalConnections", persistentProjectConnectionRepository.countAllByProjectAndUser(project.getId(), user.getId()));
        jsonObject.put("totalConsultations", persistentImageConsultationRepository.countByProjectAndUser(project.getId(), user.getId()));
        jsonObject.put("totalAnnotationSelections", annotationActionRepository.countByProjectAndUserAndAction(project.getId(), user.getId(), "select"));

        return jsonObject;
    }

    public String fillEmptyUserIds(String users, Long project){
        if (users == null || users.equals("")) {
            users = getUsersIdsFromProject(project);
        }
        return users;
    }

    public String getUsersIdsFromProject(Long project){
        String users = "";
        for (SecUser user: listUsers(projectService.get(project))) {
            users += user.getId() + ",";
        }
        return users;
    }

    public List<JsonObject> getUsersWithLastActivities(Project project) {
        List<JsonObject> results = new ArrayList<>();
        List<SecUser> users = listUsers(project).stream().sorted(Comparator.comparing(CytomineDomain::getId)).collect(Collectors.toList());


        Map<Long, JsonObject> connections = projectConnectionService.lastConnectionInProject(project, null, "user", "asc", 0L, 0L)
                .stream().collect(Collectors.toMap(x -> x.getJSONAttrLong("user"), Function.identity()));
        Map<Long, JsonObject> frequencies = projectConnectionService.numberOfConnectionsByProjectAndUser(project, null, "user", "asc", 0L, 0L)
                .stream().collect(Collectors.toMap(x -> x.getJSONAttrLong("user"), Function.identity()));
        Map<Long, JsonObject> images = imageConsultationService.lastImageOfUsersByProject(project, null, "user", "asc", 0L, 0L)
                .stream().collect(Collectors.toMap(x -> x.getJSONAttrLong("user"), Function.identity()));

        for (SecUser secUser : users) {
            if (secUser instanceof User) {
                User user = (User)secUser;

                JsonObject image = images.get(user.getId());
                JsonObject connection = connections.get(user.getId());
                JsonObject frequency = frequencies.get(user.getId());

                JsonObject jsonObject = new JsonObject();
                jsonObject.put("id", user.getId());
                jsonObject.put("username", user.getUsername());
                jsonObject.put("firstname", user.getFirstname());
                jsonObject.put("lastname", user.getLastname());
                jsonObject.put("email", user.getEmail());
                jsonObject.put("lastImageId", (image!=null? image.get("image") : null));
                jsonObject.put("lastImageName", (image!=null? image.get("imageName") : null));
                jsonObject.put("lastConnection", (connection!=null? connection.get("created") : null));
                jsonObject.put("frequency", (frequency!=null? frequency.get("frequency") : 0));
                results.add(jsonObject);
            }

        }
        return results;
    }

    /**
     * Get all online user
     */
    public List<SecUser> getAllOnlineUsers() {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        //get date with -X secondes
        Date xSecondAgo = DateUtils.addSeconds(new Date(), -300);
        // TODO: could be improve regarding performance...
        List<LastConnection> connections = lastConnectionRepository.findAllByCreatedAfter(xSecondAgo);
        List<Long> userIds = connections.stream().map(LastConnection::getUser).distinct().collect(Collectors.toList());
        return secUserRepository.findAllByIdIn(userIds);
    }

    /**
     * Get all online userIds for a project
     */
    public List<Long> getAllOnlineUserIds(Project project) {
        securityACLService.checkGuest(currentUserService.getCurrentUser());
        //get date with -X secondes
        Date xSecondAgo = DateUtils.addSeconds(new Date(), -300);
        // TODO: could be improve regarding performance...
        List<LastConnection> connections = lastConnectionRepository.findAllByProjectAndCreatedAfter(project.getId(), xSecondAgo);
        List<Long> userIds = connections.stream().map(LastConnection::getUser).distinct().collect(Collectors.toList());
        return userIds;
    }

    /**
     * Get all online user for a project
     */
    public List<SecUser> getAllOnlineUsers(Project project) {
        securityACLService.check(project, READ);
        return secUserRepository.findAllByIdIn(getAllOnlineUserIds(project));
    }

    /**
     * Get all user that share at least a same project as user from argument
     */
    public List<SecUser> getAllFriendsUsers(SecUser user) {
        securityACLService.checkIsSameUser(user, currentUserService.getCurrentUser());
        return secUserRepository.findAllSecUsersSharingAccesToSameProject(user.getUsername());
    }

    /**
     * Get all online user that share at least a same project as user from argument
     */
    public List<SecUser> getAllFriendsUsersOnline(SecUser user) {
        securityACLService.checkIsSameUser(user, currentUserService.getCurrentUser());
        List<SecUser> friends = getAllFriendsUsers(user);
        List<SecUser> friendsOnline = getAllOnlineUsers().stream()
                .distinct()
                .filter(friends::contains)
                .collect(Collectors.toList());
        return friendsOnline;
    }

    /**
     * Get all user that share at least a same project as user from argument and
     */
    public List<SecUser> getAllFriendsUsersOnline(SecUser user, Project project) {
        securityACLService.check(project, READ);
        //no need to make insterect because getAllOnlineUsers(project) contains only friends users
        return getAllOnlineUsers(project);
    }


    public Optional<UserJob> findByJobId(Long job) {
        // TODO:
        throw new CytomineMethodNotYetImplementedException("not yet implemented");
    }


    /**
     * Add the new domain with JSON data
     *
     * @param json New domain data
     * @return Response structure (created domain data,..)
     */
    public CommandResponse add(JsonObject json) {
        synchronized (this.getClass()) {
            SecUser currentUser = currentUserService.getCurrentUser();
            securityACLService.checkUser(currentUser);
            if (!json.containsKey("user")) {
                json.put("user", currentUser.getId());
                json.put("origin", "ADMINISTRATOR");
            }
            CommandResponse response = executeCommand(new AddCommand(currentUser), null, json);

            User user = (User)response.getObject();
            user.setPassword(json.getJSONAttrStr("password", true));
            user.encodePassword(passwordEncoder);
            user.setNewPassword(null);
            return response;
        }
    }

    /**
     * Update this domain with new data from json
     *
     * @param domain      Domain to update
     * @param jsonNewData New domain datas
     * @return Response structure (new domain data, old domain data..)
     */
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkIsCreator((SecUser) domain, currentUser);
        if (!jsonNewData.isMissing("password")) {
            changeUserPassword((User)domain, jsonNewData.getJSONAttrStr("password"));
        }
        return executeCommand(new EditCommand(currentUser, null), domain, jsonNewData);
    }

    /**
     * Delete this domain
     *
     * @param domain       Domain to delete
     * @param transaction  Transaction link with this command
     * @param task         Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    public CommandResponse delete(CytomineDomain domain, Transaction transaction, Task task, boolean printMessage) {
        SecUser currentUser = currentUserService.getCurrentUser();
        if (((SecUser) domain).isAlgo()) {
//            Job job = ((UserJob)domain).job
//            securityACLService.check(job?.container(),READ)
//            securityACLService.checkFullOrRestrictedForOwner(job, ((UserJob)domain).user)
            // TODO: software package
            throw new CytomineMethodNotYetImplementedException("software package not yet implemented");
        } else {
            securityACLService.checkAdmin(currentUser);
            securityACLService.checkIsSameUser((User) domain, currentUser);
        }
        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c, domain, null);
    }

    @Override
    public Class currentDomain() {
        return SecUser.class;
    }

    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new User().buildDomainFromJson(json, getEntityManager());
    }

    @Override
    public List<String> getStringParamsI18n(CytomineDomain domain) {
        SecUser secUser = (SecUser) domain;
        return Arrays.asList(String.valueOf(secUser.getId()), secUser.getUsername());
    }

    public void checkDoNotAlreadyExist(CytomineDomain domain) {
        SecUser user = (SecUser) domain;
        if (user.getUsername()==null) {
            throw new WrongArgumentException("Username is not set");
        }
        Optional<SecUser> userWithSameUsername = secUserRepository.findByUsernameLikeIgnoreCase(user.getUsername());
        if (userWithSameUsername.isPresent() && !Objects.equals(userWithSameUsername.get().getId(), user.getId())) {
            throw new AlreadyExistException("User " + user.getUsername() + " already exist!");
        }
        if (domain instanceof User) {
            Optional<User> userWithSameEmail = userRepository.findByEmailLikeIgnoreCase(((User)user).getEmail());
            if (userWithSameEmail.isPresent() && !Objects.equals(userWithSameEmail.get().getId(), user.getId())) {
                throw new AlreadyExistException("User with email " + ((User)user).getEmail() + " already exist!");
            }
        }
    }

    public void changeUserPassword(User user, String newPassword) {
        securityACLService.checkIsCreator(user,currentUserService.getCurrentUser());
        user.setPassword(newPassword);
        user.encodePassword(passwordEncoder);
        user.setPasswordExpired(false);
        user.setNewPassword(null);
        this.saveDomain(user);
    }

    public boolean isUserPassword(User user, String password) {
        securityACLService.checkIsSameUser(user, currentUserService.getCurrentUser());
        return passwordEncoder.matches(password, user.getPassword());
    }


    /**
     * Add a user in project user or admin list
     *
     * @param user    User to add in project
     * @param project Project that will be accessed by user
     * @param admin   Flaf if user will become a simple user or a project manager
     * @return Response structure
     */
    public void addUserToProject(SecUser user, Project project, boolean admin) {
        securityACLService.check(project, ADMINISTRATION);
        log.info("service.addUserToProject");
        if (project != null) {
            log.info("addUserToProject project=" + project + " user=" + user + " ADMIN=" + admin);
            synchronized (this.getClass()) {
                if (admin) {
                    permissionService.addPermission(project, user.getUsername(), ADMINISTRATION);
                }
                permissionService.addPermission(project, user.getUsername(), READ);
                if (project.getOntology() != null) {
                    log.info("addUserToProject ontology=" + project.getOntology() + " user=" + user + " ADMIN=" + admin);
                    permissionService.addPermission(project.getOntology(), user.getUsername(), READ);
                    if (admin) {
                        permissionService.addPermission(project.getOntology(), user.getUsername(), ADMINISTRATION);
                    }
                }
            }
        }
    }

    /**
     * Delete a user from a project user or admin list
     *
     * @param user    User to remove from project
     * @param project Project that will not longer be accessed by user
     * @param admin   Flaf if user will become a simple user or a project manager
     * @return Response structure
     */
    public void deleteUserFromProject(SecUser user, Project project, boolean admin) {
        if (!Objects.equals(currentUserService.getCurrentUser().getId(), user.getId())) {
            securityACLService.check(project, ADMINISTRATION);
        }
        if (project != null) {
            log.info("deleteUserFromProject project=" + project.getId() + " username=" + user.getUsername() + " ADMIN=" + admin);

            log.info("deleteUserFromProject BEFORE ADMINISTRATION=" + permissionService.hasACLPermission(project, user.getUsername(), ADMINISTRATION));
            log.info("deleteUserFromProject BEFORE READ=" + permissionService.hasACLPermission(project, user.getUsername(), READ));
            if (admin) {
                permissionService.deletePermission(project, user.getUsername(), ADMINISTRATION);
            } else {
                permissionService.deletePermission(project, user.getUsername(), READ);
            }
            log.info("deleteUserFromProject AFTER ADMINISTRATION=" + permissionService.hasACLPermission(project, user.getUsername(), ADMINISTRATION));
            log.info("deleteUserFromProject AFTER READ=" + permissionService.hasACLPermission(project, user.getUsername(), READ));
            if (!permissionService.hasACLPermission(project, user.getUsername(), READ) && project.getOntology() != null) {
                removeOntologyRightIfNecessary(project, (User) user, admin);
            }
            // if no representative, add current user as a representative
            boolean hasLostAccessToProject = (!permissionService.hasACLPermission(project, user.getUsername(), READ) && !permissionService.hasACLPermission(project, user.getUsername(), READ));
            boolean isLastRepresentative = projectRepresentativeUserService.listByProject(project).size() == 1 &&
                    projectRepresentativeUserService.listByProject(project).get(0).getUser().getId().equals(user.getId());
            if (hasLostAccessToProject && isLastRepresentative) {
                if (!securityACLService.getProjectList(currentUserService.getCurrentUser(), null).contains(project)) {
                    // if current user is not in project (= SUPERADMIN), add to the project
                    addUserToProject(currentUserService.getCurrentUser(), project, true);
                }
                log.info("add current user " + currentUserService.getCurrentUsername() + " as representative for project " + project.getId());
                ProjectRepresentativeUser pru = new ProjectRepresentativeUser();
                pru.setProject(project);
                pru.setUser((User) currentUserService.getCurrentUser());
                projectRepresentativeUserService.add(pru.toJsonObject());

                projectRepresentativeUserService.find(project, (User) user)
                        .ifPresent(x -> projectRepresentativeUserService.delete(x, null, null, false));

            }
            log.info("deleteUserFromProject " + permissionService.hasACLPermission(project, user.getUsername(), ADMINISTRATION));
        }
    }

    private void removeOntologyRightIfNecessary(Project project, User user, boolean admin) {
        //we remove the right ONLY if user has no other project with this ontology
        List<Project> projects = securityACLService.getProjectList(user, project.getOntology());
        List<Project> otherProjects = new ArrayList<>(projects);
        otherProjects.remove(project);

        if (otherProjects.isEmpty()) {
            //user has no other project with this ontology, remove the right!
            permissionService.deletePermission(project.getOntology(), user.getUsername(), READ);
            permissionService.deletePermission(project.getOntology(), user.getUsername(), ADMINISTRATION);
        } else if (admin) {
            List<Long> managedProjectList = projectService.listByAdmin(user).stream().map(NamedCytomineDomain::getId).collect(Collectors.toList());
            List<Long> otherProjectsIds = otherProjects.stream().map(CytomineDomain::getId).collect(Collectors.toList());
            if (managedProjectList.stream().noneMatch(otherProjectsIds::contains)) {
                permissionService.deletePermission(project.getOntology(), user.getUsername(), ADMINISTRATION);
            }
        }

    }


    public void addUserToStorage(SecUser user, Storage storage) {
        securityACLService.check(storage, ADMINISTRATION);
        log.info("Add user {} to storage {}", user, storage);
        permissionService.addPermission(storage, user.getUsername(), READ);
        permissionService.addPermission(storage, user.getUsername(), WRITE);
    }

    public void deleteUserFromStorage(SecUser user, Storage storage) {
        securityACLService.checkIsSameUserOrAdminContainer(storage, user, currentUserService.getCurrentUser());

        if (user == storage.getUser()) {
            throw new WrongArgumentException("The storage owner cannot be deleted.");
        }

        log.info("Remove user {} from storage {}", user, storage);
        permissionService.deletePermission(storage, user.getUsername(), READ);
        permissionService.deletePermission(storage, user.getUsername(), WRITE);
    }

    @Override
    protected void beforeDelete(CytomineDomain domain) {
        User user = (User) domain;
        commandHistoryRepository.deleteAllByUser(user);
        redoStackItemRepository.deleteAllByUser(user);
        undoStackItemRepository.deleteAllByUser(user);
        commandRepository.deleteAllByUser(user);
    }


    protected void afterAdd(CytomineDomain domain, CommandResponse response) {
        SecUserSecRole secUserSecRole = new SecUserSecRole();
        secUserSecRole.setSecUser((SecUser) domain);
        secUserSecRole.setSecRole(secRoleRepository.getUser());

        if (secUserSecRoleRepository.findBySecUserAndSecRole(secUserSecRole.getSecUser(), secUserSecRole.getSecRole()).isEmpty()) {
            secUserSecRoleRepository.save(secUserSecRole);
        }

        if (domain instanceof User) {
            storageService.initUserStorage((SecUser) domain);
        }
    }

    @Override
    public CytomineDomain retrieve(JsonObject json) {
        return secUserRepository.findById(json.getJSONAttrLong("id"))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecUser", json.getJSONAttrLong("id")));
    }


    public void deleteDependencies(CytomineDomain domain, Transaction transaction, Task task) {
        deleteDependentAlgoAnnotation((SecUser) domain, transaction, task);
        deleteDependentAlgoAnnotationTerm((SecUser) domain, transaction, task);
        deleteDependentAnnotationTerm((SecUser) domain, transaction, task);
        deleteDependentImageInstance((SecUser) domain, transaction, task);
        deleteDependentOntology((SecUser) domain, transaction, task);
        //deleteDependentForgotPasswordToken((SecUser) domain, transaction, task);
        deleteDependentReviewedAnnotation((SecUser) domain, transaction, task);
        deleteDependentSecUserSecRole((SecUser) domain, transaction, task);
        deleteDependentAbstractImage((SecUser) domain, transaction, task);
        deleteDependentUserAnnotation((SecUser) domain, transaction, task);
        //deleteDependentUserJob((SecUser) domain, transaction, task);
        deleteDependentUploadedFile((SecUser) domain, transaction, task);
        deleteDependentStorage((SecUser) domain, transaction, task);
        //deleteDependentSharedAnnotation((SecUser) domain, transaction, task);
        //deleteDependentHasManySharedAnnotation((SecUser) domain, transaction, task);
        deleteDependentAnnotationIndex((SecUser) domain, transaction, task);
        deleteDependentNestedImageInstance((SecUser) domain, transaction, task);
        deleteDependentProjectDefaultLayer((SecUser) domain, transaction, task);
        deleteDependentProjectRepresentativeUser((SecUser) domain, transaction, task);
        deleteDependentMessageBrokerServer((SecUser) domain, transaction, task);
    }


    public void deleteDependentAlgoAnnotation(SecUser user, Transaction transaction, Task task) {
        if (user instanceof UserJob) {
            for (AlgoAnnotation algoAnnotation : algoAnnotationRepository.findAllByUser((UserJob) user)) {
                algoAnnotationService.delete(algoAnnotation, transaction, task, false);
            }
        }
    }

    public void deleteDependentAlgoAnnotationTerm(SecUser user, Transaction transaction, Task task) {
        if (user instanceof UserJob) {
            for (AlgoAnnotationTerm algoAnnotationTerm : algoAnnotationTermRepository.findAllByUserJob((UserJob) user)) {
                algoAnnotationTermService.delete(algoAnnotationTerm, transaction, task, false);
            }
        }
    }

    public void deleteDependentAnnotationTerm(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            for (AnnotationTerm annotationTerm : annotationTermRepository.findAllByUser((User) user)) {
                annotationTermService.delete(annotationTerm, transaction, task, false);
            }
        }
    }

    public void deleteDependentImageInstance(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            for (ImageInstance imageInstance : imageInstanceRepository.findAllByUser((User) user)) {
                imageInstanceService.delete(imageInstance, transaction, task, false);
            }
        }
    }

    public void deleteDependentOntology(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            for (Ontology ontology : ontologyRepository.findAllByUser((User) user)) {
                ontologyService.delete(ontology, transaction, task, false);
            }
        }
    }


    public void deleteDependentForgotPasswordToken(SecUser secUser, Transaction transaction, Task task) {
        if (secUser instanceof User) {
            User user = (User) secUser;
//              ForgotPasswordToken.findAllByUser(user).each {
//                  it.delete()
//              }
            //TODO
            throw new CytomineMethodNotYetImplementedException("todo");
        }
    }

    public void deleteDependentReviewedAnnotation(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            for (ReviewedAnnotation reviewedAnnotation : reviewedAnnotationRepository.findAllByUser((User) user)) {
                reviewedAnnotationService.delete(reviewedAnnotation, transaction, null, false);
            }
        }
    }

    public void deleteDependentSecUserSecRole(SecUser user, Transaction transaction, Task task) {
        for (SecUserSecRole secUserSecRole : secUserSecRoleRepository.findAllBySecUser(user)) {
            secUserSecRoleService.delete(secUserSecRole, transaction, null, false);
        }
    }

    public void deleteDependentAbstractImage(SecUser user, Transaction transaction, Task task) {
        //:to do implemented this ? allow this or not ?
    }

    public void deleteDependentUserAnnotation(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            for (UserAnnotation userAnnotation : userAnnotationRepository.findAllByUser((User) user)) {
                userAnnotationService.delete(userAnnotation, transaction, null, false);
            }
        }
    }

    public void deleteDependentUserJob(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
//            UserJob.findAllByUser((User)user).each {
//                delete(it,transaction,null,false)
//            }
            throw new CytomineMethodNotYetImplementedException("todo");
        }
    }


    public void deleteDependentUploadedFile(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            uploadedFileRepository.deleteAllByUser((User) user);
        }
    }


    public void deleteDependentStorage(SecUser user, Transaction transaction, Task task) {
        for (Storage storage : storageRepository.findAllByUser(user)) {
            if (uploadedFileRepository.countByStorage(storage) > 0) {
                throw new ConstraintException("Storage contains data, cannot delete user. Remove or assign storage to an another user first");
            } else {
                storageService.delete(storage, transaction, null, false);
            }
        }
    }

    public void deleteDependentSharedAnnotation(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            //TODO:: implement cascade deleteting/update for shared annotation
//            if(SharedAnnotation.findAllBySender(user)) {
//                throw new ConstraintException("This user has send/receive annotation comments. We cannot delete it! ")
//            }
            throw new CytomineMethodNotYetImplementedException("todo");
        }
    }

    public void deleteDependentHasManySharedAnnotation(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            //TODO:: implement cascade deleteting/update for shared annotation
//           public void criteria = SharedAnnotation.createCriteria()
//           public void results = criteria.list {
//                receivers {
//                    inList("id", user.id)
//                }
//            }
//
//            if(!results.isEmpty()) {
//                throw new ConstraintException("This user has send/receive annotation comments. We cannot delete it! ")
//            }
            throw new CytomineMethodNotYetImplementedException("todo");
        }
    }

    public void deleteDependentAnnotationIndex(SecUser user, Transaction transaction, Task task) {
        annotationIndexRepository.deleteAllByUser(user);
    }

    public void deleteDependentNestedImageInstance(SecUser user, Transaction transaction, Task task) {
        nestedImageInstanceRepository.deleteAllByUser((User) user);
    }

    public void deleteDependentProjectDefaultLayer(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            for (ProjectDefaultLayer projectDefaultLayer : projectDefaultLayerRepository.findAllByUser(user)) {
                projectDefaultLayerService.delete(projectDefaultLayer, transaction, null, false);
            }
        }
    }

    public void deleteDependentProjectRepresentativeUser(SecUser user, Transaction transaction, Task task) {
        if (user instanceof User) {
            for (ProjectRepresentativeUser projectRepresentativeUser : projectRepresentativeUserRepository.findAllByUser(user)) {
                projectRepresentativeUserService.delete(projectRepresentativeUser, transaction, null, false);
            }
        }
    }

    public void deleteDependentMessageBrokerServer(SecUser user, Transaction transaction, Task task) {

    }



}
