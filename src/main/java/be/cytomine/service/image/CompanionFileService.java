package be.cytomine.service.image;

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
import be.cytomine.domain.image.AbstractImage;
import be.cytomine.domain.image.CompanionFile;
import be.cytomine.domain.image.UploadedFile;
import be.cytomine.domain.security.SecUser;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.InvalidRequestException;
import be.cytomine.repository.image.CompanionFileRepository;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.middleware.ImageServerService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static be.cytomine.domain.image.UploadedFileStatus.ERROR_CONVERSION;
import static be.cytomine.domain.image.UploadedFileStatus.UPLOADED;
import static org.springframework.security.acls.domain.BasePermission.READ;
import static org.springframework.security.acls.domain.BasePermission.WRITE;

@Slf4j
@Service
@Transactional
public class CompanionFileService extends ModelService {

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private SecurityACLService securityACLService;

    @Autowired
    private CompanionFileRepository companionFileRepository;

    @Autowired
    private ImageServerService imageServerService;


    @Override
    public Class currentDomain() {
        return CompanionFile.class;
    }

    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new CompanionFile().buildDomainFromJson(json, getEntityManager());
    }

    public Optional<CompanionFile> find(Long id) {
        Optional<CompanionFile> companionFile = companionFileRepository.findById(id);
        companionFile.ifPresent(cf -> {
            if (!securityACLService.hasRightToReadAbstractImageWithProject(cf.getImage())) {
                securityACLService.check(cf.container(),READ);
            }
        });
        return companionFile;
    }

    public CompanionFile get(Long id) {
        return find(id).orElse(null);
    }

    public List<CompanionFile> list(AbstractImage image) {
        if (!securityACLService.hasRightToReadAbstractImageWithProject(image)) {
            securityACLService.check(image.container(),READ);
        }
        return companionFileRepository.findAllByImage(image);
    }

    public List<CompanionFile> list(UploadedFile uploadedFile) {
        securityACLService.check(uploadedFile, READ);
        return companionFileRepository.findAllByUploadedFile(uploadedFile);
    }


    /**
     * Add the new domain with JSON data
     * @param json New domain data
     * @return Response structure (created domain data,..)
     */
    public CommandResponse add(JsonObject json) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.checkUser(currentUser);

        return executeCommand(new AddCommand(currentUser),null, json);

    }

    /**
     * Update this domain with new data from json
     * @param domain Domain to update
     * @param jsonNewData New domain datas
     * @return  Response structure (new domain data, old domain data..)
     */
    @Override
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.check(domain.container(),WRITE);
        return executeCommand(new EditCommand(currentUser, transaction), domain,jsonNewData);
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
        securityACLService.checkUser(currentUser);
        securityACLService.check(domain.container(),WRITE);

        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c,domain, null);
    }

    @Override
    public void checkDoNotAlreadyExist(CytomineDomain domain) {
        // TODO: with new session?
        Optional<CompanionFile> file = companionFileRepository.findByImageAndUploadedFile(((CompanionFile)domain).getImage(), ((CompanionFile)domain).getUploadedFile());
        if (file.isPresent() && !Objects.equals(file.get().getId(), domain.getId())) {
            throw new AlreadyExistException("Companion file already exists for AbstractImage " + ((CompanionFile)domain).getImage());
        }
    }


    @Override
    public List<Object> getStringParamsI18n(CytomineDomain domain) {
        return List.of(domain.getId(), ((CompanionFile)domain).getOriginalFilename());
    }

    public boolean hasProfile(AbstractImage image) {
        return !companionFileRepository.findAllByImage(image).stream().filter(x -> x.getType().equals("HDF5")).toList().isEmpty();
    }


//    public CompanionFile addProfile(AbstractImage image) {
//        if (hasProfile(image)) {
//            throw new InvalidRequestException("Image " + image + " already has a profile");
//        }
//
//        if (image.getDimensions().length() != 3) {
//            throw new InvalidRequestException("Image "+ image +" is not a 3D image");
//        }
//
//        String filename = "spectral.HDF5";
//        String extension = "hdf5";
//        String contentType = "HDF5";
//
//
//        UploadedFile parent = image.getUploadedFile();
//        Path parentPath = Paths.get(parent.getFilename()).getParent();
//        String destinationPath = parentPath.resolve("processed/" + filename).toString();
//        UploadedFile uf = new UploadedFile();
//        uf.setParent(parent);
//        uf.setImageServer(parent.getImageServer());
//        uf.setContentType(contentType);
//        uf.setStorage(parent.getStorage());
//        uf.setUser(currentUserService.getCurrentUser());
//        uf.setOriginalFilename(filename);
//        uf.setExt(extension);
//        uf.setSize(0L);
//        uf.setStatus(UPLOADED.getCode());
//        uf.setFilename(destinationPath);
//        getEntityManager().persist(uf);
//
//        CompanionFile cf = new CompanionFile();
//        cf.setUploadedFile(uf);
//        cf.setImage(image);
//        cf.setOriginalFilename(filename);
//        cf.setType("HDF5");
//        getEntityManager().persist(cf);
//
//        try {
//            imageServerService.makeHDF5(image.getId(), cf.getId(), uf.getId());
//        } catch (Exception e) {
//            uf.setStatus(ERROR_CONVERSION.getCode());
//            getEntityManager().persist(uf);
//        }
//        return cf;
//    }
}
