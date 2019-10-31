// Now for the rough stuff...processing the recipientinfo table

// Partition our table. Luckily it's using an Integer as primary key
CALL apoc.load.jdbc(
  "jdbc:mysql://192.168.56.101:33060/enron?user=neo4j&password=neo4j",
  "SELECT min(rid) as low, max(rid) as high from recipientinfo"
) YIELD row as bounds

// Construct boundaries to SELECT data between
UNWIND apoc.coll.pairs(range(bounds.low, bounds.high + 1, 25000)) as pair
CALL apoc.load.jdbc(
  "jdbc:mysql://192.168.56.101:33060/enron?user=neo4j&password=neo4j",
  "SELECT * FROM recipientinfo WHERE rid >= " + pair[0] + " AND rid < " + coalesce(pair[1], pair[0]) + " LIMIT 25001;"
) YIELD row

// For each recipientinfo row, construct a relationship based on the type
MATCH (m:Message {mid: row.mid})
MATCH (r:Person {email: row.rvalue})
WITH row, m, r
CALL apoc.create.relationship(m, row.rtype, {on: localdatetime(m.date)}, r) YIELD rel
RETURN COUNT(rel);
