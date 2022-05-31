package com.sysmetrics.main;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

public class ConsoleLog {
    public static SysInfoGatherer initGathererWithTOML(Path pathToProperties)
            throws IOException {
        TomlParseResult result = Toml.parse(pathToProperties);

        var sysInfoBuilder = SysInfoGatherer.newBuilder();

        if (result.getBoolean("network_usage.enabled", () -> false)) {
            sysInfoBuilder = sysInfoBuilder.initNetworkIFs();
        }
        if (result.getBoolean("cpu_usage.enabled", () -> false)) {
            sysInfoBuilder = sysInfoBuilder.initCPU();
        }
        if (result.getBoolean("disk_usage.enabled", () -> false)) {
            sysInfoBuilder = sysInfoBuilder.initDisk(result.getString("disk_usage.point", () -> "./"));
        }
        if (result.getBoolean("memory_usage.enabled", () -> false)) {
            sysInfoBuilder = sysInfoBuilder.initMemory();
        }
        if (result.getBoolean("dns_latency.enabled", () -> false)) {
            String[] domains = result.getArray("dns_latency.servers")
                    .toList()
                    .toArray(String[]::new);
            sysInfoBuilder = sysInfoBuilder.initDNS(domains);
        }
        if (result.getBoolean("cql_logging.enabled", () -> false)) {
            sysInfoBuilder.initLogCQL(result.getString("cql_logging.contact_point"),
                    result.getString("cql_logging.namespace", () -> "SysInfoDefault"),
                    result.getString("cql_logging.username", () -> ""),
                    result.getString("cql_logging.password", () -> ""));
        }

        return sysInfoBuilder.build();
    }

    public static void main(String[] args) {
        try {
            SysInfoGatherer sysInfo = initGathererWithTOML(Paths.get("settings.toml"));

            var mapper = new ObjectMapper(new YAMLFactory().disable(Feature.AUTO_CLOSE_TARGET));

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

            executor.scheduleAtFixedRate(() -> {
                var metrics = sysInfo.updateMetrics();
                try {
                    mapper.writeValue(System.out, metrics);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }, 0, 3, TimeUnit.SECONDS);

        } catch (IOException e) {
            System.out.println("An exception occured when initializing metrics!");
            e.printStackTrace();
        }
    }
}