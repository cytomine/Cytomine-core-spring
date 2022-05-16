package be.cytomine.service.social;

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

import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.domain.social.PersistentConnection;
import be.cytomine.domain.social.PersistentImageConsultation;
import be.cytomine.domain.social.PersistentProjectConnection;
import be.cytomine.exceptions.CytomineMethodNotYetImplementedException;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.AnnotationListing;
import be.cytomine.repository.UserAnnotationListing;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.repository.security.SecUserRepository;
import be.cytomine.repositorynosql.social.LastConnectionRepository;
import be.cytomine.repositorynosql.social.PersistentImageConsultationRepository;
import be.cytomine.repositorynosql.social.PersistentProjectConnectionRepository;
import be.cytomine.repositorynosql.social.ProjectConnectionRepository;
import be.cytomine.service.AnnotationListingService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.database.SequenceService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.filters.SearchParameterEntry;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;

import org.hibernate.SessionFactory;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Projections.include;
import static org.springframework.security.acls.domain.BasePermission.*;

@Slf4j
@Service
@Transactional
public class ProjectConnectionService {

    public static final String DATABASE_NAME = "cytomine";
    @Autowired
    CurrentUserService currentUserService;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    SecurityACLService securityACLService;

    @Autowired
    ProjectConnectionRepository projectConnectionRepository;

    @Autowired
    SecUserRepository secUserRepository;

    @Autowired
    MongoClient mongoClient;

    @Autowired
    PersistentProjectConnectionRepository persistentProjectConnectionRepository;

    @Autowired
    AnnotationListingService annotationListingService;

    @Autowired
    LastConnectionRepository lastConnectionRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    SequenceService sequenceService;

    @Autowired
    ImageConsultationService imageConsultationService;

    @Autowired
    PersistentImageConsultationRepository persistentImageConsultationRepository;

    @Autowired
    private SessionFactory sessionFactory;

    public PersistentProjectConnection add(SecUser user, Project project, String session, String os, String browser, String browserVersion) {
        return add(user, project, session, os, browser, browserVersion, new Date());
    }

    public PersistentProjectConnection add(SecUser user, Project project, String session, String os, String browser, String browserVersion, Date created) {
        securityACLService.check(project, READ);
        closeLastProjectConnection(user.getId(), project.getId(), created);

        PersistentProjectConnection connection = new PersistentProjectConnection();
        connection.setId(sequenceService.generateID());
        connection.setUser(user.getId());
        connection.setProject(project.getId());
        connection.setCreated(created);
        connection.setSession(session);
        connection.setOs(os);
        connection.setBrowser(browser);
        connection.setBrowserVersion(browserVersion);

        persistentProjectConnectionRepository.insert(connection);

        return connection;
    }

//    Object lastConnectionInProject(Project project, Long userId, List searchParameters){
//        return lastConnectionInProject(project, userId, searchParameters, "created", "desc", 0L, 0L);
//    }

    public Optional<PersistentProjectConnection> lastConnectionInProject(Project project, Long userId, String sortProperty, String sortDirection){
        SecUser secUser = secUserRepository.findById(userId).orElseThrow(
                () -> ObjectNotFoundException.notFoundException("User", userId));
        securityACLService.checkIsSameUserOrAdminContainer(project, secUser, currentUserService.getCurrentUser());

        return persistentProjectConnectionRepository.findAllByUserAndProject(
                userId,
                project.getId(),
                PageRequest.of(0, 1, (sortDirection.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC), sortProperty)).stream().findFirst();
        }


