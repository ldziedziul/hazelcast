# Data link support TDD

|                                |                            |
|--------------------------------|----------------------------|
| Related Github issues          | TODO                       |
| Document Status / Completeness | Approved                   |
| Author(s)                      | Viliam Durina              |
| Developer(s)                   | Sasha Syrotenko, Burak Gok |
| Technical Reviewers            | Frantisek Hartman          |

# Name of the feature

In 5.2, it was named “data store”.

We considered the following names:

1. CREATE CONNECTION
2. CREATE DATA STORE
3. CREATE DATA LINK

“Connection” was rejected because it suggests that it’s a physical connection,
while it can be a pool, or just metadata to create single-use connections.

“Data store” was rejected because it can be confused with the Storage feature we
have (similarly to how “mapping” is confused with “imap”). The term “link”
doesn't have as much content. Oracle also uses “database link” in a similar
manner.

# Summary of Beta state in 5.2

In 5.2, a new configuration element was introduced:

```xml
<!--
 ===== HAZELCAST EXTERNAL DATA STORE CONFIGURATION =====

 Configuration element's name is <external-data-store>. Contains configuration of external data stores
 used by map stores and jdbc sinks and sources
 -->
<external-data-store name="mysql-database">
    <class-name>com.hazelcast.datastore.JdbcDataStoreFactory</class-name>
    <properties>
        <property name="jdbcUrl">jdbc:mysql://dummy:3306</property>
    </properties>
    <shared>false</shared>
</external-data-store>
```

This can then be used when creating a Java pipeline JDBC source:

```java
@Beta
public static <T> BatchSource<T> jdbc(
    @Nonnull ExternalDataStoreRef externalDataStoreRef,
    @Nonnull ToResultSetFunction resultSetFn,
    @Nonnull FunctionEx<? super ResultSet,?extends T>createOutputFn
)
```

Or in a mapping:

```sql
CREATE MAPPING people
TYPE JDBC
OPTIONS (
 'externalDataStoreRef'='mysql-database'
)
```

Or in `GenericMapStore` configuration:

```xml

<map name="my-map">
    <map-store enabled="true">
        <class-name>com.hazelcast.mapstore.GenericMapStore</class-name>
        <properties>
            <property name="external-data-store-ref">my-mysql-database</property>
        </properties>
    </map-store>
</map>
```

## Implementation

### ExternalDataStoreService

Available via `NodeEngine#getExternalDataStoreService`

Gives access to any data store factories defined in the config by their name:

```java
<DS> ExternalDataStoreFactory<DS> getExternalDataStoreFactory(String name);
```

### ExternalDataStoreFactory

Creates an instance of the data store based on the configuration.

An instance of a data store differs one system from another (only JDBC is
implemented atm):

* For JDBC, it’s a `javax.sql.DataSource` (either simple or pooled)
* For Hazelcast, it is the `HazecastInstance`
* For Kafka, it is either `KafkaConsumer` or `KafkaProducer`
* For MongoDB, it is `MongoClient`

This instance should be thread-safe.

Connectors (jet sources/sinks) may either use this instance directly, or
retrieve another object from it, e.g. in case of DataSource a Connection.

