package com.sysmetrics.main;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

public class SysInfoCQL
        implements SysInfoDB {

    private Cluster cluster;
    private Session session;
    private Collection<DBException> exceptions = new ArrayList<DBException>();

    public SysInfoCQL(String contactPoint, String namespace, String username, String password) {
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

}