    public List<JsonObject> lastConnectionInProject(Project project, List<Long> users, String sortProperty, String sortDirection, Long max, Long offset){
        securityACLService.check(project, WRITE);

//        def match = [project : project.id]
//        def sp = searchParameters.find{it.operator.equals("in") && it.property.equals("user")}
//        if(sp) match << [user : [$in :sp.value]]
//
//        def aggregation = [
//                [$match:match],
//                [$sort : ["$sortProperty": sortDirection.equals("desc") ? -1 : 1]],
//                [$group : [_id : '$user', created : [$max :'$created']]],
//                [$skip : offset]
//        ]
//        if(max > 0) aggregation.push([$limit : max])


        List<Bson> matchsFilters = new ArrayList<>();
        matchsFilters.add(match(eq("project", project.getId())));
        if (users != null) {
            matchsFilters.add(match(in("user", users)));
        }

        Bson sort = sort(sortDirection.equals("desc") ? descending(sortProperty) : ascending(sortProperty));

        Bson group = group("$user", Accumulators.max("created", "$created"));

        Bson skip = skip(offset.intValue());

        List<Bson> requests = new ArrayList<>();
        requests.addAll(matchsFilters);
        requests.addAll(List.of(group, sort, skip));

        if (max > 0 ) {
            requests.add(limit(max.intValue()));
        }

        MongoCollection<Document> persistentProjectConnection = mongoClient.getDatabase(DATABASE_NAME).getCollection("persistentProjectConnection");

        List<Document> results = persistentProjectConnection.aggregate(requests)
                .into(new ArrayList<>());
        results.forEach(printDocuments());

        //TODO: bug?...seems that sometimes ProjectConnectionServiceTests.* tests are failing. the sorting on created does not work perfectly (only sort with s, not with ms)?

        return results.stream().map(x -> JsonObject.of("user", x.get("_id"), "created", x.get("created"))).collect(Collectors.toList());
    }



    /**
     * return the last connection in a project by user. If a user (in the userIds array) doesn't have a connection yet, null values will be associated to the user id.
     */
    // Improve : Can be improved if we can do this in mongo directly
    public List<JsonObject> lastConnectionOfGivenUsersInProject(Project project, List<Long> userIds, String sortProperty, String sortDirection, Long max, Long offset){
        List<JsonObject> results = new ArrayList<>();

        //        def connected = PersistentProjectConnection.createCriteria().list(sort: "user", order: sortDirection) {
//            eq("project", project)
//            projections {
//                Projections.groupProperty("user")
//                property("user")
//            }
//        }

//        Criteria criteria = Criteria.where("project").is(project);
//        criteria.
//        Projection projection = Projections.groupProperty("user");
//        criteria.setProjection(projection);
//        Query query = new Query();
//        query.addCriteria(criteria);
//        mongoTemplate.find(criteria)
//        Criteria criteria = sessionFactory.getCurrentSession().createCriteria(PersistentProjectConnection.class);
//        criteria.add(Restrictions.eq("project", project));

        AggregationResults queryResults = persistentProjectConnectionRepository.retrieve(project.getId(), sortProperty, (sortDirection.equals("desc")? -1 : 1));
        List aggregation = queryResults.getMappedResults();
        List<Long> connected = (List<Long>) aggregation.stream().map(x -> x instanceof LinkedHashMap ? (Long)((LinkedHashMap)x).get("user") : (Long)((PersistentProjectConnection)x).getUser()).distinct().collect(Collectors.toList());

        List<Long> unconnectedIds =  new ArrayList<>(userIds);
        unconnectedIds.removeAll(connected);

        List<JsonObject> unconnected = unconnectedIds.stream().map(x -> JsonObject.of("user", (Object)x)).collect(Collectors.toList());

        if(max == 0) {
            max = unconnected.size() + connected.size() - offset;
        }

        if(sortDirection.equals("desc")){
             //if o+l <= #connected ==> return connected with o et l
            // if o+l > #c c then return connected with o et l and append enough "nulls"

            if(offset < connected.size()) {
                results = lastConnectionInProject(project, null, sortProperty, sortDirection, max, offset);
            }
            int maxOfUnconnected = (int)Math.max(max - results.size(),0);
            int offsetOfUnconnected = (int)Math.max(offset - connected.size(),0);
            if (maxOfUnconnected > 0 ) {
                results.addAll(unconnected.subList(offsetOfUnconnected,offsetOfUnconnected+maxOfUnconnected));
            }
        } else {
            if(offset + max <= unconnected.size()){
                results = unconnected.subList(offset.intValue(),(int)(offset+max));
            }
            else if(offset + max > unconnected.size() && offset <= unconnected.size()) {
                results = unconnected.subList(offset.intValue(),unconnected.size());
                results.addAll(lastConnectionInProject(project, null, sortProperty, sortDirection, max-(unconnected.size()-offset), 0L));
            } else {
                results.addAll(lastConnectionInProject(project, null, sortProperty, sortDirection, max, offset - unconnected.size()));
            }
        }
        return results;
    }




