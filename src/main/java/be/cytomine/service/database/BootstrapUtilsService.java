package be.cytomine.service.database;

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

import be.cytomine.config.ApplicationConfiguration;
import be.cytomine.domain.image.Mime;
import be.cytomine.domain.image.server.MimeImageServer;
import be.cytomine.domain.meta.Configuration;
import be.cytomine.domain.meta.ConfigurationReadingRole;
import be.cytomine.domain.middleware.ImageServer;
import be.cytomine.domain.processing.ImageFilter;
import be.cytomine.domain.processing.ImagingServer;
import be.cytomine.domain.processing.ParameterConstraint;
import be.cytomine.domain.processing.SoftwareUserRepository;
import be.cytomine.domain.security.*;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.image.MimeRepository;
import be.cytomine.repository.meta.ConfigurationRepository;
import be.cytomine.repository.middleware.AmqpQueueRepository;
import be.cytomine.repository.middleware.ImageServerRepository;
import be.cytomine.repository.middleware.MessageBrokerServerRepository;
import be.cytomine.repository.ontology.RelationRepository;
import be.cytomine.repository.processing.ImageFilterRepository;
import be.cytomine.repository.processing.ImagingServerRepository;
import be.cytomine.repository.processing.ParameterConstraintRepository;
import be.cytomine.repository.processing.ProcessingServerRepository;
import be.cytomine.repository.security.SecRoleRepository;
import be.cytomine.repository.security.SecUserRepository;
import be.cytomine.repository.security.SecUserSecRoleRepository;
import be.cytomine.repository.security.UserRepository;
import be.cytomine.service.amqp.AmqpQueueConfigService;
import be.cytomine.service.image.server.StorageService;
import be.cytomine.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@Transactional
public class BootstrapUtilsService {

    @Autowired
    SecRoleRepository secRoleRepository;

    @Autowired
    SecUserSecRoleRepository secUserSecRoleRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ApplicationConfiguration applicationConfiguration;

    @Autowired
    StorageService storageService;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    RelationRepository relationRepository;

    @Autowired
    ImageFilterRepository imageFilterRepository;

    @Autowired
    MimeRepository mimeRepository;
    
    @Autowired
    ImageServerRepository imageServerRepository;
    
    @Autowired
    ConfigurationRepository configurationRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    ProcessingServerRepository processingServerRepository;

    @Autowired
    ImagingServerRepository imagingServerRepository;

    @Autowired
    MessageBrokerServerRepository messageBrokerServerRepository;

    @Autowired
    AmqpQueueRepository amqpQueueRepository;

    @Autowired
    AmqpQueueConfigService amqpQueueConfigService;

    @Autowired
    SecUserRepository secUserRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ParameterConstraintRepository parameterConstraintRepository;

    public void createRole(String role) {
        secRoleRepository.createIfNotExist(role);
    }

    public void createUser(String username, String firstname, String lastname, String email, String password, List<String> roles) {
        if (userRepository.findByUsernameLikeIgnoreCase(username).isEmpty()) {
            log.info("Creating {}...", username);
            User user = new User();
            user.setUsername(username);
            user.setFirstname(firstname);
            user.setLastname(lastname);
            user.setEmail(email);
            user.setPassword(password);
            user.encodePassword(passwordEncoder);
            user.setLanguage(Language.valueOf(applicationConfiguration.getDefaultLanguage()));
            user.setEnabled(true);
            user.setIsDeveloper(false);
            user.setOrigin("BOOTSTRAP");
            user.generateKeys();

            log.info("Saving {}...", user.getUsername());
            user = userRepository.save(user);

            for (String role : roles) {
                SecUserSecRole secUserSecRole = new SecUserSecRole();
                secUserSecRole.setSecRole(secRoleRepository.getByAuthority(role));
                secUserSecRole.setSecUser(user);
                secUserSecRoleRepository.save(secUserSecRole);
            }

            //SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "admin"));
            SecurityUtils.reauthenticate(applicationContext, "admin", "admin");

            storageService.initUserStorage(user);
        }
    }


