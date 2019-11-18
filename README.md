# Communications Surveillance Demo
__Building a Communication Surveillance Network__

![Project Stanley](./img/enron-project-stanley.png?raw=true)

## 0. Prerequisites
This demo relies on loading data from a MySQL instance containing the
[Enron Email Corpus](https://en.wikipedia.org/wiki/Enron_Corpus). It
contains approximately 252k emails circa 2001 between various internal
folks at Enron.

It will consume ~2.2G of disk space once restored, so make sure you
have some free space.

1. **MySQL v5 *OR* Docker & Compose**

You can provide your own MySQL v5 instance if you'd like, but if you have
Docker and can have installed [Docker
Compose](https://docs.docker.com/compose/install/) you can use Docker instead.

> Note: There are some known headaches around Kettle pulling from
> MySQL v8, so please, save yourself the trouble and use v5.

2. **GCP Access to the "neo4j-se-demodata" bucket**

The database dump is in GCP:
`gs://neo4j-se-demodata/enron-mysqldump_v5.sql.gz`

Or in AWS S3 at:
`<TBD>`

3. **Google Cloud SDK** *OR* **AWS CLI**

You need the Google Cloud SDK installed so `gcloud` is available on
your path. You also need to be authenticated (`gcloud auth login`). It
will provide the `gsutil` program for cli access to the storage bucket.

If you don't want to use GCP, use the file in S3.


4. **APOC and MySQL JDBC plugins**

Data loading will be done via JDBC APOC procedures.


## 1. Populating the MySQL Database

1. Start the database.

```bash
$ DOCKER_UID=$(id -u) DOCKER_GID=$(id -g) docker-compose up -d
```

2. After a moment, get the root password (if you're starting it up for the first time).
```bash
$ docker-compose logs | grep "GENERATED ROOT PASSWORD" | awk '{ print $NF }'
```

3. Change the password to something you know:

```bash
docker exec -it comms-demo_mysql_1 mysqladmin --user=root -p password
```

Follow the prompts. From here on after, let's say you set it to: `PASSWORD`

4. Load the data. You can do this all in one go straight from GCP.

```bash
$ gsutil cat gs://neo4j-se-demodata/enron-mysqldump_v5.sql.gz \
    | pv -rabt \
    | gunzip \
    | docker exec -i comms-demo_mysql_1 sh -c 'mysql enron -u root'
```

> Note: I'm using `pv` in the above command. I recommend putting it in
> there to report on activity as it loads...it'll give you some
> details on data transfer through the pipes. You can get it via `apt
> install pv` or `yum install pv`. See the pv website for details:
> http://www.ivarch.com/programs/pv.shtml

5. Verify the data is loaded.

You can use the embedded `mysql` client via:

```bash
$ docker exec -it comms-demo_mysql_1 mysql -u root
```

You should see 4 tables...
```sql
SHOW TABLES FROM enron;
```
```
+-----------------+
| Tables_in_enron |
+-----------------+
| employeelist    |
| message         |
| recipientinfo   |
| referenceinfo   |
+-----------------+
```

And have the following counts...

```sql
USE enron;
SELECT
    (SELECT COUNT(*) FROM employeelist) as employeelist,
    (SELECT COUNT(*) FROM message) as message,
    (SELECT COUNT(*) FROM recipientinfo) as recipientinfo,
    (SELECT COUNT(*) FROM referenceinfo) as referenceinfo;
```

```
+--------------+---------+---------------+---------------+
| employeelist | message | recipientinfo | referenceinfo |
+--------------+---------+---------------+---------------+
|          149 |  252759 |       2064442 |         54778 |
+--------------+---------+---------------+---------------+
```

6. Lastly, create a user to access the DB remotely with (from Neo4j)

We're going to connect remotely via JDBC, so create a new user
(`neo4j`) with a password (`neo4j`) with a "wildcard" host (`%`) value:

```sql
CREATE USER 'neo4j'@'%' IDENTIFIED BY 'neo4j';
GRANT SELECT ON enron.* TO 'neo4j'@'%';
```


## 2. Populating the Graph
The following steps are for building out the graph in Neo4j. You
should run these in order.

### Loading the Email Messages
The primary content are emails in the MySQL database. We've got two
ways to load them: Kettle and APOC.

#### Option 1: Using Kettle
Grab the Kettle Remix and follow my [instructions](./kettle/README.md)
for running the provided Kettle job. It'll produce a data model
resembling:

![db.schema output](./img/db-schema-v3.png?raw=true)

In Cypher:

```
(:Person)-[:HAS_EMAIL]->(:EmailAddress)->[:SENT]->(:Message)-[:TO|CC]->(:Message)
```

#### Option 2: Using APOC
> Note: This is my original approach and needs to be updated.

We're going to use APOC to populate the graph. Make sure you have the
following plugins installed:

- APOC: https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases
- MySQL JDBC Driver: https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-5.1.48.tar.gz

1. Load Employees -- [00-people.cypher](./00-people.cypher)
2. Load Messages -- [01-email.cypher](./01-email.cypher)
3. Load Relationships -- [02-relationships.cypher](./02-relationships.cypher)

### Threading Messages
The Enron dataset doesn't contain email "threads" for various reasons
having to do with email, how the data was collected, etc. All beyond
the scope of this demo.

However, to make things interesting, we're going to take a naive
approach to identifying simple communication threads.

#### Naive Methodology
1. Over the corpus, analyze each _subject_ to determine the "root" of
   the subject and the "type".

   ```
   "Something Awesome" ->     {:root "Something Awesome" :type :original}
   "re: Something Awesome" -> {:root "Something Awesome" :type :re}
   "FW: Something Awesome" -> {:root "Something Awesome" :type :fw}
   ```

2. Reduce the corpus on equivalent "roots", collecting them together.

3. Order each collection by _date_

4. Sanity check, removing any threads with multiple "originals"

5. Declare victory.

> Note: Currently, forwards do not "fork" an email thread. That's
> future work.

#### The Execution
I whipped up some simple [Clojure code](./enron-threading) that will
connect to the MySQL database, pull out all the messages, perform the
analysis, and dump out a simple mapping of relationships to _stdout_
like:

```
from,to
12451,12522
12522,15162
15233,22222
22222,23323
23323,55555
...
```

> Note: for now the connection details are hardcoded, but you can
> modify `core.clj` to update connection details to your MySQL db,
> install [Leiningen](https://leiningen.org/) and just `lein run`

Where each value is the _mid_ (i.e. message id). Loading the output as
a CSV is trivial Cypher:

```cypher
LOAD CSV WITH HEADERS FROM 'file:///threads.csv' AS row
MATCH (left:Message {mid:toInteger(row.from)})
MATCH (right:Message {mid:toInteger(row.to)}) WHERE left.mid <> right.mid
MERGE (left)-[:NEXT]->(right)
RETURN COUNT(*)
```


### Identifying and Tagging Entities

**TODO**: add extraction of topics like "Project Stanley" et. al.

## 3. Exploring the Data

**TODO**: demo story!!!

For now, here's an example of an email exchange with replies and
someone being dropped out of a cc list in a reply:

![example reply to thread](./img/example-01.png?raw=true)


---
#### Who to ping if you need help

Email: dave.voutila@neo4j.com or ping `@dave.voutila` on Slack
