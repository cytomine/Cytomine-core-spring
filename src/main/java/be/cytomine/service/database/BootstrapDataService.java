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
import be.cytomine.domain.meta.ConfigurationReadingRole;
import be.cytomine.domain.processing.ImagingServer;
import be.cytomine.domain.security.SecUser;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.security.SecUserRepository;
import be.cytomine.service.amqp.AmqpQueueConfigService;
import be.cytomine.service.utils.Dataset;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static be.cytomine.domain.ontology.RelationTerm.PARENT;

@Service
@Transactional
public class BootstrapDataService {

    @Autowired
    AmqpQueueConfigService amqpQueueConfigService;
    
    @Autowired
    BootstrapUtilsService bootstrapUtilsService;

    @Autowired
    Dataset dataset;

    @Autowired
    SecUserRepository secUserRepository;

    @Autowired
    ApplicationConfiguration applicationConfiguration;

    public void initData() {

        amqpQueueConfigService.initAmqpQueueConfigDefaultValues();

        bootstrapUtilsService.initRabbitMq();

        ImagingServer imagingServer = bootstrapUtilsService.createImagingServer();

        bootstrapUtilsService.createFilter("Binary", "/vision/process?method=binary&url=", imagingServer);
        bootstrapUtilsService.createFilter("Huang Threshold", "/vision/process?method=huang&url=", imagingServer);
        bootstrapUtilsService.createFilter("Intermodes Threshold", "/vision/process?method=intermodes&url=", imagingServer);
        bootstrapUtilsService.createFilter("IsoData Threshold", "/vision/process?method=isodata&url=", imagingServer);
        bootstrapUtilsService.createFilter("Li Threshold", "/vision/process?method=li&url=", imagingServer);
        bootstrapUtilsService.createFilter("Max Entropy Threshold", "/vision/process?method=maxentropy&url=", imagingServer);
        bootstrapUtilsService.createFilter("Mean Threshold", "/vision/process?method=mean&url=", imagingServer);
        bootstrapUtilsService.createFilter("Minimum Threshold", "/vision/process?method=minimum&url=", imagingServer);
        bootstrapUtilsService.createFilter("MinError(I) Threshold", "/vision/process?method=minerror&url=", imagingServer);
        bootstrapUtilsService.createFilter("Moments Threshold", "/vision/process?method=moments&url=", imagingServer);
        bootstrapUtilsService.createFilter("Otsu Threshold", "/vision/process?method=otsu&url=", imagingServer);
        bootstrapUtilsService.createFilter("Renyi Entropy Threshold", "/vision/process?method=renyientropy&url=", imagingServer);
        bootstrapUtilsService.createFilter("Shanbhag Threshold", "/vision/process?method=shanbhag&url=", imagingServer);
        bootstrapUtilsService.createFilter("Triangle Threshold", "/vision/process?method=triangle&url=", imagingServer);
        bootstrapUtilsService.createFilter("Yen Threshold", "/vision/process?method=yen&url=", imagingServer);
        bootstrapUtilsService.createFilter("Percentile Threshold", "/vision/process?method=percentile&url=", imagingServer);
        bootstrapUtilsService.createFilter("H&E Haematoxylin", "/vision/process?method=he-haematoxylin&url=", imagingServer);
        bootstrapUtilsService.createFilter("H&E Eosin", "/vision/process?method=he-eosin&url=", imagingServer);
        bootstrapUtilsService.createFilter("HDAB Haematoxylin", "/vision/process?method=hdab-haematoxylin&url=", imagingServer);
        bootstrapUtilsService.createFilter("HDAB DAB", "/vision/process?method=hdab-dab&url=", imagingServer);
        bootstrapUtilsService.createFilter("Haematoxylin", "/vision/process?method=haematoxylin&url=", imagingServer);
        bootstrapUtilsService.createFilter("Eosin", "/vision/process?method=eosin&url=", imagingServer);
        bootstrapUtilsService.createFilter("Red (RGB)", "/vision/process?method=r_rgb&url=", imagingServer);
        bootstrapUtilsService.createFilter("Green (RGB)", "/vision/process?method=g_rgb&url=", imagingServer);
        bootstrapUtilsService.createFilter("Blue (RGB)", "/vision/process?method=b_rgb&url=", imagingServer);
        bootstrapUtilsService.createFilter("Cyan (CMY)", "/vision/process?method=c_cmy&url=", imagingServer);
        bootstrapUtilsService.createFilter("Magenta (CMY)", "/vision/process?method=m_cmy&url=", imagingServer);
        bootstrapUtilsService.createFilter("Yellow (CMY)", "/vision/process?method=y_cmy&url=", imagingServer);
        
        
        bootstrapUtilsService.createMime("tif", "image/pyrtiff");
        bootstrapUtilsService.createMime("jp2", "image/jp2");
        bootstrapUtilsService.createMime("ndpi", "openslide/ndpi");
        bootstrapUtilsService.createMime("mrxs", "openslide/mrxs");
        bootstrapUtilsService.createMime("vms", "openslide/vms");
        bootstrapUtilsService.createMime("svs", "openslide/svs");
        bootstrapUtilsService.createMime("scn", "openslide/scn");
        bootstrapUtilsService.createMime("bif", "openslide/bif");
        bootstrapUtilsService.createMime("tif", "openslide/ventana");
        bootstrapUtilsService.createMime("tif", "philips/tif");

        bootstrapUtilsService.createRole("ROLE_USER");
        bootstrapUtilsService.createRole("ROLE_ADMIN");
        bootstrapUtilsService.createRole("ROLE_SUPER_ADMIN");
        bootstrapUtilsService.createRole("ROLE_GUEST");

        bootstrapUtilsService.createUser("admin", "Just an", "Admin", dataset.ADMINEMAIL, dataset.ADMINPASSWORD,  List.of("ROLE_USER", "ROLE_ADMIN"));
        bootstrapUtilsService.createUser("ImageServer1", "Image", "Server", dataset.ADMINEMAIL, RandomStringUtils.random(32).toUpperCase(), List.of("ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"));
        bootstrapUtilsService.createUser("superadmin", "Super", "Admin", dataset.ADMINEMAIL, dataset.ADMINPASSWORD,  List.of("ROLE_USER", "ROLE_ADMIN","ROLE_SUPER_ADMIN"));
        bootstrapUtilsService.createUser("rabbitmq", "rabbitmq", "user", dataset.ADMINEMAIL, RandomStringUtils.random(32).toUpperCase(),  List.of("ROLE_USER", "ROLE_SUPER_ADMIN"));
        bootstrapUtilsService.createUser("monitoring", "Monitoring", "Monitoring", dataset.ADMINEMAIL, RandomStringUtils.random(32).toUpperCase(),  List.of("ROLE_USER","ROLE_SUPER_ADMIN"));

        bootstrapUtilsService.createRelation(PARENT);

        bootstrapUtilsService.createConfigurations("WELCOME", "<p>Welcome to the Cytomine software.</p><p>This software is supported by the <a href='https://cytomine.coop'>Cytomine company</a></p>", ConfigurationReadingRole.ALL);
        bootstrapUtilsService.createConfigurations("admin_email", applicationConfiguration.getAdminEmail(), ConfigurationReadingRole.ADMIN);
//        bootstrapUtilsService.createConfigurations("notification_email", applicationConfiguration.getNotification().getEmail(), ConfigurationReadingRole.ADMIN);
//        bootstrapUtilsService.createConfigurations("notification_password", applicationConfiguration.getNotification().getPassword(), ConfigurationReadingRole.ADMIN);
//        bootstrapUtilsService.createConfigurations("notification_smtp_host", applicationConfiguration.getNotification().getSmtpHost(), ConfigurationReadingRole.ADMIN);
//        bootstrapUtilsService.createConfigurations("notification_smtp_port", applicationConfiguration.getNotification().getSmtpPort(), ConfigurationReadingRole.ADMIN);

//        SecUser admin = secUserRepository.findByUsernameLikeIgnoreCase("admin")
//                .orElseThrow(() -> new ObjectNotFoundException("admin user does not exists"));
//        admin.setPrivateKey(applicationConfiguration.getAdminPrivateKey());
//        admin.setPublicKey(applicationConfiguration.getAdminPublicKey());
//        secUserRepository.save(admin);

        changeUserKeys("admin", applicationConfiguration.getAdminPrivateKey(), applicationConfiguration.getAdminPublicKey());
        changeUserKeys("superadmin", applicationConfiguration.getSuperAdminPrivateKey(), applicationConfiguration.getSuperAdminPublicKey());

        bootstrapUtilsService.addDefaultProcessingServer();
        bootstrapUtilsService.addDefaultConstraints();

        changeUserKeys("rabbitmq", applicationConfiguration.getRabbitMQPrivateKey(), applicationConfiguration.getRabbitMQPublicKey());

        bootstrapUtilsService.createSoftwareUserRepository("GitHub", "cytomine", "cytomine","S_");
//        sur.save(failOnError: true))
    }

    private void changeUserKeys(String username, String privateKey, String publicKey) {
        SecUser user = secUserRepository.findByUsernameLikeIgnoreCase(username)
                .orElseThrow(() -> new ObjectNotFoundException(username + " user does not exists, cannot set its keys", ErrorCode.NOT_FOUND_USER.getValue(), Map.of("user", username)));
        user.setPrivateKey(privateKey);
        user.setPublicKey(publicKey);
        secUserRepository.save(user);
    }
}
