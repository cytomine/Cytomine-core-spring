package be.cytomine.config;

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

import be.cytomine.utils.LTreeType;
import com.vladmihalcea.hibernate.type.array.IntArrayType;
import com.vladmihalcea.hibernate.type.array.LongArrayType;
import org.hibernate.spatial.dialect.postgis.PostgisDialect;

import java.sql.Types;

public class CustomPostgreSQLDialect extends PostgisDialect {


    public CustomPostgreSQLDialect () {
        super();
//        registerHibernateType(Types.OTHER, IntArrayType.class.getName());
//        registerHibernateType(Types.ARRAY, IntArrayType.class.getName());
        registerHibernateType(Types.ARRAY, LongArrayType.class.getName());
        registerHibernateType(Types.OTHER, LTreeType.class.getName());
//        registerHibernateType(Types.OTHER, JsonBinaryType.class.getName());
//        registerHibernateType(Types.OTHER, JsonNodeBinaryType.class.getName());
//        registerHibernateType(Types.OTHER, JsonNodeStringType.class.getName());
    }
}
