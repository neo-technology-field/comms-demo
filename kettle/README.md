# Kettle Job for Loading Enron Data

The complete Enron db can be ETL'd into Neo4j in approximately 2
minutes using the provided Kettle job. It also serves as a simple
example of how to build an online bulk-loading pipeline featuring:

- Multi-step Job using multiple isolated Tranformations
- Batched Cypher transactions utilizing `UNWRAP`
- Connectivity details in Kettle _Environments_

The main job (_bootstrap.kjb_) looks like:

![The Bootstrapping Job](/img/bootstrap.png?raw=true)

In words, at a highlevel:

1. Check our connection to MySQL before we proceed
2. Check our connection to Neo4j
3. Run some Cypher to establish a basic schema
4. Load "well-known" Enron Employees (`Person` & `EmailAddress`) from
   the `employeelist` table, establishing who `:HAS_EMAIL` accounts.
5. Derive more `EmailAddress`es from known messages in the `messages`
   table. This includes non-Enron people and services.
6. Load the `Message`s, establishing which `EmailAddress` they were
   `:SENT` to
7. Fill in any gaps in known `EmailAddress`es from our recipient info
   and load the `:TO`/`:CC` relationships.


## Getting Kettle

See [http://kettle.be/] for the Kettle Remix distro containing the
Neo4j plugin already included. (Also, make sure you've got Java 8
kicking around as it's required.)

You'll also need to install the [PDI MySQL
Plugin](https://ctools.pentaho.com/files/pdi-mysql-plugin/pdi-mysql-plugin-TRUNK-SNAPSHOT.zip)
either manually or via the Marketplace feature.


## Before You Get Started

Once you've got Kettle installed, the MySQL database restored per the
[instructions](../README.md), and a target Neo4j server, make note of
the following details:

- `ENRON_DB_HOST` -- IP or hostname of your MySQL v5 system
- `ENRON_DB_PORT` -- TCP port for MySQL
- `ENRON_DB_NAME` -- Name of the MySQL database (e.g. `enron`)
- `ENRON_DB_USER` -- MySQL database user
- `ENRON_DB_PASSWORD` -- MySql database password
- `NEO4J_HOST` -- IP or hostname of target Neo4j server
- `NEO4J_BOLT_PORT` -- TCP port for the Neo4j Bolt connection
- `NEO4J_BROWSER_PORT` -- TCP port for the Neo4j http connection
- `NEO4J_USERNAME` -- Neo4j admin user for populating graph
- `NEO4J_PASSWORD` -- Neo4j admin user password


## Configuring a Kettle Environment

Assuming you have Kettle Remix installed, you can either use the Spoon
gui or the following command line steps.

1. Make sure Java 8 (JRE or JDK) is set in the case you have multiple
   versions of Java:

```sh
$ export JAVA_HOME=<path to JDK/JRE 8>
```

2. To follow along, you might want to set `KETTLE_HOME` as
   well. Change to match where you installed Kettle Remix.

```sh
$ export KETTLE_DIR=/Applications/Kettle
```

3. Set a path to where you've cloned/checked-out the Kettle files. It
   should be the same directory this README is located.

```sh
$ export WORK_DIR=/Users/dave/src/neo4j/comms-demo/kettle
```

4. Create and configure a Kettle Environment using values that match
   your workstation or server configuration (change values as needed):

```sh
$ sh ${KETTLE_DIR}/maitre.sh \
    -C EnronDemo=${WORK_DIR} \
    -V ENRON_DB_HOST=192.168.56.101 \
    -V ENRON_DB_PORT=33060 \
    -V ENRON_DB_NAME=enron \
    -V ENRON_DB_USER=neo4j \
    -V ENRON_DB_PASSWORD=neo4j \
    -V NEO4J_HOST=localhost \
    -V NEO4J_BOLT_PORT=7687 \
    -V NEO4J_BROWSER_PORT=7474 \
    -V NEO4J_USERNAME=neo4j \
    -V NEO4J_PASSWORD=password
```

5. You should see some feedback indicating success, but you can
   confirm your environment by checking the following directory for
   the XML representation of the config:

```sh
$ cat /Users/dave/.kettle/environment/metastore/pentaho/Kettle\ Environment/EnronDemo.xml
```


## Running the Bootstrapping Job

Once you've got your Environment configured either via the Spoon gui
or command line, you can either launch Spoon and run the
[bootstrap](./bootstrap.kjb) job or use the same `maitre.sh` cli tool
to run headless:

```sh
$ sh ${KETTLE_DIR}/maitre.sh -e EnronDemo -f bootstrap.kjb
```

> Note: You may see some stack traces barfed out complaining about
> "Transaction can't be committed....". That's a known issue at the
> moment with transformation design.

At time of writing (1 Nov 2019), the job should produce:

- 328,462 nodes (3 labels)
- 2,063,763 relationships (4 types)