    private void fillProjectConnection(PersistentProjectConnection connection, Date before){
        Date after = connection.getCreated();
//
//        // collect {it.created.getTime} is really slow. I just want the getTime of PersistentConnection
//        def db = mongo.getDB(noSQLCollectionService.getDatabaseName())
//        def connections = db.persistentConnection.aggregate(
//                [$match: [project: connection.project, user: connection.user, $and : [[created: [$gte: after]],[created: [$lte: before]]]]],
//                [$sort: [created: 1]],
//                [$project: [dateInMillis: [$subtract: ['$created', new Date(0L)]]]]
//        );

        AggregationResults connections = projectConnectionRepository.retrieve(connection.getProject(), connection.getUser(), before, after, new Date(0));
        List aggregation = connections.getMappedResults();

        List<Long> continuousConnections = new ArrayList<>();
        // don't understand why sometime it is LinkedHashMap and sometimes PersistentConnection :-/
        if (!aggregation.isEmpty() && aggregation.get(0) instanceof LinkedHashMap) {
            continuousConnections = (List<Long>)aggregation.stream().map(x ->
                    x instanceof LinkedHashMap ? be.cytomine.utils.DateUtils.computeDateInMillis((Date)((LinkedHashMap) x).get("created")) :
                            be.cytomine.utils.DateUtils.computeDateInMillis((Date)((PersistentConnection) x).getCreated())).collect(Collectors.toList());
        }

        //we calculated the gaps between connections to identify the period of non activity
        List<Long> continuousConnectionIntervals = new ArrayList<>();

        Long first = connection.getCreated().getTime();
        for (Long time : continuousConnections) {
            continuousConnectionIntervals.add(time - first);
            first = time;
        }

        connection.setTime(continuousConnectionIntervals.stream().filter(x -> x < 30000L).reduce(0L, Long::sum));

        if(connection.getTime() == null) {
            connection.setTime(0L);
        }
        // TODO:

        // count viewed images
        List<Long> images = imageConsultationService.getImagesOfUsersByProjectBetween(connection.getUser(), connection.getProject(), after, before).stream().map(x -> ((Long) x.get("image"))).distinct().collect(Collectors.toList());
        connection.setCountViewedImages(images.size());

        AnnotationListing al = new UserAnnotationListing(entityManager);
        al.setProject(connection.getProject());
        al.setUser(connection.getUser());
        al.setBeforeThan(before);
        al.setAfterThan(after);

        // count created annotations
        connection.setCountCreatedAnnotations(annotationListingService.listGeneric(al).size());
        persistentProjectConnectionRepository.save(connection);
    }

    public Page<PersistentProjectConnection> getConnectionByUserAndProject(SecUser user, Project project, Integer limit, Integer offset){
        securityACLService.check(project,WRITE);
        if (limit==0) {
            limit = Integer.MAX_VALUE;
        }

        Page<PersistentProjectConnection> results = persistentProjectConnectionRepository.findAllByUserAndProject(user.getId(), project.getId(), PageRequest.of(offset, limit, Sort.Direction.DESC, "created"));
        List<PersistentProjectConnection> connections = new ArrayList<PersistentProjectConnection>(results.getContent());
        if(connections.size() == 0) {
            return Page.empty();
        }

        if(connections.get(0).getTime()==null) {
            connections.set(0, ((PersistentProjectConnection)(connections.get(0)).clone()));
            boolean online = !lastConnectionRepository.findByProjectAndUser(project.getId(), user.getId()).isEmpty();
            fillProjectConnection(connections.get(0), new Date());
            if(online) {
                connections.get(0).getExtraProperties().put("online", true);
            }
        }
        return new PageImpl<>(connections, Pageable.ofSize(limit), results.getTotalElements());
    }

    public JsonObject numberOfConnectionsByProjectAndUser(Project project, User user) {
        securityACLService.check(project,WRITE);
        long rows = persistentProjectConnectionRepository.countAllByProjectAndUser(project.getId(), user.getId());
        return  JsonObject.of("user", user.getId(), "frequency", rows);
    }

