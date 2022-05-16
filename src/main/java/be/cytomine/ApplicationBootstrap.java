package be.cytomine;

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
import be.cytomine.config.nosqlmigration.InitialMongodbSetupMigration;
import be.cytomine.domain.ontology.Ontology;
import be.cytomine.domain.ontology.Term;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.image.server.StorageRepository;
import be.cytomine.repository.ontology.OntologyRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.repository.security.SecUserRepository;
import be.cytomine.service.PermissionService;
import be.cytomine.service.UrlApi;
import be.cytomine.service.database.BootstrapDataService;
import be.cytomine.service.database.BootstrapTestsDataService;
import be.cytomine.service.database.BootstrapUtilsService;
import be.cytomine.service.database.SequenceService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.utils.Dataset;
import be.cytomine.utils.EnvironmentUtils;
import be.cytomine.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static be.cytomine.service.database.BootstrapTestsDataService.*;
import static org.springframework.security.acls.domain.BasePermission.ADMINISTRATION;

@Component
@Order(0)
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties({LiquibaseProperties.class,ApplicationConfiguration.class})
@Transactional
class ApplicationBootstrap {

    private final SecUserRepository secUserRepository;

    private final ApplicationConfiguration applicationConfiguration;

    private final Environment environment;

    private boolean initAlreadyDone;


    @Autowired
    BootstrapDataService bootstrapDataService;

    @Autowired
    BootstrapUtilsService bootstrapUtilDataService;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    PermissionService permissionService;

    @Autowired
    InitialMongodbSetupMigration initialSetupMigration;

    @Autowired
    Dataset dataset;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    BootstrapTestsDataService bootstrapTestsDataService;

//    @Override
//    public void onApplicationEvent(ApplicationReadyEvent event) {
//        log.info("ApplicationListener#onApplicationEvent()");
//        if (!initAlreadyDone) {
//            // onApplicationEvent can be called multiple times (if cyclcic dependencies)
//            init();
//        }
//        this.initAlreadyDone = true;
//    }

    @PostConstruct
    public void init() {

        printConfiguration();

        initialSetupMigration.changeSet();

        log.info ("#############################################################################");
        log.info ("#############################################################################");
        log.info ("#############################################################################");
        String cytomineWelcomMessage = "                   _____      _                  _\n" +
                "                  / ____|    | |                (_)\n" +
                "                 | |    _   _| |_ ___  _ __ ___  _ _ __   ___\n" +
                "                 | |   | | | | __/ _ \\| '_ ` _ \\| | '_ \\ / _ \\\n" +
                "                 | |___| |_| | || (_) | | | | | | | | | |  __/\n" +
                "                  \\_____\\__, |\\__\\___/|_| |_| |_|_|_| |_|\\___|\n" +
                "                 |  _ \\  __/ |     | |     | |\n" +
                "                 | |_) ||___/  ___ | |_ ___| |_ _ __ __ _ _ __\n" +
                "                 |  _ < / _ \\ / _ \\| __/ __| __| '__/ _` | '_ \\\n" +
                "                 | |_) | (_) | (_) | |_\\__ \\ |_| | | (_| | |_) |\n" +
                "                 |____/ \\___/ \\___/ \\__|___/\\__|_|  \\__,_| .__/\n" +
                "                                                         | |\n" +
                "                                                         |_|";
        log.info (cytomineWelcomMessage);
        log.info ("#############################################################################");
        log.info ("#############################################################################");
        log.info ("#############################################################################");
        log.info ("Environment:" + Arrays.toString(environment.getActiveProfiles()));
        log.info ("Current directory:" + new File( "./" ).getAbsolutePath());
        log.info ("HeadLess:" + java.awt.GraphicsEnvironment.isHeadless());
        log.info ("JVM Args" + ManagementFactory.getRuntimeMXBean().getInputArguments());
        log.info (applicationConfiguration.toString());
        log.info ("#############################################################################");
        log.info ("#############################################################################");
        log.info ("#############################################################################");

        UrlApi.setServerURL(
                applicationConfiguration.getServerURL(),
                applicationConfiguration.getUseHTTPInternally()
        );

        if (EnvironmentUtils.isTest(environment) && secUserRepository.count() == 0) {
            bootstrapDataService.initData();
            //noSQLCollectionService.cleanActivityDB() TODO:
            bootstrapUtilDataService.createUser(dataset.ANOTHERLOGIN, "Just another", "User", dataset.ADMINEMAIL, dataset.ADMINPASSWORD, List.of("ROLE_USER", "ROLE_ADMIN","ROLE_SUPER_ADMIN"));

            // same as superadmin, but a userjob
            bootstrapUtilDataService.createUserJob("superadminjob", dataset.ADMINPASSWORD,
                    (User)secUserRepository.findByUsernameLikeIgnoreCase("superadmin").orElseThrow(() -> ObjectNotFoundException.notFoundException("User", "superadmin")),
                    List.of("ROLE_USER", "ROLE_ADMIN","ROLE_SUPER_ADMIN"));

            // We need these users for all authorization tests
            // So we create them at the beginning in order to avoid creating them before each authorization tests
            bootstrapTestsDataService.createUserForTests(SUPERADMIN);
            bootstrapTestsDataService.createUserForTests(USER_ACL_READ);
            bootstrapTestsDataService.createUserForTests(USER_ACL_WRITE);
            bootstrapTestsDataService.createUserForTests(USER_ACL_CREATE);
            bootstrapTestsDataService.createUserForTests(USER_ACL_DELETE);
            bootstrapTestsDataService.createUserForTests(USER_ACL_ADMIN);
            bootstrapTestsDataService.createUserForTests(USER_NO_ACL);
            bootstrapTestsDataService.createUserForTests(GUEST);
            bootstrapTestsDataService.createUserForTests(CREATOR);


        } else if (secUserRepository.count() == 0) {
            //if database is empty, put minimal data
            bootstrapDataService.initData();
        }

        if (applicationConfiguration.getImageServerPrivateKey()!=null && applicationConfiguration.getImageServerPublicKey()!=null) {
            SecUser imageServerUser = secUserRepository.findByUsernameLikeIgnoreCase("ImageServer1")
                    .orElseThrow(() -> new ObjectNotFoundException("No user imageserver1, cannot assign keys", ErrorCode.NOT_FOUND_USER.getValue(), Map.of("user", "imageserver1")));
            imageServerUser.setPrivateKey(applicationConfiguration.getImageServerPrivateKey());
            imageServerUser.setPublicKey(applicationConfiguration.getImageServerPublicKey());
            secUserRepository.save(imageServerUser);
        }
        if (applicationConfiguration.getRabbitMQPrivateKey()!=null && applicationConfiguration.getRabbitMQPrivateKey()!=null) {
            secUserRepository.findByUsernameLikeIgnoreCase("rabbitmq")
                    .ifPresent(user -> {
                        user.setPrivateKey(applicationConfiguration.getRabbitMQPrivateKey());
                        user.setPublicKey(applicationConfiguration.getRabbitMQPublicKey());
                            secUserRepository.save(user);
                    });
        }

        File softwareSourceDirectory = new File(applicationConfiguration.getSoftwareSources());
        if (!softwareSourceDirectory.exists() && !softwareSourceDirectory.mkdirs()) {
            log.error("Software Sources folder doesn't exist");
        }

        bootstrapUtilDataService.initRabbitMq();

        log.info("create multiple IS and Retrieval...");
        bootstrapUtilDataService.createMultipleImageServer();
        bootstrapUtilDataService.updateProcessingServerRabbitQueues();
        log.info ("#############################################################################");
        log.info ("###################              READY              #########################");
        log.info ("#############################################################################");

    }