    public void createUserJob(String username, String password, User creator, List<String> roles) {
        if (secUserRepository.findByUsernameLikeIgnoreCase(username).isEmpty()) {
            log.info("Creating {}...", username);
            UserJob user = new UserJob();
            user.setUsername(username);
            user.setPassword(password);
            user.setEnabled(true);
            user.setOrigin("BOOTSTRAP");
            user.setUser(creator);
            user.generateKeys();
            log.info("Saving {}...", user.getUsername());
            user = secUserRepository.save(user);

            for (String role : roles) {
                SecUserSecRole secUserSecRole = new SecUserSecRole();
                secUserSecRole.setSecRole(secRoleRepository.getByAuthority(role));
                secUserSecRole.setSecUser(user);
                secUserSecRoleRepository.save(secUserSecRole);
            }
//
//            //SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "admin"));
//            SecurityUtils.reauthenticate(applicationContext, "admin", "admin");
//
//            storageService.initUserStorage(user);
        }
    }

    public void createRelation(String name) {
        relationRepository.createIfNotExist(name);
    }

    public void createFilter(String name, String baseUrl, ImagingServer imagingServer) {
        if (imageFilterRepository.findByName(name).isEmpty()) {
            ImageFilter filter = new ImageFilter();
            filter.setName(name);
            filter.setBaseUrl(baseUrl);
            filter.setImagingServer(imagingServer);
            imageFilterRepository.save(filter);
        }
    }

    public void createMime(String extension, String mimeType) {
        if (mimeRepository.findByMimeType(mimeType).isEmpty()) {
            Mime mime = new Mime();
            mime.setMimeType(mimeType);
            mime.setExtension(extension);
            mimeRepository.save(mime);
        }
    }



    public void createConfigurations(String key, String value, ConfigurationReadingRole readingRole){
        Configuration configuration = new Configuration();
        configuration.setKey(key);
        configuration.setValue(value);
        configuration.setReadingRole(readingRole);
        configurationRepository.save(configuration);


//        if(!update) configs << new Configuration(key: "WELCOME", value: "<p>Welcome to the Cytomine software.</p><p>This software is supported by the <a href='https://cytomine.coop'>Cytomine company</a></p>", readingRole: allUsers)
//
//        configs << new Configuration(key: "admin_email", value: grailsApplication.config.grails.admin.email, readingRole: adminRole)
//
//        //SMTP values
//        configs << new Configuration(key: "notification_email", value: grailsApplication.config.grails.notification.email, readingRole: adminRole)
//        configs << new Configuration(key: "notification_password", value: grailsApplication.config.grails.notification.password, readingRole: adminRole)
//        configs << new Configuration(key: "notification_smtp_host", value: grailsApplication.config.grails.notification.smtp.host, readingRole: adminRole)
//        configs << new Configuration(key: "notification_smtp_port", value: grailsApplication.config.grails.notification.smtp.port, readingRole: adminRole)
    }


    public void createMultipleImageServer() {
        for (ImageServer imageServer : imageServerRepository.findAll()) {
            log.info("imageServer '" + imageServer.getUrl() + "'");
            if(!applicationConfiguration.getImageServerURL().contains(imageServer.getUrl())) {
                log.info("ImageServer not in config, disable it");
                imageServer.setAvailable(false);
                imageServerRepository.save(imageServer);
            } else {
                log.info("ImageServer in config");
            }
        }

        for (int i=0; i<applicationConfiguration.getImageServerURL().size(); i++) {
            createImageServer("IMS " + i, applicationConfiguration.getImageServerURL().get(i), applicationConfiguration.getStoragePath());
        }
    }
    