    public List<JsonObject> numberOfConnectionsByProjectAndUser(Project project, List<Long> users, String sortProperty, String sortDirection, Long max, Long offset) {
        securityACLService.check(project,WRITE);

          // what we want
          // db.persistentProjectConnection.aggregate([{$match: {project : ID_PROJECT}}, { $group : { _id : {user:"$user"} , number : { $sum : 1 }}}])

        //            def aggregation = [
//                    [$match : match],
//                    [$group : [_id : [ user: '$user'], "frequency":[$sum:1]]],
//                    [$sort : ["$sortProperty": sortDirection.equals("desc") ? -1 : 1]],
//                    [$skip : offset]
//            ]
            List<Bson> matchsFilters = new ArrayList<>();
            matchsFilters.add(match(eq("project", project.getId())));
            if (users != null) {
                matchsFilters.add(match(in("user", users)));
            }

            Bson sort = sort(sortDirection.equals("desc") ? descending(sortProperty) : ascending(sortProperty));

            Bson group = group("$user", Accumulators.sum("frequency", 1), Accumulators.max("created", "$created"));

            Bson skip = skip(offset.intValue());

            List<Bson> requests = new ArrayList<>();
            requests.addAll(matchsFilters);
            requests.addAll(List.of(group, sort, skip));

            if (max > 0 ) {
                requests.add(limit(max.intValue()));
            }

            MongoCollection<Document> persistentProjectConnection = mongoClient.getDatabase(DATABASE_NAME).getCollection("persistentProjectConnection");

            List<Document> results = persistentProjectConnection.aggregate(requests)
                    .into(new ArrayList<>());
            results.forEach(printDocuments());

            return results.stream().map(x -> JsonObject.of("user", x.get("_id"), "frequency", x.get("frequency"))).collect(Collectors.toList());
    }

    /**
     * return the number of project connections by user in a Project. If a user (in the userIds array) doesn't have a connection yet, null values will be associated to the user id.
     */
    public List<JsonObject>  numberOfConnectionsOfGivenByProject(Project project, List<Long> userIds, String sortProperty, String sortDirection, Long max, Long offset){
        List<JsonObject> results = new ArrayList<>();

        MongoCollection<Document> persistentProjectConnection = mongoClient.getDatabase(DATABASE_NAME).getCollection("persistentProjectConnection");
        List<Document> requestResults = persistentProjectConnection.
                aggregate(List.of(Document.parse("{$match: {project: "+project.getId()+"}}"),Document.parse("{$group: {_id : '$user', created : {$max :'$created'}}}"), Document.parse("{$sort: {"+sortProperty+": "+(sortDirection.equals("desc")? -1 : 1)+"}}")))
                .into(new ArrayList<>());
        requestResults.forEach(printDocuments());

//
//        AggregationResults queryResults = persistentProjectConnectionRepository.retrieve(project.getId(), sortProperty, (sortDirection.equals("desc")? -1 : 1));
        List<Long> connected = requestResults.stream().map(x -> (Long)x.get("_id")).collect(Collectors.toList());

        List<Long> unconnectedIds =  new ArrayList<>(userIds);
        unconnectedIds.removeAll(connected);

        List<JsonObject> unconnected = unconnectedIds.stream().map(x -> JsonObject.of("user", (Object)x)).collect(Collectors.toList());

        if(max == 0) {
            max = unconnected.size() + connected.size() - offset;
        }

        if(sortDirection.equals("desc")){
            //if o+l <= #connected ==> return connected with o et l
            // if o+l > #c c then return connected with o et l and append enough "nulls"

            if(offset < connected.size()) {
                results = numberOfConnectionsByProjectAndUser(project, null, sortProperty, sortDirection, max, offset);
            }
            int maxOfUnconnected = (int)Math.max(max - results.size(),0);
            int offsetOfUnconnected = (int)Math.max(offset - connected.size(),0);
            if (maxOfUnconnected > 0 ) {
                results.addAll(unconnected.subList(offsetOfUnconnected,Math.min(offsetOfUnconnected+maxOfUnconnected, unconnected.size())));
            }
        } else {
            if(offset + max <= unconnected.size()){
                results = unconnected.subList(offset.intValue(),(int)(offset+max));
            }
            else if(offset + max > unconnected.size() && offset <= unconnected.size()) {
                results = unconnected.subList(offset.intValue(),unconnected.size());
                results.addAll(numberOfConnectionsByProjectAndUser(project, null, sortProperty, sortDirection, max-(unconnected.size()-offset), 0L));
            } else {
                results.addAll(numberOfConnectionsByProjectAndUser(project, null, sortProperty, sortDirection, max, offset - unconnected.size()));
            }
        }
        return results;
    }


