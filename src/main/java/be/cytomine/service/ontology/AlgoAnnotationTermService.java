package be.cytomine.service.ontology;

import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.command.AddCommand;
import be.cytomine.domain.command.Command;
import be.cytomine.domain.command.DeleteCommand;
import be.cytomine.domain.command.Transaction;
import be.cytomine.domain.ontology.*;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.domain.security.UserJob;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.CytomineMethodNotYetImplementedException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.ontology.AlgoAnnotationTermRepository;
import be.cytomine.repository.ontology.AnnotationTermRepository;
import be.cytomine.repository.ontology.TermRepository;
import be.cytomine.repository.ontology. AlgoAnnotationRepository;
import be.cytomine.repository.security.SecUserRepository;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.security.acls.domain.BasePermission.READ;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AlgoAnnotationTermService extends ModelService {

    private final AlgoAnnotationTermRepository algoAnnotationTermRepository;

    private final SecurityACLService securityACLService;

    private final CurrentUserService currentUserService;

    private final SecUserRepository userRepository;

    private final TermRepository termRepository;

    private final  AlgoAnnotationRepository algoAnnotationRepository;

    private final TransactionService transactionService;

    @Override
    public Class currentDomain() {
        return AlgoAnnotationTerm.class;
    }


    public List<AlgoAnnotationTerm> list(AnnotationDomain  annotationDomain) {
        securityACLService.check(annotationDomain.container(),READ);
        return algoAnnotationTermRepository.findAllByAnnotation(annotationDomain);
    }

    public long count(Project project) {
        return algoAnnotationTermRepository.countByProject(project);
    }

    //TODO:
//    def count(Job job) {
//        securityACLService.check(job.container(),READ)
//        long total = 0
//        List<UserJob> users = UserJob.findAllByJob(job)
//        users.each {
//            total = total + AlgoAnnotationTerm.countByUserJobAndDeletedIsNull(it)
//        }
//        total
//    }


    public Optional<AlgoAnnotationTerm> find(AnnotationDomain annotation, Term term, UserJob userJob) {
        securityACLService.check(annotation.container(),READ);
        if (userJob!=null) {
            return algoAnnotationTermRepository.findByAnnotationIdentAndTermAndUserJob(annotation.getId(), term, userJob);
        } else {
            //TODO: problem if it exists multiple results (2 users => same term for same annotation)
            return algoAnnotationTermRepository.findByAnnotationIdentAndTerm(annotation.getId(), term);
        }
    }

    /**
     * Add the new domain with JSON data
     * @param jsonObject New domain data
     * @return Response structure (created domain data,..)
     */
    @Override
    public CommandResponse add(JsonObject jsonObject) {

        AnnotationDomain annotation;
        try {
            annotation = AnnotationDomain.getAnnotationDomain(getEntityManager(), jsonObject.getJSONAttrLong("annotation"));
        } catch(Exception e) {
            annotation = AnnotationDomain.getAnnotationDomain(getEntityManager(), jsonObject.getJSONAttrLong("annotationIdent"));
        }

        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.check(annotation.container(),READ);
        SecUser creator = userRepository.findById(jsonObject.getJSONAttrLong("user",0L))
                .orElse(currentUser);
        jsonObject.put("user", creator.getId());
        jsonObject.put("annotationIdent", annotation.getId());
        jsonObject.put("annotationClassName", annotation.getClass().getName());

        return executeCommand(new AddCommand(currentUser),null,jsonObject);
    }



    public CommandResponse addAlgoAnnotationTerm(AnnotationDomain annotation, Long idTerm, Long idUser, SecUser currentUser, Transaction transaction){
        JsonObject json = JsonObject.of(
                "annotationClassName", annotation.getClass().getName(),
                "annotationIdent",annotation.getId(),
                "term", idTerm,
                "user", idUser
        );
        return executeCommand(new AddCommand(currentUser, transaction), null,json);
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
        // TODO: no ACL?
        Command c = new DeleteCommand(currentUserService.getCurrentUser(), transaction);
        return executeCommand(c,domain, null);
    }

    @Override
    public void checkDoNotAlreadyExist(CytomineDomain domain) {

    }


    protected void afterAdd(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }

    protected void afterDelete(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }

    protected void afterUpdate(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }


    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new AlgoAnnotationTerm().buildDomainFromJson(json, getEntityManager());
    }


    @Override
    public List<String> getStringParamsI18n(CytomineDomain domain) {
        AlgoAnnotationTerm rt = (AlgoAnnotationTerm)domain;
        return Arrays.asList(rt.getTerm().getName(), rt.getAnnotationIdent().toString(), rt.getUserJob().toString());
    }

    @Override
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        throw new RuntimeException("Update is not implemented for Annotation Term");
    }



    public void deleteDependencies(CytomineDomain domain, Transaction transaction, Task task) {
        return;
    }

    // TODO: retrieval migration?
}