   public void createImageServer(String name, String url, String basePath) {
       log.info("Check if '" + url + "' is in database " + imageServerRepository.findAll().stream().map(ImageServer::getUrl).toList());
       if (!imageServerRepository.findAll().stream().anyMatch(x -> x.getUrl().equals(url))) {
           log.info("ImageServer '" + url + "'  not in database " + imageServerRepository.findAll().stream().map(ImageServer::getUrl).toList());
           ImageServer imageServer = new ImageServer();
           imageServer.setName(name);
           imageServer.setUrl(url);
           imageServer.setBasePath(basePath);
           imageServer.setAvailable(true);
           entityManager.persist(imageServer);
           for (Mime mime : mimeRepository.findAll()) {
               MimeImageServer mimeImageServer = new MimeImageServer();
               mimeImageServer.setImageServer(imageServer);
               mimeImageServer.setMime(mime);
               entityManager.persist(mimeImageServer);
           }

       }
   }

    public ImagingServer createImagingServer() {
        String imageServerURL = applicationConfiguration.getImageServerURL().stream().findFirst()
                .orElseThrow(() -> new ObjectNotFoundException("No image server defined in configuration", ErrorCode.NOT_FOUND_IMAGE_SERVER.getValue()));
        if (imagingServerRepository.findByUrl(imageServerURL).isEmpty()) {
            ImagingServer imagingServer = new ImagingServer();
            imagingServer.setUrl(imageServerURL);
            return imagingServerRepository.save(imagingServer);
        } else {
            return imagingServerRepository.findByUrl(imageServerURL).get();
        }
    }

    public void updateProcessingServerRabbitQueues() {
        // TODO: TRANSLATION DONE, BUT amqpQueueService HAS TO BE IMPLEMENTED
//        for (ProcessingServer processingServer : processingServerRepository.findAll()) {
//            String processingServerName = StringUtils.capitalize(processingServer.getName());
//            String queueName = AmqpQueueService.queuePrefixProcessingServer + processingServerName;
//            // TODO: in grails version we use processingServer.queueName (bug?)
//            amqpQueueService.createAmqpQueueDefault(queueName);
//        }
    }

    public void initRabbitMq() {
        // TODO: TRANSLATION DONE, BUT amqpQueueService HAS TO BE IMPLEMENTED
//        log.info("init RabbitMQ connection...");
//        String messageBrokerURL = applicationConfiguration.getCytomine().getMessageBrokerServerURL();
//        log.info("messageBrokerURL = " + messageBrokerURL);
//        Optional<MessageBrokerServer> firstMessageBrokerServer = messageBrokerServerRepository.findAll().stream().findFirst();
//        boolean toUpdate = false;
//
//        for (MessageBrokerServer messageBroker : messageBrokerServerRepository.findAll()) {
//            if(!messageBrokerURL.equals(messageBroker.getHost()+":"+messageBroker.getPort())) {
//                toUpdate = true;
//                log.info(messageBroker.getHost() + "is not in config, drop it");
//                log.info("delete Message Broker Server " + messageBroker);
//                messageBrokerServerRepository.delete(messageBroker);
//            }
//        }
//
//        MessageBrokerServer messageBrokerServer = firstMessageBrokerServer.orElse(null);
//        String[] splittedURL = messageBrokerURL.split(":");
//        if (toUpdate || firstMessageBrokerServer.isEmpty()) {
//            messageBrokerServer = new MessageBrokerServer();
//            messageBrokerServer.setName("MessageBrokerServer");
//            messageBrokerServer.setHost(splittedURL[0]);
//            messageBrokerServer.setPort(Integer.parseInt(splittedURL[1]));
//            messageBrokerServerRepository.save(messageBrokerServer);
//
//            for (AmqpQueue amqpQueue : amqpQueueRepository.findAll()) {
//                amqpQueue.setHost(messageBrokerServer.getHost());
//                amqpQueueRepository.save(amqpQueue);
//                if(!amqpQueueService.checkRabbitQueueExists("queueCommunication", messageBrokerServer)) {
//                    AmqpQueue queueCommunication = amqpQueueService.read("queueCommunication");
//                    amqpQueueService.createAmqpQueueDefault(queueCommunication);
//                }
//            }
//
//        }
//        // Initialize default configurations for amqp queues
//        amqpQueueConfigService.initAmqpQueueConfigDefaultValues();
//
//        // Initialize RabbitMQ queue to communicate software added
//        if(amqpQueueRepository.findByName("queueCommunication").isEmpty()) {
//            AmqpQueue queueCommunication = new AmqpQueue();
//            queueCommunication.setName("queueCommunication");
//            queueCommunication.setHost(messageBrokerServer.getHost());
//            queueCommunication.setExchange("exchangeCommunication");
//            amqpQueueRepository.save(queueCommunication);
//            amqpQueueService.createAmqpQueueDefault(queueCommunication);
//        } else if(!amqpQueueService.checkRabbitQueueExists("queueCommunication", messageBrokerServer)) {
//            AmqpQueue queueCommunication = amqpQueueService.read("queueCommunication");
//            amqpQueueService.createAmqpQueueDefault(queueCommunication);
//        }
//        updateProcessingServerRabbitQueues();
//
//        if (EnvironmentUtils.isTest(environment)) {
//            rabbitConnectionService.getRabbitConnection(messageBrokerServer);
//        }


    }

