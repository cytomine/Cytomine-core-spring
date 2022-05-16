package be.cytomine.api;

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
import be.cytomine.domain.security.AuthWithToken;
import be.cytomine.domain.security.ForgotPasswordToken;
import be.cytomine.domain.security.SecRole;
import be.cytomine.domain.security.User;
import be.cytomine.dto.LoginVM;
import be.cytomine.config.security.JWTFilter;
import be.cytomine.exceptions.ForbiddenException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.security.AuthWithTokenRepository;
import be.cytomine.repository.security.ForgotPasswordTokenRepository;
import be.cytomine.repository.security.UserRepository;
import be.cytomine.security.jwt.TokenProvider;
import be.cytomine.security.jwt.TokenType;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.service.utils.NotificationService;
import be.cytomine.utils.JsonObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import javax.persistence.EntityManager;
import javax.validation.Valid;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("")
@AllArgsConstructor
public class LoginController extends RestCytomineController {

    private final TokenProvider tokenProvider;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserRepository userRepository;

    private final NotificationService notificationService;

    private final SecurityACLService securityACLService;


    @PostMapping("/api/authenticate")
    public ResponseEntity<JWTToken> authorize(@Valid @RequestBody LoginVM loginVM) {

        try {
            JWTToken jwtTokens = logInAndGenerateCredentials(loginVM.getUsername(), loginVM.getPassword(), loginVM.isRememberMe());
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwtTokens.getToken());
            return new ResponseEntity<>(jwtTokens, httpHeaders, HttpStatus.OK);
        } catch (BadCredentialsException e) {
            throw new ForbiddenException("Bad username or password");
        } catch (AccountExpiredException e) {
            throw new ForbiddenException("Account expired");
        } catch (CredentialsExpiredException e) {
            throw new ForbiddenException("Password expired");
        } catch (DisabledException e) {
            throw new ForbiddenException("Account disabled");
        } catch (LockedException e) {
            throw new ForbiddenException("Account locked");
        }
    }



    @RequestMapping(path = {"/login/forgotUsername"}, method = {RequestMethod.POST})
    public ResponseEntity<String> checkPassword(@RequestBody MultiValueMap<String, String> formData) throws MessagingException {
        log.debug("REST request to retrieve username");

        User user = userRepository.findByEmailLikeIgnoreCase(formData.getFirst("j_email"))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", JsonObject.of("email", formData.getFirst("j_email")).toJsonString()));
        notificationService.notifyForgotUsername(user);
        return responseSuccess(JsonObject.of("message","Check your inbox"));
    }




    @RequestMapping(path = {"/login/forgotPassword"}, method = {RequestMethod.POST})
    public ResponseEntity<String> forgotPassword(
            @RequestBody MultiValueMap<String, String> formData
    ) throws MessagingException {
        String username = formData.getFirst("j_username");
        if (username!=null) {
            Optional<User> user = userRepository.findByUsernameLikeIgnoreCase(username); //we are not logged, so we bypass the service
            if (user.isPresent()) {
                String tokenKey = UUID.randomUUID().toString();
                ForgotPasswordToken forgotPasswordToken = new ForgotPasswordToken();
                forgotPasswordToken.setUser(user.get());
                forgotPasswordToken.setExpiryDate(DateUtils.addDays(new Date(), 1));
                forgotPasswordToken.setTokenKey(tokenKey);
                forgotPasswordTokenRepository.save(forgotPasswordToken);

                notificationService.notifyForgotPassword(user.get(), forgotPasswordToken);

                return responseSuccess(JsonObject.of("success", true, "message", "Check your inbox"));
            }

        }
        return responseSuccess(JsonObject.of()); //no explicit error
    }

    @Autowired
    AuthWithTokenRepository authWithTokenRepository;

    @Autowired
    ForgotPasswordTokenRepository forgotPasswordTokenRepository;

    @RequestMapping(path = {"/login/loginWithToken"}, method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<String> loginWithToken(
            @RequestParam String username,
            @RequestParam String tokenKey
    ) throws MessagingException {
        User user = userRepository.findByUsernameLikeIgnoreCaseAndEnabledIsTrue(username)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", username));

        Optional<AuthWithToken> authToken = authWithTokenRepository.findByTokenKeyAndUser(tokenKey, user);
        Optional<ForgotPasswordToken> forgotPasswordToken = forgotPasswordTokenRepository.findByTokenKeyAndUser(tokenKey, user);

        //check first if a entry is made for this token
        if (authToken.isPresent() && authToken.get().isValid())  {
            JWTToken jwtTokens = logInAndGenerateCredentials(user.getUsername(), user.getRoles(), false);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwtTokens.getToken());
            return new ResponseEntity<>(jwtTokens.toJsonObject().toJsonString(), httpHeaders, HttpStatus.OK);
        } else if (forgotPasswordToken.isPresent() && forgotPasswordToken.get().isValid())  {
            user = forgotPasswordToken.get().getUser();
            user.setPasswordExpired(true);
            userRepository.save(user);
            JWTToken jwtTokens = logInAndGenerateCredentials(user.getUsername(), user.getRoles(), false);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwtTokens.getToken());
            return new ResponseEntity<>(jwtTokens.toJsonObject().toJsonString(), httpHeaders, HttpStatus.OK);
        } else {
            throw new WrongArgumentException("Token is not valid");
        }
    }

    @RequestMapping(path = {"/api/token.json"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> buildToken() throws IOException {

        securityACLService.checkCurrentUserIsAdmin();

        JsonObject params = super.mergeQueryParamsAndBodyParams();
        String username = params.getJSONAttrStr("username");

        Double validity = params.getJSONAttrDouble("validity", 60d);
        User user = userRepository.findByUsernameLikeIgnoreCaseAndEnabledIsTrue(username)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", username));

        String tokenKey = UUID.randomUUID().toString();
        AuthWithToken token = new AuthWithToken();
        token.setUser(user);
        token.setExpiryDate(DateUtils.addMinutes(new Date(), validity.intValue()));
        token.setTokenKey(tokenKey);
        authWithTokenRepository.save(token);
        return  responseSuccess(JsonObject.of("success", true, "token", token.getTokenKey()));
    }



    private JWTToken logInAndGenerateCredentials(String username, String password, Boolean isRememberMe) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                username,
                password
        );
        Authentication authentication = authenticationManagerBuilder.getOrBuild().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = tokenProvider.createToken(authentication, isRememberMe ? TokenType.REMEMBER_ME : TokenType.SESSION);
        String shortTermToken=  tokenProvider.createToken(SecurityContextHolder.getContext().getAuthentication(), TokenType.SHORT_TERM);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + token);
        return new JWTToken(token, shortTermToken);
    }


    private JWTToken logInAndGenerateCredentials(String username, Set<SecRole> roles, Boolean isRememberMe) {
        List<GrantedAuthority> authorities = roles.stream().map(x -> new SimpleGrantedAuthority(x.getAuthority())).collect(Collectors.toList());
        Authentication newAuth = new UsernamePasswordAuthenticationToken(username,"************",authorities);
        SecurityContextHolder.getContext().setAuthentication(newAuth);
        String token = tokenProvider.createToken(newAuth, isRememberMe ? TokenType.REMEMBER_ME : TokenType.SESSION);
        String shortTermToken=  tokenProvider.createToken(SecurityContextHolder.getContext().getAuthentication(), TokenType.SHORT_TERM);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + token);
        return new JWTToken(token, shortTermToken);
    }

    /**
     * Object to return as body in JWT Authentication.
     */
    static class JWTToken {

        private String token;

        private String shortTermToken;

        JWTToken(String idToken, String shortTermToken) {
            this.token = idToken;
            this.shortTermToken = shortTermToken;
        }

        @JsonProperty("token")
        String getToken() {
            return token;
        }

        @JsonProperty("shortTermToken")
        String getShortTermToken() {
            return shortTermToken;
        }

        public JsonObject toJsonObject() {return JsonObject.of("token", token, "shortTermToken", shortTermToken);}
    }
}
