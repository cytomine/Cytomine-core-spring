package be.cytomine.service.ontology;

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
import be.cytomine.domain.ontology.*;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.*;
import be.cytomine.exceptions.*;
import be.cytomine.repository.ontology.*;
import be.cytomine.repository.security.ForgotPasswordTokenRepository;
import be.cytomine.repository.security.SecRoleRepository;
import be.cytomine.repository.security.SecUserSecRoleRepository;
import be.cytomine.service.CurrentRoleService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.dto.CropParameter;
import be.cytomine.service.middleware.ImageServerService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.service.utils.NotificationService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.mail.MessagingException;
import javax.transaction.Transactional;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.security.acls.domain.BasePermission.*;

@Slf4j
@Service
@Transactional
public class SharedAnnotationService extends ModelService {

    @Autowired
    private SharedAnnotationRepository sharedAnnotationRepository;

    @Autowired
    private SecurityACLService securityACLService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private AnnotationDomainRepository annotationDomainRepository;


    @Autowired
    private SecUserService secUserService;

    @Autowired
    private SecRoleRepository secRoleRepository;

    @Autowired
    private SecUserSecRoleRepository secUserSecRoleRepository;

    @Autowired
    private ForgotPasswordTokenRepository forgotPasswordTokenRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CurrentRoleService currentRoleService;

    private ImageServerService imageServerService;

    @Autowired
    public void setImageServerService(ImageServerService imageServerService) {
        this.imageServerService = imageServerService;
    }

    @Override
    public Class currentDomain() {
        return SharedAnnotation.class;
    }

    /**
     * List all sharedAnnotation, Only for admin
     */
    public List<SharedAnnotation> list() {
        securityACLService.checkAdmin(currentUserService.getCurrentUser());
        return sharedAnnotationRepository.findAll();
    }

    public SharedAnnotation get(Long id) {
        return find(id).orElse(null);
    }

    public Optional<SharedAnnotation> find(Long id) {
        Optional<SharedAnnotation> optionalSharedAnnotation = sharedAnnotationRepository.findById(id);
        optionalSharedAnnotation.ifPresent(sharedAnnotation -> securityACLService.check(sharedAnnotation.container(),READ));
        return optionalSharedAnnotation;
    }


    public List<SharedAnnotation> listComments(AnnotationDomain annotation) {
        User user = (User)currentUserService.getCurrentUser();
        List<SharedAnnotation> sharedAnnotations = sharedAnnotationRepository
                .findAllByAnnotationIdentOrderByCreatedDesc(annotation.getId());
        boolean isUserAdmin = currentRoleService.isAdminByNow(user);
        sharedAnnotations = sharedAnnotations.stream().filter(x -> isUserAdmin || x.getSender().equals(user) || x.getReceivers().contains(user))
                .distinct()
                .collect(Collectors.toList());

        return sharedAnnotations;
    }