    // TODO: required?

//    def mongo
//    def noSQLCollectionService
//    def imageConsultationService
//    void fillProjectConnections() {
//        SpringSecurityUtils.doWithAuth("superadmin", {
//                Date before = new Date();
//
//        def connections = PersistentProjectConnection.findAllByTimeIsNullOrCountCreatedAnnotationsIsNullOrCountViewedImagesIsNull(sort: 'created', order: 'desc', max: Integer.MAX_VALUE)
//        log.info "project connections to update " + connections.size()
//
//        def sql = new Sql(dataSource)
//
//        for (PersistentProjectConnection projectConnection : connections) {
//            Date after = projectConnection.created;
//
//            // collect {it.created.getTime} is really slow. I just want the getTime of PersistentConnection
//            def db = mongo.getDB(noSQLCollectionService.getDatabaseName())
//            def lastConnection = db.persistentConnection.aggregate(
//                    [$match: [project: projectConnection.project, user: projectConnection.user, $and : [[created: [$gte: after]],[created: [$lte: before]]]]],
//                        [$sort: [created: 1]],
//                        [$project: [dateInMillis: [$subtract: ['$created', new Date(0L)]]]]
//                );
//
//            def continuousConnections = lastConnection.results().collect { it.dateInMillis }
//
//            //we calculate the gaps between connections to identify the period of non activity
//            def continuousConnectionIntervals = []
//
//            continuousConnections.inject(projectConnection.created.time) { result, i ->
//                    continuousConnectionIntervals << (i-result)
//                i
//            }
//
//            projectConnection.time = continuousConnectionIntervals.split{it < 30000}[0].sum()
//            if(projectConnection.time == null) projectConnection.time=0;
//
//            // count viewed images
//            projectConnection.countViewedImages = imageConsultationService.getImagesOfUsersByProjectBetween(projectConnection.user, projectConnection.project,after, before).size()
//
//            db.persistentImageConsultation.update(
//                    [$and :[ [project:projectConnection.project],[user:projectConnection.user],[created:[$gte:after]],[created:[$lte:before]]]],
//                        [$set: [projectConnection: projectConnection.id]])
//
//            // count created annotations
//            String request = "SELECT COUNT(*) FROM user_annotation a WHERE a.project_id = ${projectConnection.project} AND a.user_id = ${projectConnection.user} AND a.created < '${before}' AND a.created > '${after}'"
//
//            sql.eachRow(request) {
//                projectConnection.countCreatedAnnotations = it[0];
//            }
//
//            projectConnection.save(flush : true, failOnError: true)
//            before = projectConnection.created
//        }
//        sql.close()
//        });
//    }
//    void fillImageConsultations() {
//        SpringSecurityUtils.doWithAuth("superadmin", {
//                Date before = new Date();
//
//        def consultations = PersistentImageConsultation.findAllByTimeIsNullOrCountCreatedAnnotationsIsNull(sort: 'created', order: 'desc', max: Integer.MAX_VALUE)
//        log.info "image consultations to update " + consultations.size()
//
//        def sql = new Sql(dataSource)
//
//        for (PersistentImageConsultation consultation : consultations) {
//            Date after = consultation.created;
//
//            // collect {it.created.getTime} is really slow. I just want the getTime of PersistentConnection
//            def db = mongo.getDB(noSQLCollectionService.getDatabaseName())
//            def positions = db.persistentUserPosition.aggregate(
//                    [$match: [project: consultation.project, user: consultation.user, image: consultation.image, $and : [[created: [$gte: after]],[created: [$lte: before]]]]],
//                        [$sort: [created: 1]],
//                        [$project: [dateInMillis: [$subtract: ['$created', new Date(0L)]]]]
//                );
//
//            def continuousConnections = positions.results().collect { it.dateInMillis }
//
//            //we calculate the gaps between connections to identify the period of non activity
//            def continuousConnectionIntervals = []
//
//            continuousConnections.inject(consultation.created.time) { result, i ->
//                    continuousConnectionIntervals << (i-result)
//                i
//            }
//
//            consultation.time = continuousConnectionIntervals.split{it < 30000}[0].sum()
//            if(consultation.time == null) consultation.time=0;
//
//            // count created annotations
//            String request = "SELECT COUNT(*) FROM user_annotation a WHERE " +
//                    "a.project_id = ${consultation.project} " +
//                    "AND a.user_id = ${consultation.user} " +
//                    "AND a.image_id = ${consultation.image} " +
//                    "AND a.created < '${before}' AND a.created > '${after}'"
//
//            sql.eachRow(request) {
//                consultation.countCreatedAnnotations = it[0];
//            }
//
//            consultation.save(flush : true, failOnError: true)
//            before = consultation.created
//        }
//        sql.close()
//        });
//    }



