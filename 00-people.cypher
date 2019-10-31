// First step: Load all the People assuming they have unique Emails
CREATE CONSTRAINT ON (p:Person) ASSERT p.email IS UNIQUE;

// First, known employees
CALL apoc.load.jdbc(
  "jdbc:mysql://192.168.56.101:33060/enron?user=neo4j&password=neo4j",
  "SELECT * FROM employeelist;"
) YIELD row
MERGE (p:Person {email:row.Email_id})
  ON CREATE SET
    p.firstName = row.firstName,
    p.lastName = row.lastName,
    p.fullName = row.firstName + " " + row.lastName;

// Second, from recipient data
CALL apoc.load.jdbc(
  "jdbc:mysql://192.168.56.101:33060/enron?user=neo4j&password=neo4j",
  "SELECT DISTINCT(rvalue) FROM recipientinfo WHERE rvalue NOT IN (SELECT Email_id FROM employeelist);"
) YIELD row
MERGE (p:Person {email:row.rvalue})
  ON CREATE SET p.email = row.rvalue;

// Third, derive any other people from other known Senders
CALL apoc.load.jdbc(
  "jdbc:mysql://192.168.56.101:33060/enron?user=neo4j&password=neo4j",
  "SELECT DISTINCT(sender) FROM message WHERE
     sender NOT IN (SELECT Email_id FROM employeelist) AND
     sender NOT IN (SELECT DISTINCT(rvalue) FROM recipientinfo WHERE
       rvalue NOT IN (SELECT Email_id FROM employeelist));"
) YIELD row
MERGE (p:Person {email:row.sender})
  ON CREATE SET p.email = row.sender;