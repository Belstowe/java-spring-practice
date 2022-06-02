package com.sysmetrics.main;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class SysInfoCQL
        implements SysInfoDB {

    private Cluster cluster;
    private Session session;
    private String namespace;
    private Collection<DBException> exceptions = new ArrayList<DBException>();

    public SysInfoCQL(String contactPoint, String namespace, String username, String password) {
        this.namespace = namespace;
        var clusterBuilder = Cluster.builder()
                .withoutJMXReporting()
                .addContactPoint(contactPoint);
        if (username != "") {
            clusterBuilder = clusterBuilder.withCredentials(username, password);
        }
        cluster = clusterBuilder.build();
        session = cluster.connect();

        session.execute("CREATE KEYSPACE IF NOT EXISTS " + namespace
                + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 2};");
        session.execute("USE " + namespace);
    }

    @Override
    public void close() {
        cluster.close();
    }

    @Override
    public void initTable(String tableName, Collection<String> keys) {
        String createTableStatement = "CREATE TABLE IF NOT EXISTS " + tableName;
        createTableStatement += " ( infodate date, infotime time, ";
        for (var key : keys) {
            createTableStatement += key + " double, ";
        }
        createTableStatement += "PRIMARY KEY ((infodate), infotime) );";
        try {
            session.execute(createTableStatement);
        } catch (Exception e) {
            exceptions.add(new DBException(createTableStatement, e.getMessage()));
        }
    }

    @Override
    public void insert(String tableName, Map<String, Double> values) {
        var infoDate = "'" + DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now()) + "'";
        var infoTime = "'" + DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now()) + "'";
        String insertStatement = "INSERT INTO " + tableName + " (infodate, infotime";
        for (var key : values.keySet()) {
            insertStatement += ", " + key;
        }
        insertStatement += ") VALUES (" + infoDate + ", " + infoTime;
        for (var value : values.values()) {
            insertStatement += ", " + Double.toString(value);
        }
        insertStatement += ");";
        try {
            session.execute(insertStatement);
        } catch (Exception e) {
            exceptions.add(new DBException(insertStatement, e.getMessage()));
        }
    }

    @Override
    public Collection<DBException> getExceptions() {
        return exceptions;
    }

    @Override
    public String[] getGroups() {
        return cluster.getMetadata()
                .getKeyspace(namespace)
                .getTables()
                .stream()
                .map((metadata) -> {
                    return metadata.getName();
                })
                .toArray(String[]::new);
    }

    private Map<String, Map<String, Double>> requestTimeRange(
            LocalDate date, LocalTime from, LocalTime to, String table) {
        String selectStatement = "SELECT * FROM " + table + " WHERE infodate = '" + date + "'";
        if (from != null) {
            selectStatement += " AND infotime >= '" + from.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "'";
        }
        if (to != null) {
            selectStatement += " AND infotime <= '" + to.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "'";
        }
        selectStatement += ";";
        ResultSet rs = session.execute(selectStatement);
        var columns = rs.getColumnDefinitions();
        var dateString = date.toString();

        var result = new TreeMap<String, Map<String, Double>>();
        for (Row row : rs) {
            var metaTime = dateString + "T" + LocalTime.ofNanoOfDay(row.getTime("infotime")).toString();
            var metrics = new HashMap<String, Double>();
            for (int i = 2; i < columns.size(); i++) {
                metrics.put(columns.getName(i), row.getDouble(i));
            }
            result.put(metaTime, metrics);
        }
        return result;
    }

    @Override
    public Map<String, Map<String, Map<String, Double>>> selectTimeRange(
            LocalDateTime from, LocalDateTime to, Collection<String> groups) {
        var fromDate = from.toLocalDate();
        var toDate = to.toLocalDate();
        var result = new HashMap<String, Map<String, Map<String, Double>>>();

        for (var date = fromDate; date.isBefore(toDate) || date.isEqual(toDate); date = date.plusDays(1)) {
            var fromTime = (date.isEqual(fromDate) ? from.toLocalTime() : null);
            var toTime = (date.isEqual(toDate) ? to.toLocalTime() : null);
            for (var group : groups) {
                var pastMetrics = result.get(group);
                var thisDateMetrics = requestTimeRange(date, fromTime, toTime, group);
                if (pastMetrics != null) {
                    thisDateMetrics.forEach(
                        (key, value) -> pastMetrics.merge(key, value, (v1, v2) -> v1)
                    );
                } else {
                    result.put(group, requestTimeRange(date, fromTime, toTime, group));
                }
            }
        }

        return result;
    }

    @Override
    public void destroy() {
        session.execute("DROP KEYSPACE " + namespace + ";");
    }
}