    public void addDefaultProcessingServer() {
//        log.info("Add the default processing server");
//        // TODO: SpringSecurityUtils.doWithAuth { !!! ????!!!
//
//        if (processingServerRepository.findByName("local-server").isEmpty()) {
//            ProcessingServer processingServer = new ProcessingServer();
//            processingServer.setName("local-server");
//            processingServer.setHost("slurm");
//            processingServer.setUsername("cytomine");
//            processingServer.setPort(22);
//            processingServer.setType("cpu");
//            processingServer.setProcessingMethodName("SlurmProcessingMethod");
//            processingServer.setPersistentDirectory(applicationConfiguration.getCytomine().getSoftware().getPath().getSoftwareImages());
//            processingServer.setIndex(1);
//            String processingServerName = StringUtils.capitalize(processingServer.getName());
//            String queueName = amqpQueueService.queuePrefixProcessingServer + processingServerName;
//
//            if (!amqpQueueService.checkAmqpQueueDomainExists(queueName)) {
//                // Creation of the default processing server queue
//                String exchangeName = AmqpQueueService.exchangePrefixProcessingServer + processingServerName;
//                String brokerServerURL = (messageBrokerServerRepository.findByName("MessageBrokerServer"))
//                        .orElseThrow(() -> new ObjectNotFoundException("No message broker server found")).getHost();
//
//                AmqpQueue amqpQueue = new AmqpQueue();
//                amqpQueue.setName(queueName);
//                amqpQueue.setHost(brokerServerURL);
//                amqpQueue.setExchange(exchangeName);
//                amqpQueueRepository.save(amqpQueue);
//
//                amqpQueueService.createAmqpQueueDefault(amqpQueue);
//
//                // Associates the processing server to an amqp queue
//                processingServer.setAmqpQueue(amqpQueue);
//                processingServerRepository.save(processingServer);
//
//                // Sends a message on the communication queue to warn the software router a new queue has been created
//                JsonObject jsonObject = new JsonObject();
//                jsonObject.put("requestType", "addProcessingServer");
//                jsonObject.put("name", amqpQueue.getName());
//                jsonObject.put("host", amqpQueue.getHost());
//                jsonObject.put("exchange", amqpQueue.getExchange());
//                jsonObject.put("processingServerId", processingServer.getId());
//
//                amqpQueueService.publishMessage(amqpQueueRepository.findByName("queueCommunication")
//                        .orElseThrow(() -> new ObjectNotFoundException("amqpQueue queueCommunication not found")), jsonObject.toJsonString());
//
//            }
//        }
    }


