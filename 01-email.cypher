CREATE CONSTRAINT ON (m:Message) ASSERT m.mid IS UNIQUE;

CALL apoc.periodic.iterate(
  'CALL apoc.load.jdbc(
   "jdbc:mysql://192.168.56.101:33060/enron?user=neo4j&password=neo4j",
   "SELECT mid, sender, date, message_id, subject FROM message;") YIELD row
   MATCH (s:Person {email: row.sender})
   RETURN s, row',
  'MERGE (m:Message {mid: row.mid})
    ON CREATE SET
      m = row,
      m.date = localdatetime(row.date)
   MERGE (s)-[:SENT {on:localdatetime(m.date)}]->(m);',
  { batchSize: 2500, parallel: true, concurrency: 4 }
);