    /**
     * Add the new domain with JSON data
     * @param jsonObject New domain data
     * @return Response structure (created domain data,..)
     */
    @Override
    @Transactional(dontRollbackOn = IOException.class)
    public CommandResponse add(JsonObject jsonObject) {
        User sender = (User)currentUserService.getCurrentUser();
        securityACLService.checkUser(sender);

        AnnotationDomain annotation = annotationDomainRepository.findById(jsonObject.getJSONAttrLong("annotationIdent"))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AnnotationDomain", jsonObject.getJSONAttrStr("annotationIdent")));

        jsonObject.putIfAbsent("sender", sender.getId());

        String cid = UUID.randomUUID().toString();

        //create annotation crop (will be send with comment)
        File annotationCrop = null;
        try {
            CropParameter cropParameter = new CropParameter();
            cropParameter.setFormat("png");
            cropParameter.setAlphaMask(true);
            cropParameter.setGeometry(annotation.getWktLocation());
            cropParameter.setComplete(true);
            cropParameter.setMaxSize(512);

            BufferedImage bufferedImage;
            try {
                InputStream is = new ByteArrayInputStream(imageServerService.crop(annotation, cropParameter));
                bufferedImage = ImageIO.read(is);
            } catch(Exception e) {
                bufferedImage = null;
            }

            log.info("Image " + bufferedImage);

            if (bufferedImage != null) {
                annotationCrop = File.createTempFile("temp", "."+cropParameter.getFormat());
                annotationCrop.deleteOnExit();
                ImageIO.write(bufferedImage, cropParameter.getFormat(), annotationCrop);
            }
        } catch (FileNotFoundException e) {
            annotationCrop = null;
        } catch (IOException e) {
            log.error("Cannot retrieve crop", e);
            throw new WrongArgumentException("Cannot retrieve crop:" + e);
        }

        Map<String, File> attachments = new LinkedHashMap<>();
        if (annotationCrop != null) {
            attachments.put(cid, annotationCrop);
        }

        //do receivers email list
        List<String> receiversEmail = new ArrayList<>();
        List<User> receivers = new ArrayList<>();

        if (jsonObject.containsKey("receivers")) {
            receiversEmail = jsonObject.getJSONAttrListLong("receivers")
                    .stream()
                    .map(x -> getEntityManager().find(User.class, x))
                    .map(User::getEmail)
                    .collect(Collectors.toList());
        } else if (jsonObject.containsKey("emails")) {
            receiversEmail = Arrays.stream(jsonObject.getJSONAttrStr("emails").split(",")).collect(Collectors.toList());

            for (String email : receiversEmail) {
                if (secUserService.findByEmail(email).isEmpty()) {
                    JsonObject guestUser = JsonObject.of(
                            "username", email,
                            "firstname", "firstname",
                            "lastname", "lastname",
                            "email", email,
                            "password", "passwordExpired"
                    );

                    secUserService.add(guestUser);
                    User user = (User) secUserService.findByUsername(email).get();
                    SecRole secRole = secRoleRepository.findByAuthority("ROLE_GUEST").get();

                    SecUserSecRole secUserSecRole = new SecUserSecRole();
                    secUserSecRole.setSecUser(user);
                    secUserSecRole.setSecRole(secRole);
                    secUserSecRole = secUserSecRoleRepository.save(secUserSecRole);

                    secUserService.addUserToProject(user, annotation.getProject(), false);

                    user.setPasswordExpired(true);
                    getEntityManager().persist(user);

                    ForgotPasswordToken forgotPasswordToken = new ForgotPasswordToken();
                    forgotPasswordToken.setUser(user);
                    forgotPasswordToken.setTokenKey(UUID.randomUUID().toString());
                    forgotPasswordToken.setExpiryDate(DateUtils.addDays(new Date(), 1));
                    forgotPasswordTokenRepository.save(forgotPasswordToken);

                    try {
                        notificationService.notifyWelcome(sender, user, forgotPasswordToken);
                    } catch (Exception e) {
                        log.error("Cannot send welcome message to user " + user.getEmail(), e);
                    }
                }
                receivers.add((User)secUserService.findByUsername(email).get());
            }
            jsonObject.put("receivers", receivers.stream().map(x -> x.getId()).collect(Collectors.toList()));
        }

        securityACLService.checkFullOrRestrictedForOwner(annotation, annotation.user());
        CommandResponse result =  executeCommand(new AddCommand(sender), null, jsonObject);
        if (result!=null) {
            log.info("send mail to " + receiversEmail);
            try {
                notificationService.notifyShareAnnotationMessage(
                        sender,
                        receiversEmail,
                        jsonObject.getJSONAttrStr("subject"),
                        jsonObject.getJSONAttrStr("from"),
                        jsonObject.getJSONAttrStr("comment"),
                        jsonObject.getJSONAttrStr("annotationURL"),
                        jsonObject.getJSONAttrStr("shareAnnotationURL"),
                        attachments,
                        cid);
            } catch (Exception e) {
                log.error("Cannot send email for shared annotation", e);
            }
        }
        return result;
    }


    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    @Override
    public CommandResponse delete(CytomineDomain domain, Transaction transaction, Task task, boolean printMessage) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkFullOrRestrictedForOwner(domain.container(),((SharedAnnotation)domain).getSender());
        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c,domain, null);
    }


    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new SharedAnnotation().buildDomainFromJson(json, getEntityManager());
    }

    @Override
    public List<String> getStringParamsI18n(CytomineDomain domain) {
        SharedAnnotation sharedAnnotation = (SharedAnnotation)domain;
        return Arrays.asList(String.valueOf(sharedAnnotation.getSender().getId()), String.valueOf(sharedAnnotation.getAnnotationIdent()), sharedAnnotation.getAnnotationClassName());
    }

    @Override
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        throw new RuntimeException("Update is not implemented for shared annotation");
    }

    @Override
    public void deleteDependencies(CytomineDomain domain, Transaction transaction, Task task) {
    }

}