    public void addDefaultConstraints() {
        log.info("Add the default constraints");
        // SpringSecurityUtils.doWithAuth {
        List<ParameterConstraint> parameterConstraints = new ArrayList<>();

        //TODO: check if expression can work with java

        log.info("Add Number constraints");
        parameterConstraints.add(new ParameterConstraint("integer", "(\"[value]\".isInteger()", "Number"));
        parameterConstraints.add(new ParameterConstraint("minimum", "(Double.valueOf(\"[parameterValue]\") as Number) <= (Double.valueOf(\"[value]\") as Number)", "Number"));
        parameterConstraints.add(new ParameterConstraint("maximum", "(Double.valueOf(\"[parameterValue]\") as Number) >= (Double.valueOf(\"[value]\") as Number)", "Number"));
        parameterConstraints.add(new ParameterConstraint("equals", "(Double.valueOf(\"[parameterValue]\") as Number) == (Double.valueOf(\"[value]\") as Number)", "Number"));
        parameterConstraints.add(new ParameterConstraint("in", "\"[value]\".tokenize(\"[separator]\").find { elem -> (Double.valueOf(elem) as Number) == (Double.valueOf(\"[parameterValue]\") as Number) } != null", "Number"));

        log.info("Add String constraints");
        parameterConstraints.add(new ParameterConstraint("minimum", "\"[parameterValue]\".length() < [value]", "String"));
        parameterConstraints.add(new ParameterConstraint("maximum", "\"[parameterValue]\".length() > [value]", "String"));
        parameterConstraints.add(new ParameterConstraint("equals", "\"[parameterValue]\" == \"[value]\"", "String"));
        parameterConstraints.add(new ParameterConstraint("in", "\"[value]\".tokenize(\"[separator]\").contains(\"[parameterValue]\")", "String"));

        log.info("Add Boolean constraints");
        parameterConstraints.add(new ParameterConstraint("equals", "Boolean.parseBoolean(\"[value]\") == Boolean.parseBoolean(\"[parameterValue]\")", "Boolean"));

        log.info("Add Date constraints");
        parameterConstraints.add(new ParameterConstraint("minimum", "new Date().parse(\"HH:mm:ss\", \"[parameterValue]\").format(\"HH:mm:ss\") < new Date().parse(\"HH:mm:ss\", \"[value]\").format(\"HH:mm:ss\")", "Date"));
        parameterConstraints.add(new ParameterConstraint("maximum", "new Date().parse(\"HH:mm:ss\", \"[parameterValue]\").format(\"HH:mm:ss\") > new Date().parse(\"HH:mm:ss\", \"[value]\").format(\"HH:mm:ss\")", "Date"));
        parameterConstraints.add(new ParameterConstraint("equals", "new Date().parse(\"HH:mm:ss\", \"[parameterValue]\").format(\"HH:mm:ss\") == new Date().parse(\"HH:mm:ss\", \"[value]\").format(\"HH:mm:ss\")", "Date"));
        parameterConstraints.add(new ParameterConstraint("in", "\"[value]\".tokenize(\"[separator]\").contains(\"[parameterValue]\")", "Date"));

// TODO:; SpringSecurityUtils.doWithAuth { ????
        for (ParameterConstraint parameterConstraint : parameterConstraints) {
            if (parameterConstraintRepository.findByNameAndDataType(parameterConstraint.getName(), parameterConstraint.getDataType()).isEmpty()) {
                parameterConstraintRepository.save(parameterConstraint);
            }
        }
    }

    public void createSoftwareUserRepository(String provider, String username, String dockerUsername, String prefix) {
        SoftwareUserRepository softwareUserRepository = new SoftwareUserRepository();
        softwareUserRepository.setProvider(provider);
        softwareUserRepository.setUsername(username);
        softwareUserRepository.setDockerUsername(dockerUsername);
        softwareUserRepository.setPrefix(prefix);
        entityManager.persist(softwareUserRepository);
    }

}