    public List<JsonObject> totalNumberOfConnectionsByProject(){
        securityACLService.checkAdmin(currentUserService.getCurrentUser());

        List<JsonObject> projectConnections = new ArrayList<>();

        // what we want
        // db.persistentProjectConnection.aggregate([{ $group : { _id : {project:"$project"} , total : { $sum : 1 }}}])
        AggregationResults aggregationResults = persistentProjectConnectionRepository.countConnectionByProject();
        List<Document> results = (List<Document>)aggregationResults.getRawResults().get("results");
        for (Document result : results) {
            projectConnections.add(JsonObject.of("project", result.get("_id"), "total", result.get("total")));
        }
        return projectConnections;
    }


    public List<JsonObject> numberOfConnectionsByProjectOrderedByHourAndDays(Project project, Long afterThan, SecUser user) {

        securityACLService.check(project, WRITE);
        // what we want
        //db.persistentProjectConnection.aggregate( {"$match": {$and: [{project : ID_PROJECT}, {created : {$gte : new Date(AFTER) }}]}}, { "$project": { "created": {  "$subtract" : [  "$created",  {  "$add" : [  {"$millisecond" : "$created"}, { "$multiply" : [ {"$second" : "$created"}, 1000 ] }, { "$multiply" : [ {"$minute" : "$created"}, 60, 1000 ] } ] } ] } }  }, { "$project": { "y":{"$year":"$created"}, "m":{"$month":"$created"}, "d":{"$dayOfMonth":"$created"}, "h":{"$hour":"$created"}, "time":"$created" }  },  { "$group":{ "_id": { "year":"$y","month":"$m","day":"$d","hour":"$h"}, time:{"$first":"$time"},  "total":{ "$sum": 1}  }});

        Bson projection1 = Document.parse(
                "{$project : { created : {$subtract:['$created', {$add : [{$millisecond : '$created'}, {$multiply : [{$second : '$created'}, 1000]}, {$multiply : [{$minute : '$created'}, 60000]} ]}]}}}");
        Bson projection2 = Document.parse(
                "{$project : { y : {$year:'$created'}, m : {$month:'$created'}, d : {$dayOfMonth:'$created'}, h : {$hour:'$created'}, time : '$created'}}");
        Bson group = Document.parse(
                "{$group : {_id : { year: '$y', month: '$m', day: '$d', hour: '$h'}, \"time\":{$first:'$time'}, \"frequency\":{$sum:1}}}");

        Bson match = match(eq("project", project.getId()));
        if (afterThan != null) {
            match = match(and(gte("created", new Date(afterThan)), eq("project", project.getId())));
        }

        List<Bson> requests = List.of(match, projection1, projection2, group);

        MongoCollection<Document> persistentProjectConnection = mongoClient.getDatabase(DATABASE_NAME).getCollection("persistentProjectConnection");

        List<Document> results = persistentProjectConnection.aggregate(requests)
                .into(new ArrayList<>());
        results.forEach(printDocuments());

        List<JsonObject> connections = new ArrayList<>();
        for (Document result : results) {
            // TODO evolve when https://jira.mongodb.org/browse/SERVER-6310 is resolved
            // as we groupBy hours in UTC, the GMT + xh30 have problems.

            /*def year = it["_id"]["year"]
            def month = it["_id"]["month"]
            def day = it["_id"]["day"]
            def hour = it["_id"]["hour"]*/
//            def time = it["time"]
//            def frequency = it["frequency"]


            /*Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.DAY_OF_MONTH, day);
            cal.set(Calendar.MONTH, month);*/

//            connections << [/*year: year, month: month, day: day, hour: hour, */time : time, frequency: frequency]

            connections.add(JsonObject.of("time", result.get("time"), "frequency", result.get("frequency")));
        }
        return connections;
    }

