# Communications Demo
__Building a Communication Surveillance Network__

## Background & Prerequisites
This demo relies on loading data from a MySQL instance containing the
[Enron Email Corpus](https://en.wikipedia.org/wiki/Enron_Corpus). It
contains approximately 252k emails circa 2001 between various internal
folks at Enron.

It will consume ~2.2G of disk space once restored, so make sure you
have some free space.

1. MySQL --OR-- Docker & Compose
You can provide your own MySQL instance if you'd like, but if you have
Docker and can have installed [Docker
Compose](https://docs.docker.com/compose/install/) you can use Docker instead.

2. GCP Access to the "neo4j-se-demodata" bucket
The database dump is in GCP:
`gs://neo4j-se-demodata/enron-mysqldump.sql.gz`

3. Google Cloud SDK
You need the Google Cloud SDK installed so `gcloud` is available on
your path. You also need to be authenticated (`gcloud auth login`). It
will provide the `gsutil` program for cli access to the storage bucket.

4. APOC and MySQL JDBC plugins


## Populating the MySQL Database

1. Start the database.

```bash
$ DOCKER_UID=$(id -u) DOCKER_GID=$(id -g) docker-compose start
```

2. Get the root password (if you're starting it up for the first time).
```bash
$ docker-compose logs | grep "GENERATED ROOT PASSWORD" | awk '{ print $NF }
```

3. Change the password to something you know:

```bash
docker exec -it comms-demo_mysql_1 mysqladmin --user=root -p password
```

Follow the prompts. From here on after, let's say you set it to: `PASSWORD`

4. Load the data. You can do this all in one go straight from GCP.

```bash
$ gsutil cat gs://neo4j-se-demodata/enron-mysqldump.sql.gz \
    | pv -a | gunzip -v \
    | docker exec -i comms-demo_mysql_1 sh -c 'mysql -u root --password=ooT4jizoozah5eey9fo1weiwohtho3vi'
```

> Note: I'm using `pv` in the above command. I recommend putting it in
> there to report on activity as it loads...it'll give you some
> details on data transfer through the pipes. You can get it via `apt
> install pv` or `yum install pv`. See the pv website for details:
> http://www.ivarch.com/programs/pv.shtml

5. Verify the data is loaded

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
|          151 |  252759 |       2064442 |         54778 |
+--------------+---------+---------------+---------------+
```

6. Lastly, create a user to access the DB remotely with (from Neo4j)

We're going to connect remotely via JDBC, so create a new user
(`neo4j`) with a password (`neo4j`) with a "wildcard" host (`%`) value:

```sql
CREATE USER 'neo4j'@'%' IDENTIFIED BY 'neo4j';
GRANT SELECT ON enron.* TO 'neo4j'@'%';
```

## Populating the Graph
We're going to use APOC to populate the graph. Make sure you have the
following plugins installed:

- APOC: https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases
- MySQL JDBC Driver: https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-8.0.18.tar.gz

...
**TODO: Write some CYPER + APOC**

## Who to ping if you need help

Email: dave.voutila@neo4j.com or ping @dave.voutila on Slack
