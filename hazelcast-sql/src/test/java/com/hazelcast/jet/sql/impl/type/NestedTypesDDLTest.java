/*
 * Copyright 2023 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sql.impl.type;

import com.hazelcast.config.Config;
import com.hazelcast.jet.sql.SqlTestSupport;
import com.hazelcast.jet.sql.impl.CalciteSqlOptimizer;
import com.hazelcast.jet.sql.impl.schema.RelationsStorage;
import com.hazelcast.sql.HazelcastSqlException;
import com.hazelcast.test.HazelcastSerialClassRunner;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Serializable;

import static com.hazelcast.spi.properties.ClusterProperty.SQL_CUSTOM_TYPES_ENABLED;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(HazelcastSerialClassRunner.class)
public class NestedTypesDDLTest extends SqlTestSupport {
    private static RelationsStorage storage;

    @BeforeClass
    public static void beforeClass() {
        Config config = smallInstanceConfig()
                .setProperty(SQL_CUSTOM_TYPES_ENABLED.getName(), "true");

        initialize(2, config);
        storage = ((CalciteSqlOptimizer) getNodeEngineImpl(instance()).getSqlService().getOptimizer()).relationsStorage();
    }

    @Test
    public void test_createTypeIsNotDuplicatedByDefault() {
        execute(format("CREATE TYPE FirstType OPTIONS ('format'='java','javaClass'='%s')", FirstType.class.getName()));
        assertThatThrownBy(() -> instance().getSql()
                .execute(format("CREATE TYPE FirstType OPTIONS ('format'='java','javaClass'='%s')", SecondType.class.getName())))
                .isInstanceOf(HazelcastSqlException.class)
                .hasMessage("Type already exists: FirstType");
        assertEquals(FirstType.class.getName(), storage.getType("FirstType").getJavaClassName());
    }

    @Test
    public void test_replaceType() {
        execute(format("CREATE TYPE FirstType OPTIONS ('format'='java','javaClass'='%s')", FirstType.class.getName()));
        execute(format("CREATE OR REPLACE TYPE FirstType OPTIONS ('format'='java','javaClass'='%s')", SecondType.class.getName()));

        assertEquals(SecondType.class.getName(), storage.getType("FirstType").getJavaClassName());
    }

    @Test
    public void test_createIfNotExists() {
        execute(format("CREATE TYPE FirstType OPTIONS ('format'='java','javaClass'='%s')", FirstType.class.getName()));
        execute(format("CREATE TYPE IF NOT EXISTS FirstType OPTIONS ('format'='java','javaClass'='%s')", SecondType.class.getName()));

        assertEquals(FirstType.class.getName(), storage.getType("FirstType").getJavaClassName());
    }

    @Test
    public void test_showTypes() {
        execute(format("CREATE TYPE FirstType OPTIONS ('format'='java','javaClass'='%s')", FirstType.class.getName()));
        execute(format("CREATE TYPE SecondType OPTIONS ('format'='java','javaClass'='%s')", SecondType.class.getName()));
        assertRowsAnyOrder("SHOW TYPES", rows(1, "FirstType", "SecondType"));
    }

    @Test
    public void test_dropNonexistentType() {
        assertThatThrownBy(() -> execute("DROP TYPE Foo"))
                .isInstanceOf(HazelcastSqlException.class)
                .hasMessage("Type does not exist: Foo");

        execute("DROP TYPE IF EXISTS Foo");
    }

    @Test
    public void test_failOnDuplicateColumnName() {
        assertThatThrownBy(() -> execute("CREATE TYPE TestType (id BIGINT, id BIGINT) "
                + "OPTIONS ('format'='compact', 'compactTypeName'='TestType')"))
                .isInstanceOf(HazelcastSqlException.class)
                .hasMessageContaining("Column 'id' specified more than once");
    }

    @Test
    public void test_failOnReplaceAndIfNotExists() {
        assertThatThrownBy(() -> execute("CREATE OR REPLACE TYPE IF NOT EXISTS TestType (id BIGINT, name VARCHAR) "
                + "OPTIONS ('format'='compact', 'compactTypeName'='TestType')"))
                .isInstanceOf(HazelcastSqlException.class)
                .hasMessageContaining("OR REPLACE in conjunction with IF NOT EXISTS not supported");
    }

    @Test
    public void test_fullyQualifiedTypeName() {
        execute(format("CREATE TYPE hazelcast.public.FirstType OPTIONS ('format'='java','javaClass'='%s')",
                FirstType.class.getName()));
        assertNotNull(storage.getType("FirstType"));

        execute("DROP TYPE hazelcast.public.FirstType");
        assertNull(storage.getType("FirstType"));
    }

    @Test
    public void test_failOnNonPublicSchemaType() {
        assertThatThrownBy(() -> execute("CREATE TYPE information_schema.TestType (id BIGINT, name VARCHAR) "
                + "OPTIONS ('format'='compact', 'compactTypeName'='TestType')"))
                .isInstanceOf(HazelcastSqlException.class)
                .hasMessageContaining("The type must be created in the \"public\" schema");
        assertNull(storage.getType("TestType"));

        execute(format("CREATE TYPE hazelcast.public.TestType OPTIONS ('format'='java','javaClass'='%s')",
                FirstType.class.getName()));
        assertThatThrownBy(() -> execute("DROP TYPE information_schema.TestType"))
                .isInstanceOf(HazelcastSqlException.class)
                .hasMessageContaining("Type does not exist: information_schema.TestType");
        assertNotNull(storage.getType("TestType"));
    }

    @Test
    public void test_failOnDuplicateOptions() {
        assertThatThrownBy(() -> execute("CREATE TYPE TestType (id BIGINT, name VARCHAR) "
                + "OPTIONS ('format'='compact', 'compactTypeName'='TestType', 'compactTypeName'='TestType2')"))
                .isInstanceOf(HazelcastSqlException.class)
                .hasMessageContaining("Option 'compactTypeName' specified more than once");
    }

    void execute(String sql) {
        instance().getSql().execute(sql);
    }

    public static class FirstType implements Serializable {
        private String name;

        public FirstType() { }

        public FirstType(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    public static class SecondType implements Serializable {
        private String name;

        public SecondType() { }

        public SecondType(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