    public Long countByProject(Project project, Long startDate, Long endDate) {
        if (startDate==null && endDate==null) {
            return persistentProjectConnectionRepository.countByProject(project.getId());
        } else if (endDate==null) {
            return persistentProjectConnectionRepository.countByProjectAndCreatedAfter(project.getId(), new Date(startDate));
        } else if (startDate==null) {
            return persistentProjectConnectionRepository.countByProjectAndCreatedBefore(project.getId(), new Date(endDate));
        } else {
            return persistentProjectConnectionRepository.countByProjectAndCreatedBetween(project.getId(), new Date(startDate), new Date(endDate));
        }
    }


    public List<JsonObject> numberOfProjectConnections(String period, Long afterThan, Long beforeThan, Project project, SecUser user){
        if (user==null && project==null) {
            securityACLService.checkAdmin(currentUserService.getCurrentUser());
        } else if(project!=null) {
            securityACLService.check(project, ADMINISTRATION);
        } else if(user!=null) {
            securityACLService.checkIsSameUser(user, currentUserService.getCurrentUser());
        }

        if (beforeThan == null) {
            beforeThan = new Date().getTime();
        }
        // what we want
        //db.persistentProjectConnection.aggregate( {"$match": {$and: [{project : ID_PROJECT}, {created : {$gte : new Date(AFTER) }}]}}, { "$project": { "created": {  "$subtract" : [  "$created",  {  "$add" : [  {"$millisecond" : "$created"}, { "$multiply" : [ {"$second" : "$created"}, 1000 ] }, { "$multiply" : [ {"$minute" : "$created"}, 60, 1000 ] } ] } ] } }  }, { "$project": { "y":{"$year":"$created"}, "m":{"$month":"$created"}, "d":{"$dayOfMonth":"$created"}, "h":{"$hour":"$created"}, "time":"$created" }  },  { "$group":{ "_id": { "year":"$y","month":"$m","day":"$d","hour":"$h"}, time:{"$first":"$time"},  "total":{ "$sum": 1}  }});

        List<Bson> matchs = new ArrayList<>();
        Bson projection1 = null;
        Bson projection2 = null;
        Bson group = null;

        if(period==null) {
            period = "hour";
        }

        switch (period){
            case "hour" :
                //substract all minutes,seconds & milliseconds (last unit is hour)
                projection1 = Document.parse(
                        "{$project : { created : {$subtract:['$created', {$add : [{$millisecond : '$created'}, {$multiply : [{$second : '$created'}, 1000]}, {$multiply : [{$minute : '$created'}, 60000]} ]}]}}}");
                projection2 = Document.parse(
                        "{$project : { y : {$year:'$created'}, m : {$month:'$created'}, d : {$dayOfMonth:'$created'}, h : {$hour:'$created'}, time : '$created'}}");
                group = Document.parse(
                        "{$group : {_id : { year: '$y', month: '$m', day: '$d', hour: '$h'}, \"time\":{$first:'$time'}, \"frequency\":{$sum:1}}}");
                break;
            case "day" :
                //also substract hours (last unit is day)
                projection1 = Document.parse(
                        "{$project : { created : {$subtract:['$created', {$add : [{$millisecond : '$created'}, {$multiply : [{$second : '$created'}, 1000]}, {$multiply : [{$minute : '$created'}, 60000]}, {$multiply : [{$hour : '$created'}, 3600000]}]}]}}}");
                projection2 = Document.parse(
                        "{$project : { y : {$year:'$created'}, m : {$month:'$created'}, d : {$dayOfMonth:'$created'}, time : '$created'}}");
                group = Document.parse(
                        "{$group : {_id : { year: '$y', month: '$m', day: '$d'}, \"time\":{$first:'$time'}, \"frequency\":{$sum:1}}}");
                break;
            case "week" :
                //also substract days (last unit is week)
                projection1 = Document.parse(
                        "{$project : { created : {$subtract:['$created', {$add : [{$millisecond : '$created'}, {$multiply : [{$second : '$created'}, 1000]}, {$multiply : [{$minute : '$created'}, 60000]}, {$multiply : [{$hour : '$created'}, 3600000]},  {$multiply : [{$subtract : [{$dayOfWeek: '$created'}, 1]}, 86400000]}       ]}]}}}");
                projection2 = Document.parse(
                        "{$project : { y : {$year:'$created'}, m : {$month:'$created'}, w : {$week:'$created'}, time : '$created'}}");
                group = Document.parse(
                        "{$group : {_id : { year: '$y', month: '$m', week: '$w'}, \"time\":{$first:'$time'}, \"frequency\":{$sum:1}}}");
                break;
        }

        if(afterThan!=null) {
            matchs.add(match(gte("created", new Date(afterThan))));
        }
        if(beforeThan!=null) {
            matchs.add(match(lte("created", new Date(beforeThan))));
        }
        if(project!=null){
            matchs.add(match(eq("project", project.getId())));
        }
        if(user!=null){
            matchs.add(match(eq("user", user.getId())));
        }

        List<Bson> requests = new ArrayList<>();
        requests.addAll(matchs);
        requests.addAll(List.of(projection1, projection2, group));

        MongoCollection<Document> persistentProjectConnection = mongoClient.getDatabase(DATABASE_NAME).getCollection("persistentProjectConnection");

        List<Document> results = persistentProjectConnection.aggregate(requests)
                .into(new ArrayList<>());

        List<JsonObject> connections = new ArrayList<>();
        for (Document result : results) {
            // TODO evolve when https://jira.mongodb.org/browse/SERVER-6310 is resolved
            // as we groupBy hours in UTC, the GMT + xh30 have problems.
            connections.add(JsonObject.of("time", result.get("time"), "frequency", result.get("frequency")));

        }
        return connections;
    }


