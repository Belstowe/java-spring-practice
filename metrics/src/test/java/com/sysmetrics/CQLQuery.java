package com.sysmetrics;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.sysmetrics.main.SysInfoCQL;
import com.sysmetrics.main.SysInfoDB;

@RunWith(JUnit4.class)
public class CQLQuery {
    static SysInfoDB cqlInterface;

    @BeforeClass
    public static void setUp() {
        cqlInterface = new SysInfoCQL("127.0.0.1", "systest", "", "");
        cqlInterface.initTable("foo", Arrays.asList("indata", "outdata"));
        cqlInterface.initTable("bar", Arrays.asList("testdata"));
        assertTrue(cqlInterface.getExceptions().size() == 0);
    }

    @Test
    public void tablesVerified() {
        List<String> expectedTables = Arrays.asList("foo", "bar");
        assertTrue(Arrays.stream(cqlInterface.getGroups()).allMatch((table) -> {
            return expectedTables.contains(table);
        }));
        assertTrue(cqlInterface.getGroups().length == expectedTables.size());
    }

    @AfterClass
    public static void tearDown() {
        cqlInterface.destroy();
        try {
            cqlInterface.close();
        } catch (Exception e) {
        }
        assertTrue(cqlInterface.getExceptions().size() == 0);
    }
}