    private void printConfiguration() {
        log.info("Cytomine-core: " + applicationConfiguration.getVersion());
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        log.info("*************** SYSTEM CONFIGURATION ******************");
        log.info("Max memory:" + memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024) + "MB");
        log.info("Used memory:" + memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024) + "MB");
        log.info("Init memory:" + memoryBean.getHeapMemoryUsage().getInit() / (1024 * 1024) + "MB");
        log.info("Default charset: " + Charset.defaultCharset());
//        log.info("*************** JAVA CONFIGURATION ******************");
//        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
//            log.info(entry.getKey() + "=" + entry.getValue());
//        }
        log.info("*************** APPLICATION CONFIGURATION ******************");
        log.info(applicationConfiguration.toString());
         final Environment env = applicationContext.getEnvironment();

        log.info("====== Environment and configuration ======");
        log.info("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
        MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
        StreamSupport.stream(sources.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
                .flatMap(Arrays::stream)
                .distinct()
                .sorted()
                .forEach(prop -> log.info("ACTIVE {}: {}", prop, mustBeObscurify(prop) ? StringUtils.obscurify(env.getProperty(prop), 2) : env.getProperty(prop)));

    }

    private boolean mustBeObscurify(String prop) {
        return (prop.toLowerCase().contains("credentials") || prop.toLowerCase().contains("password") || prop.toLowerCase().contains("privatekey") || prop.toLowerCase().contains("secret"));
    }

//
    @Autowired
    ApplicationContext applicationContext;

//
//    @EventListener
//    public void handleContextRefreshed(ContextRefreshedEvent event) {
//        printActiveProperties((ConfigurableEnvironment) event.getApplicationContext().getEnvironment());
//    }
//
//    private void printActiveProperties(ConfigurableEnvironment env) {
//
//        System.out.println("************************* ACTIVE APP PROPERTIES ******************************");
//
//        List<MapPropertySource> propertySources = new ArrayList<>();
//
//        env.getPropertySources().forEach(it -> {
//            propertySources.add((MapPropertySource) it);
//        });
//
//
//        propertySources.stream()
//                .map(propertySource -> propertySource.getSource().keySet())
//                .flatMap(Collection::stream)
//                .distinct()
//                .sorted()
//                .forEach(key -> {
//                    try {
//                        System.out.println(key + "=" + env.getProperty(key));
//                    } catch (Exception e) {
//                        log.warn("{} -> {}", key, e.getMessage());
//                    }
//                });
//        System.out.println("******************************************************************************");
//    }

}