    public List<JsonObject> averageOfProjectConnections(String period, Long afterThan, Long beforeThan, Project project, SecUser user){
        if (project != null) {
            securityACLService.check(project,READ);
            if (user!= null) {
                securityACLService.checkIsSameUserOrAdminContainer(project, user, currentUserService.getCurrentUser());
            }
        } else {
            securityACLService.checkAdmin(currentUserService.getCurrentUser());
        }


        if (beforeThan == null) {
            beforeThan = new Date().getTime();
        }
        if(afterThan==null){
            afterThan = DateUtils.addYears(new Date(beforeThan), -1).getTime();
        }

        // what we want: db.persistentProjectConnection.aggregate( {"$match": {$and: [{project : ID_PROJECT}, {created : {$gte : new Date(AFTER) }}]}}, { "$project": { "created": {  "$subtract" : [  "$created",  {  "$add" : [  {"$millisecond" : "$created"}, { "$multiply" : [ {"$second" : "$created"}, 1000 ] }, { "$multiply" : [ {"$minute" : "$created"}, 60, 1000 ] } ] } ] } }  }, { "$project": { "y":{"$year":"$created"}, "m":{"$month":"$created"}, "d":{"$dayOfMonth":"$created"}, "h":{"$hour":"$created"}, "time":"$created" }  },  { "$group":{ "_id": { "year":"$y","month":"$m","day":"$d","hour":"$h"}, time:{"$first":"$time"},  "total":{ "$sum": 1}  }});

        List<Bson> matchs = new ArrayList<>();
        Bson projection1 = null;
        Bson projection2 = null;
        Bson group = null;

        if(period==null) {
            period = "hour";
        }

        switch (period){
            case "hour" :
                //substract all minutes,seconds & milliseconds (last unit is hour)
                projection1 = Document.parse(
                        "{$project : { created : {$subtract:['$created', {$add : [{$millisecond : '$created'}, {$multiply : [{$second : '$created'}, 1000]}, {$multiply : [{$minute : '$created'}, 60000]} ]}]}}}");
                projection2 = Document.parse(
                        "{$project : { h : {$hour:'$created'}, time : '$created'}}");
                group = Document.parse(
                        "{$group : {_id : { hour: '$h'}, \"time\":{$first:'$time'}, \"frequency\":{$sum:1}}}");
                break;
            case "day" :
                //also substract hours (last unit is day)
                projection1 = Document.parse(
                        "{$project : { created : {$subtract:['$created', {$add : [{$millisecond : '$created'}, {$multiply : [{$second : '$created'}, 1000]}, {$multiply : [{$minute : '$created'}, 60000]}, {$multiply : [{$hour : '$created'}, 3600000]}]}]}}}");
                projection2 = Document.parse(
                        "{$project : { d : {$dayOfMonth:'$created'}, time : '$created'}}");
                group = Document.parse(
                        "{$group : {_id : { day: '$d'}, \"time\":{$first:'$time'}, \"frequency\":{$sum:1}}}");
                break;
            case "week" :
                //also substract days (last unit is week)
                projection1 = Document.parse(
                        "{$project : { created : {$subtract:['$created', {$add : [{$millisecond : '$created'}, {$multiply : [{$second : '$created'}, 1000]}, {$multiply : [{$minute : '$created'}, 60000]}, {$multiply : [{$hour : '$created'}, 3600000]},  {$multiply : [{$subtract : [{$dayOfWeek: '$created'}, 1]}, 86400000]}       ]}]}}}");
                projection2 = Document.parse(
                        "{$project : { w : {$week:'$created'}, time : '$created'}}");
                group = Document.parse(
                        "{$group : {_id : { week: '$w'}, \"time\":{$first:'$time'}, \"frequency\":{$sum:1}}}");
                break;
        }

        matchs.add(match(gte("created", new Date(afterThan))));
        matchs.add(match(lte("created", new Date(beforeThan))));

        if(project!=null){
            matchs.add(match(eq("project", project.getId())));
        }
        if(user!=null){
            matchs.add(match(eq("user", user.getId())));
        }

        List<Bson> requests = new ArrayList<>();
        requests.addAll(matchs);
        requests.addAll(List.of(projection1, projection2, group));

        MongoCollection<Document> persistentProjectConnection = mongoClient.getDatabase(DATABASE_NAME).getCollection("persistentProjectConnection");

        List<Document> results = persistentProjectConnection.aggregate(requests)
                .into(new ArrayList<>());

        Integer total = results.stream().map(x -> x.get("frequency",0)).reduce(0, Integer::sum);
        if (total == 0L) {
            total = 1;
        }

        List<JsonObject> connections = new ArrayList<>();
        for (Document result : results) {
            // TODO evolve when https://jira.mongodb.org/browse/SERVER-6310 is resolved
            // as we groupBy hours in UTC, the GMT + xh30 have problems.
            connections.add(JsonObject.of("time", result.get("time"), "frequency", ((Integer)result.get("frequency")).doubleValue()/total.doubleValue()));

        }
        return connections;
    }


