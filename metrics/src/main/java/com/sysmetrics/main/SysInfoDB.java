package com.sysmetrics.main;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
class DBException {
    private String statement;
    private String message;
}

public interface SysInfoDB extends AutoCloseable {
    public void initTable(String tableName, Collection<String> keys);

    public void insert(String tableName, Map<String, Double> values);

    public String[] getGroups();

    // Map<Group, Map<Timestamp, Map<Metric, Value>>>
    public Map<String, Map<String, Map<String, Double>>> selectTimeRange(
            LocalDateTime from, LocalDateTime to, Collection<String> groups);

    public Collection<DBException> getExceptions();

    void destroy();
}
