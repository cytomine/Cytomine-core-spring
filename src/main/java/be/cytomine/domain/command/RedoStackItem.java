package be.cytomine.domain.command;

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
import be.cytomine.domain.security.SecUser;
import be.cytomine.utils.JsonObject;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
public class RedoStackItem extends CytomineDomain {


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "command_id", nullable = false)
    protected Command command;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    protected SecUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = true)
    protected Transaction transaction;


    public RedoStackItem() {
    }

    public RedoStackItem(UndoStackItem firstUndoStack) {
        this.command = firstUndoStack.getCommand();
        this.user = firstUndoStack.getUser();
        this.transaction = firstUndoStack.getTransaction();
    }

//    @PrePersist
//    public void beforeCreate() {
//        super.beforeInsert();
//    }
//
//    @PreUpdate
//    public void beforeUpdate() {
//        super.beforeUpdate();
//    }

    public CytomineDomain buildDomainFromJson(JsonObject json, EntityManager entityManager) {
        throw new RuntimeException("Not supported");
    }

    public static JsonObject getDataFromDomain(CytomineDomain domain) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public String toJSON() {
        return getDataFromDomain(this).toJsonString();
    }

    @Override
    public JsonObject toJsonObject() {
        return getDataFromDomain(this);
    }
}
