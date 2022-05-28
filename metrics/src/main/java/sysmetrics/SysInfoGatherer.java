package sysmetrics;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

import dns.PingUtility;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

interface UpdateCallable {
    void update();
}

public class SysInfoGatherer {
    private List<NetworkIF> networkInterfaces;
    private CentralProcessor cpu;
    private long[][] cpuLoadTicks;
    private GlobalMemory ram;
    private FileStore disk;
    private String[] dnsServers;
    private Cluster cqlCluster;
    private Session cqlSession;
    private boolean cqlTablesInitialized = false;

    private Map<String, Map<String, Double>> metrics = new HashMap<String, Map<String, Double>>();
    private List<UpdateCallable> updaters = new ArrayList<UpdateCallable>();

    private SysInfoGatherer() {
    }

    private void executeStatement(String statement) {
        try {
            cqlSession.execute(statement);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (cqlCluster != null) {
            cqlCluster.close();
        }
    }

    public Map<String, Map<String, Double>> updateMetrics() {
        for (UpdateCallable updater : updaters) {
            updater.update();
        }
        if (cqlCluster != null) {
            if (!cqlTablesInitialized) {
                for (var entry : metrics.entrySet()) {
                    String createTableStatement = "CREATE TABLE IF NOT EXISTS " + entry.getKey() + " ( ";
                    createTableStatement += "time_got timestamp PRIMARY KEY";
                    for (var subEntry : entry.getValue().keySet()) {
                        createTableStatement += ", ";
                        createTableStatement += subEntry + " double";
                    }
                    createTableStatement += " );";
                    executeStatement(createTableStatement);
                }
                cqlTablesInitialized = true;
            }
            var timestamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now());
            timestamp = timestamp.substring(0, timestamp.length() - 8);
            for (var entry : metrics.entrySet()) {
                String insertStatement = "INSERT INTO " + entry.getKey() + " (time_got";
                for (var subKey : entry.getValue().keySet()) {
                    insertStatement += ", ";
                    insertStatement += subKey;
                }
                insertStatement += ") VALUES ('" + timestamp + "'";
                for (var subValue : entry.getValue().values()) {
                    insertStatement += ", ";
                    insertStatement += Double.toString(subValue);
                }
                insertStatement += ");";
                executeStatement(insertStatement);
            }
        }
        return metrics;
    }

    public static Builder newBuilder() {
        return new SysInfoGatherer().new Builder();
    }

    public class Builder {
        private SystemInfo sysInfo = new SystemInfo();
        private HardwareAbstractionLayer sysHAL = sysInfo.getHardware();

        private Builder() {
        }

        public Builder initNetworkIFs() {
            SysInfoGatherer.this.networkInterfaces = sysHAL.getNetworkIFs();
            SysInfoGatherer.this.metrics.put("network_usage", new HashMap<String, Double>());

            SysInfoGatherer.this.updaters.add(() -> {
                for (NetworkIF networkInterface : networkInterfaces) {
                    String interfaceName = networkInterface.getName();
                    long oldRecv = networkInterface.getBytesRecv();
                    long oldSent = networkInterface.getBytesSent();
                    networkInterface.updateAttributes();
                    long newRecv = networkInterface.getBytesRecv();
                    long newSent = networkInterface.getBytesSent();
                    metrics.get("network_usage").put(interfaceName + "_recv",
                            Double.valueOf(newRecv - oldRecv));
                    metrics.get("network_usage").put(interfaceName + "_sent",
                            Double.valueOf(newSent - oldSent));
                }
            });

            return this;
        }

        public Builder initCPU() {
            SysInfoGatherer.this.cpu = sysHAL.getProcessor();
            SysInfoGatherer.this.cpuLoadTicks = cpu.getProcessorCpuLoadTicks();
            SysInfoGatherer.this.metrics.put("cpu_usage", new HashMap<String, Double>());

            SysInfoGatherer.this.updaters.add(() -> {
                double[] recentUsage = cpu.getProcessorCpuLoadBetweenTicks(cpuLoadTicks);
                for (int i = 0; i < recentUsage.length; i++) {
                    metrics.get("cpu_usage").put("cpu" + Integer.toString(i + 1), recentUsage[i]);
                }
                cpuLoadTicks = cpu.getProcessorCpuLoadTicks();
            });

            return this;
        }

        public Builder initMemory() {
            SysInfoGatherer.this.ram = sysHAL.getMemory();
            SysInfoGatherer.this.metrics.put("memory_usage", new HashMap<String, Double>());

            SysInfoGatherer.this.updaters.add(() -> {
                double usedToTotal = 1 - (ram.getAvailable() / (double) ram.getTotal());
                metrics.get("memory_usage").put("used_to_total", usedToTotal);
            });

            return this;
        }

        public Builder initDisk(String pathOnDisk)
                throws IOException {
            SysInfoGatherer.this.disk = Files.getFileStore(Paths.get(pathOnDisk));
            SysInfoGatherer.this.metrics.put("disk_usage", new HashMap<String, Double>());

            SysInfoGatherer.this.updaters.add(() -> {
                double usedToTotal;
                try {
                    usedToTotal = 1 - (disk.getUsableSpace() / (double) disk.getTotalSpace());
                } catch (IOException e) {
                    usedToTotal = 1.0;
                }
                metrics.get("disk_usage").put("used_to_total", usedToTotal);
            });

            return this;
        }

        public Builder initDNS(String... domains) {
            SysInfoGatherer.this.dnsServers = domains;
            SysInfoGatherer.this.metrics.put("dns_latency", new HashMap<String, Double>());

            SysInfoGatherer.this.updaters.add(() -> {
                for (String dnsServer : dnsServers) {
                    String cqlIPRepr = "ip_" + dnsServer.replace('.', '_');
                    try {
                        Optional<Float> timeMonad = PingUtility.check(dnsServer);
                        if (!timeMonad.isPresent()) {
                            metrics.get("dns_latency").put(cqlIPRepr, 0.0);
                            continue;
                        }
                        metrics.get("dns_latency").put(cqlIPRepr, Double.valueOf(timeMonad.get()));
                    } catch (IOException | InterruptedException e) {
                        metrics.get("dns_latency").put(cqlIPRepr, 0.0);
                    }
                }
            });

            return this;
        }

        public Builder initLogCQL(String contactPoint, String namespace, String username, String password) {
            var cqlClusterBuilder = Cluster.builder().addContactPoint(contactPoint);
            if (username != "") {
                cqlClusterBuilder.withCredentials(username, password);
            }
            SysInfoGatherer.this.cqlCluster = cqlClusterBuilder.build();
            SysInfoGatherer.this.cqlSession = SysInfoGatherer.this.cqlCluster.connect();

            SysInfoGatherer.this.cqlSession.execute("CREATE KEYSPACE IF NOT EXISTS " + namespace
                    + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 2};");
            SysInfoGatherer.this.cqlSession.execute("USE " + namespace);

            return this;
        }

        public Builder initLogCQL(String contactPoint, String namespace) {
            return initLogCQL(contactPoint, namespace, "", "");
        }

        public Builder initLogCQL(String contactPoint) {
            return initLogCQL(contactPoint, "SysInfoDefault", "", "");
        }

        public SysInfoGatherer build() {
            return SysInfoGatherer.this;
        }
    }
}
