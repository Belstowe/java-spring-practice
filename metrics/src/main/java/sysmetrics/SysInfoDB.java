package sysmetrics;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

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
    public void initTable(String tableName, Set<String> keys);

    public void insert(String tableName, Map<String, Double> values);

    public Collection<DBException> getExceptions();
}