For JDBC, we didn't want to create our own pool so we used [Hikari
pool](https://github.com/brettwooldridge/HikariCP).

Particular Jet source/sink then uses the `ExternalDataStoreRef` to look the
factory up:

```java
ExternalDataStoreFactory<?> dataStoreFactory = nodeEngine.getExternalDataStoreService().getExternalDataStoreFactory(name);
if (!(dataStoreFactory instanceof JdbcDataStoreFactory)) {
   String className = JdbcDataStoreFactory.class.getSimpleName();
   throw new HazelcastException("Data store factory '" + name + "' must be an instance of " + className);
}
return (JdbcDataStoreFactory) dataStoreFactory;
```
We considered 2 use cases of a data source - shared and non-shared:

* Shared - an instance is created at startup - e.g. a single instance of the
  pool is created, getExternalDataStoreFactory(name) returns same instance each
  time
* Non-shared - each getExternalDataStoreFactory(name) is called, a different
  instance is created

They have a different lifecycle - shared is created once and closed at shutdown.

Non-shared should be closed when the job is finished.

To make minimal changes to the Jet sources/sinks we create a wrapper which
doesn’t close the DataSource when close is called, and leaves the closing to the
JdbcDataStoreFactory on shutdown. See
[com.hazelcast.datastore.impl.CloseableDataSource#nonClosing](https://github.com/hazelcast/hazelcast/blob/7c3894b1474dba56648cf10ad8441615486fcdea/hazelcast/src/main/java/com/hazelcast/datastore/impl/CloseableDataSource.java#L98-L106)

# Terminology

* **Connector**: a type of remote system, e.g. remote Hazelcast, Kafka, Jdbc,
  Kinesis…
* **Data link**: a reference to a single external system. It contains metadata
  needed to connect, and might or might not maintain a physical connection or
  connections, depending on the connector.
* **Connection**: a single physical connection instance used by a processor to
  read/write from/to a remote system. The connection might or might not be
  shared or reused, that depends on the data link implementation.

# Background

Hazelcast Jet was originally designed with large batch jobs and streaming jobs
in mind. With SQL this changed, very small jobs are now common. Sources and
sinks currently open and close connections for each job, and this is a huge
overhead if that connection is used, for example, to insert just a single
row.[^1]

In 5.2 we added support for JDBC data link, which essentially is a connection
pool specified in the member config. A source or sink processor can then refer
to this data link by name, and instead of creating a new connection, one will be
taken from the pool. We want to generalize this approach to all connectors. The
feature was released in Beta status in 5.2.

# Types of connection sharing

* **Single-use**: when a connection is needed, new one is created, and it’s
  disconnected after use
* **Pooled:** if a connection is needed, it is borrowed from the pool. After
  done, it is reset[^2] and returned to the pool. One physical connection is
  always used by one process.
* **Shared**: the same connection can be used by multiple threads for multiple
  concurrent tasks. The connection must be stateless. Hazelcast client works
  this way.

Streaming sources use single-use connections. Streaming sinks might use
single-use connections, if there’s high traffic, or they can use pooled, if
connection reset is sufficiently fast or the traffic is sufficiently sparse.

Batch sources and sinks typically use pooled connections. If the engine knows
that the batch is large, it can use a single-use connection.

A shared connection can be used in all scenarios, if the particular client
supports it.

In some scenarios the user might want to not share or pool the connections, even
if it is supported, for example for large batch jobs: this can be worked around
by not using a data link, but directly providing connection parameters to the
source/sink in question, or in the MAPPING for SQL, or by creating multiple data
links, all connecting to the same remote system.

# Two ways to define a data link

1. In config. This way allows a more reliable way of defining a data link. An
   SQL object can be lost in case of a cluster havoc. The user will have to
   recreate them to keep the cluster up. Having it configured in the config also
   allows us to define a GenericMapStore in config, which is common.
   Disadvantage of config is that it’s not updatable, to update the IP address
   or change password one needs to restart the cluster. \
2. Through SQL: the data link metadata will be stored in SQL catalog, will be
   updatable and removable. These metadata will be lost in a full cluster
   restart (unless backed by Hot Restart).

# Creating a data link in SQL

```sql
CREATE DATA LINK <name>
[CONNECTOR] TYPE <connector name>
OPTIONS ( /* connector-specific options */ );
```

Example for JDBC:

```sql
CREATE DATA LINK my_database
TYPE JDBC
OPTIONS (
    'jdbcUrl'='jdbc:mysql:...',
    'initialPoolSize'='5',
    'maximumPoolSize'='0', …);
```

A data link created in SQL can be also used in Jet API.

## What happens after executing CREATE DATA LINK

1. The engine checks whether a same-named data link exists in config. If yes,
   fail
2. Write the data link metadata to the SQL catalog IMap
3. Send a message to all members (including itself) to pick up the new config,
   in a fire-and-forget manner
4. When handling that message, create the requested data link (that means call
   `SqlConnector.createDataLink()` and store it in some map on the member).
5. There should also be a background process on each that will regularly scan
   the SQL catalog and create/alter/delete the data link according to the data
   in the catalog. This avoids getting out of sync or leaking of data links.
   Also resolves a race in handling concurrent CREATE/DROP DATA LINK

# Data Link object

A data link is a named schema object. It’s created either in config, or using
SQL. According to the connector type, it might be either just metadata, a
connection pool or a shared connection.

The data link has a unique name, it’s unique in config and in the SQL catalog.
If a data link is found in the catalog that conflicts in name with one in the
config, the one from the catalog is deleted by the background job. This happens
also after a data link is added through _dynamic config_.

## Name scoping

SQL catalog currently stores only objects in the `public` schema, and the schema
isn’t stored in the catalog map at all. Also, all objects in it are in a single
namespace, because you can’t have a _mapping_ with name equal to a _view_ or
_user-defined type name._

Ideally, the map’s key should have been a tuple of three strings: {namespace,
schema, name}. We can either implement a migration from the current key, or
devise a backwards-compatible way to encode all three in a single string, or a
combination thereof. We leave this to the implementer.

Data links will be stored in a new namespace. That means that one can have a
data link named `foo`, and a mapping named `foo` in the same schema. Currently,
all will be in the `public` schema[^3]. The data links defined in config will
also be assumed to exist in the public schema.

When a data link is used in an SQL command (e.g. in a CREATE MAPPING command),
it can have a fully qualified name. A dot (.) in a data link name defined in
config, or any other character, is interpreted literally, to use such a data
link in SQL, one has to enclose it in double quotes.

## Resources

### Multiple types of resources in a single data link

Since one connector can have multiple types of data sources, both bounded and
unbounded, and we want to use the same connection for all of them, we need to
move the `SqlConnector.isStream()` method to `JetTable` class (or another
object-level class). For example, Hazelcast can support not only imap, but also
imap journal, IList, queue etc., and we want to access all of them using the
same HZ client instance.

Since the resources can be in different namespaces, the list of resources needs
a namespace property. We’ll call it “object type”. We’ll add this to the CREATE
MAPPING command after the TYPE keyword. Because now we’ll have two kinds of
TYPEs, we’ll change TYPE to CONNECTOR TYPE, and the CONNECTOR will be optional
for compatibility. The object type value will be a SimpleIdentifier, and will be
evaluated case-insensitively.

Current state:

```sql
CREATE MAPPING my_mapping 
EXTERNAL NAME "my_map" (
   … columns
)
TYPE imap
OPTIONS (...);
```

New state:

```sql
CREATE MAPPING my_mapping 
EXTERNAL NAME "my_map" (
   … columns
)
[CONNECTOR] TYPE imap OBJECT TYPE mapJournal
OPTIONS (...);
```

More examples of type clauses.

```sql
CONNECTOR TYPE jms OBJECT TYPE topic;
CONNECTOR TYPE local OBJECT TYPE ilist;
```

Connectors can have a default object type. To accommodate the new syntax, we can
rename the `imap` connector to `local` (and keep the old as deprecated for b-w
compatibility) and make the `imap` the default object type.

### List of resources

The data link should be able to provide a list of resources. A resource is an
object for which a mapping can be created. For JDBC it is the list of tables and
views, for Kafka the list of topics, and for a filesystem the list of files etc.

The list should include all accessible resources the connection provides. For
RDBMS, they include tables and views in all schemas, or system tables. However,
it is not strictly required that the data link lists all resources; a mapping
can be created for a resource that is not listed. For example, the list of
resources in Oracle might not include tables available through a database link.
In fact, it might list no resources at all, perhaps if the security in the
target system prevents reading of such a list.

The EXTERNAL NAME is a simple identifier. That means it cannot contain a dot
(.). For remote SQL databases the dot separates the schema, but the dot can also
be part of the table name. Take an example:

<table>
  <tr>
   <td><strong>Schema name</strong>
   </td>
   <td><strong>Table name</strong>
   </td>
   <td><strong>Resource name</strong>
   </td>
   <td><strong>Used in EXTERNAL NAME</strong>
   </td>
   <td><strong>Note</strong>
   </td>
  </tr>
  <tr>
   <td><default>
   </td>
   <td>bar
   </td>
   <td>bar
   </td>
   <td>bar
   </td>
   <td>
   </td>
  </tr>
  <tr>
   <td>foo
   </td>
   <td>bar
   </td>
   <td>foo.bar
   </td>
   <td>“foo.bar”
   </td>
   <td>
   </td>
  </tr>
  <tr>
   <td>foo
   </td>
   <td>bar.baz
   </td>
   <td>foo.”bar.baz”
<p>
OR
<p>
“foo”.”bar.baz”
   </td>
   <td>“foo.””bar.baz”””
<p>
OR
<p>
“””foo””.””bar.baz”””
   </td>
   <td>Literal dot in table name. Quoting schema is perhaps more readable.
   </td>
  </tr>
</table>

For simplicity, the connector can always fully-qualify resources and avoid using
the default schema.[^4]

# Java APIs

**hazelcast.jar**

```java
interface JetConnector {
    // moved from SqlConnector
    String typeName();

    // the implementation might decide to throw UnsupOpExc
    DataLink createDataLink(String name,
                            Map<String, String> options);
}

interface DataLink {
    // returns the name specified in config, or in CREATE DATA LINK
    String getName();


    // list available resources: tuples of {object_type, object_name}
    List<Tuple2<String, String>> listResources();


    // not implementing:
    // tries to connect, but don't read any data.
    void testConnection();

    // options from the config or SQL command
    Map<String, String> getOptions();

    // Called for DROP DATA LINK.
    // Should close unused connections in the pool. Shared connection
    // should be closed when last thread returns it (by refcounting)
    void destroy();
}
```

**hazelcast-sql.jar**

```java
interface SqlConnector extends DataLinkConnector {
    // add arguments:
    //   @Nullable String objectType
    //   @Nullable DataLink dataLink
    // to methods resolveAndValidateFields() and createTable()

    // move isStream() to JetConnector

    // move all the remaining methods to Table

    // in Table, add `boolean unbounded` to sink–producing methods:
    // insertProcessor, sinkProcessor, updateProcessor, deleteProcessor
}
```

## Connector-specific API

### Hazelcast Client

Used to connect to remote HZ clusters. Since the HZ client is fully thread-safe,
a single shared instance can be used. Any processor that needs a connection from
the data link will obtain the same HZ client instance.

```java
public class RemoteHazelcastDataLink implements DataLink {
    HazelcastInstance getSharedClient();
}
```

### Kafka

Apache Kafka has 2 types of connections: producers and consumers. Consumers are
non-shareable, and are always streaming, therefore a single-use KafkaConsumer
instance will be created for each processor that needs a connection, according
to the metadata stored in the data link.

On the producer side (in sinks), we can have a different behavior depending on
the type of the job:

* for bounded jobs, a pool will be used
* for unbounded jobs, single-use instance will be used

```java
public class KafkaDataLink implements DataLink {
    KafkaConcumer createSingleUseConsumer();

    KafkaProducer createSingleUseProducer();

    KafkaProducer getPooledProducer();
}
```

### JDBC

* For the bounded case, a pooled connection will be used. This applies to all
  sources, and for sinks in bounded jobs
* For the unbounded case, a single-use connection will be used. This applies to
  sinks in unbounded jobs.

```java
public class JdbcDataLink implements DataLink {
    Connection getPooledConnection();

    Connection createSingleUseConnection();
}
```

Note that it does not support XAConnection. An XA connection is only needed in
an exactly-once sink, which we assume is used only in streaming jobs, where
single-use connections can be used in the traditional way. Alternatively, we can
provide XA data links with a new connector type, JDBC-XA.

# Commands

## Create, drop a data link

```sql
CREATE [OR REPLACE] DATA LINK [IF NOT EXISTS] <name>
TYPE <connector name>
OPTIONS ( … );

DROP DATA LINK [IF EXISTS] <name>;
```

Alter is not supported. Replacing or dropping a data link created in config will
throw an error.

## Test data link

Users might need to test whether the connection properties they provided are
correct. It was proposed to use:

```sql
TEST CONNECTION /* … the rest equal to CREATE */
```

It was meant to be able to test a connection before it was created, because it
assumed that we won’t be able to modify the data link later. We **will not
implement this command**, because we will provide means to modify the data link.

To test connections for already defined data link, we can use one of these:

```sql
ALTER DATA LINK <data link name> TEST CONNECTION;
TEST CONNECTION <data link name>;
```

The `ALTER .. TEST` command is confusing, we’re not altering the data link in
any way. It’s inspired by `ALTER JOB &lt;name> RESTART`, which we already
support.

The second form with `TEST` keyword introduces a new initial keyword, and it’s
also a completely new command for the user to remember. Also the CONNECTION
keyword isn’t use anywhere else. With `TEST DATA LINK` I also find it confusing.

**I propose to not implement any dedicated command for testing the connection.
Instead, to test a data link, one can use `SHOW RESOURCES` command.**

## Use the data link in a mapping

To create a mapping using an existing data link, use this command:

```sql
CREATE [OR REPLACE] [EXTERNAL] MAPPING [IF NOT EXISTS] <mapping name>
[EXTERNAL NAME <resource name>]
(  /* columns */ )
DATA LINK <data link name> [OBJECT TYPE <object type>]
[ OPTIONS … ]
```

That is, instead of `CONNECTOR TYPE &lt;connector type>` the user will refer to
a data link. The connector can throw an error, if some option in the mapping
conflicts with the options in the data link definition (e.g. a different target
IP address).

### Mapping without a connector

For any new SQL connectors the only way to create a mapping will be only through
a data link. We’ll keep the direct mapping for current connectors. The reason is
to avoid _having two ways to do the same thing._ Secondly, the connection
lifecycle is better defined with a data link, currently users might be surprised
to learn that if you have a Kafka mapping without a data link, and execute 10
INSERT commands, the engine connects and disconnects to Kafka 10 times.

## SHOW commands

```sql
SHOW RESOURCES FOR <data link name>;

SHOW DATA LINKS;

SELECT *
FROM information_schema.data_links;
```

The list of data links should also include links created in the config. In the
`information_schema` there should be a flag for such data links.

## GET_DDL system function

This is an alternative to the proposed DESCRIBE command. The DESCRIBE command is
used by SQL server and it doesn’t produce DDL, but it’s more like an
information_schema query: a table with columns and their types.

PostgreSQL uses a command-line tool `pg_dump`, it’s not possible to get the DDL
through SQL. Oracle uses `dbms_metadata.get_ddl`, which we can mimic. Its
arguments will be:

* **namespace**: allowed values: ‘relation’, ‘datalink’. ‘relation’ is used for
  mappings, views, types (and actual tables in the future, if we have them).
* **object_name**
* **schema** (optional)

```sql
SELECT system.get_ddl('relation', 'my_mapping');
SELECT system.get_ddl('datalink', 'my_db_link');
```

The function will be in the `system` schema.

# Dependency management

We will use lazy dependency management, as with other objects. When a data link
is modified or deleted, dependent mappings or queries are unaffected, they
continue to run with the old settings.

When a new mapping is created, it can optionally validate the settings. When a
query which uses a mapping based on a data link is submitted, the current
settings of the data link are used, and subsequent changes aren’t reflected, nor
do they change the query failure.

If a data link is dropped, all the connections opened from it should remain
active, until they are returned. For a shared connection, refcounting should be
implemented. For pooled data links, idle connections are closed at the time of
dropping, others are closed when returned to the pool.

When a data link is modified concurrently to a starting job, it can happen that
different processors will obtain a connection from a different version of the
link. It would be possible to guard against this, for example by
ProcessorMetaSupplier taking some data link hash, and each processor will check
that hash when checking out a connection. But we propose to not implement this
in the initial version, as it’s not trivial (every connector has to implement
it), and because we consider that the users will be able to understand, if it
happens.

# Access rights

The data links can contain sensitive information such as passwords. We will have
these privileges related to data links:

* new SQL actions `create-datalink` and `drop-datalink`. To CREATE OR REPLACE a
  data link, one needs permission to both actions.
* new SQL action: `view-datalink`. Without this permission the user won’t be
  able to see data link options, which might contain passwords. This includes
  the `get_ddl` function, or views in information_schema, if we have them. Also
  note that the `__sql.catalog` IMap exposes the data link options in a
  non-documented way, so access to this map must be denied.
* To DROP data link, we require both `view-datalink` and `drop-datalink`.

There will be no way to grant/revoke access to individual data links, or to
individual remote resources, every user will be able to access every data link.
But the user has to have access to the target resource, which is already covered
by the [connector
permissions](https://docs.hazelcast.com/hazelcast/5.2/security/native-client-security#connector-permission).

# Tasks and optimistic estimates

**Platform tasks**

1. Add/refactor of basic APIs, adapt JDBC data link to this TDD (prereq. for all other tasks)  
   [https://hazelcast.atlassian.net/browse/HZ-2013](https://hazelcast.atlassian.net/browse/HZ-2013) - **6MD**
2. Implement a data link for Kafka - [https://hazelcast.atlassian.net/browse/HZ-1985](https://hazelcast.atlassian.net/browse/HZ-1985)  **3 MD**
3. Implement a data link for MongoDB - [https://hazelcast.atlassian.net/browse/HZ-1633](https://hazelcast.atlassian.net/browse/HZ-1633)  **1 MD**
4. Implement a data link for remote IMap - [https://hazelcast.atlassian.net/browse/HZ-1433](https://hazelcast.atlassian.net/browse/HZ-1433)  **1 MD**
5. Implement missing connections privileges for kafka, mongoDB  **(Optional, out of scope)**

**SQL tasks**

6. Implement CREATE, DROP commands https://hazelcast.atlassian.net/browse/HZ-1955  **3MD**
7. Implement background checker https://hazelcast.atlassian.net/browse/HZ-2041 **2MD**
8. Implement CREATE MAPPING with a datalink https://hazelcast.atlassian.net/browse/HZ-2040 **2MD**
9. Implement SHOW RESOURCES https://hazelcast.atlassian.net/browse/HZ-2039 **1MD**
10. Implement GET_DDL - https://hazelcast.atlassian.net/browse/HZ-2024 **5MD**
11. Implement information_schema  **2MD**
12. Implement SQL privileges  **1MD**

All tasks can be done in parallel after task 1 is done.

<!-- Footnotes -->

## Notes

[^1]:

Future work for this kind of jobs is to avoid running them on all members. It
does not make sense to launch a distributed job to insert one row. This is not
part of this TDD.

[^2]:

E.g. rolled back

[^3]:

Adding support for user-created schemas was discussed, but it’s not on the
roadmap yet.

[^4]:

It should also validate the external name to avoid SQL injection.