    public List<PersistentImageConsultation> getUserActivityDetails(Long activityId){
        PersistentProjectConnection connection = persistentProjectConnectionRepository.findById(activityId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("PersistentProjectConnection", activityId));

        Project project = projectRepository.getById(connection.getProject());
        securityACLService.check(project,WRITE);

        List<PersistentImageConsultation> consultations = persistentImageConsultationRepository
                .findAllByCreatedGreaterThanAndProjectConnectionOrderByCreatedDesc(connection.getCreated(), activityId);

        if(consultations.size() == 0) {
            return consultations;
        }
        // current connection. We need to calculate time for the currently opened image
        if(connection.getTime()==null) {
            int i = 0;
            Date before = new Date();
            while(i < consultations.size() && consultations.get(i).getTime()==null) {
                consultations.set(i,(PersistentImageConsultation)(consultations.get(i)).clone());
                imageConsultationService.fillImageConsultation(consultations.get(i), before);
                before = consultations.get(i).getCreated();
                i++;
            }
        }
        return consultations;
    }

    private void closeLastProjectConnection(Long user, Long project, Date before){
        Optional<PersistentProjectConnection> connection =
                persistentProjectConnectionRepository.findAllByUserAndProjectAndCreatedLessThan(user, project, before,
                        PageRequest.of(0, 1, Sort.Direction.DESC, "created")).stream().findFirst();

        //first connection
        if(connection.isEmpty()) {
            return;
        }

        //last connection already closed
        if(connection.get().getTime()!=null) {
            return;
        }
        fillProjectConnection(connection.get(), before);

        persistentProjectConnectionRepository.save(connection.get());
    }



    private static Consumer<Document> printDocuments() {
        return doc -> System.out.println(doc.toJson(JsonWriterSettings.builder().indent(true).build()));
    }
}
