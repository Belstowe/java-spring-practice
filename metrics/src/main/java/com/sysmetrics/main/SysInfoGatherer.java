package com.sysmetrics.main;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sysmetrics.dns.PingUtility;
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
    private SysInfoDB db;

    private Map<String, Map<String, Double>> metrics = new HashMap<String, Map<String, Double>>();
    private List<UpdateCallable> updaters = new ArrayList<UpdateCallable>();

    private SysInfoGatherer() {
    }

    public Map<String, Map<String, Double>> updateMetrics() {
        for (UpdateCallable updater : updaters) {
            updater.update();
        }
        if (db != null) {
            for (var metric_group : metrics.entrySet()) {
                db.insert(metric_group.getKey(), metric_group.getValue());
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
        private Map<String, List<String>> dbColumns = new HashMap<String, List<String>>();

        private Builder() {
        }

        public Builder initNetworkIFs() {
            SysInfoGatherer.this.networkInterfaces = sysHAL.getNetworkIFs();
            SysInfoGatherer.this.metrics.put("network_usage", new HashMap<String, Double>());
            dbColumns.put("network_usage", new ArrayList<String>());
            for (NetworkIF networkInterface : SysInfoGatherer.this.networkInterfaces) {
                String interfaceName = networkInterface.getName();
                dbColumns.get("network_usage").add(interfaceName + "_recv");
                dbColumns.get("network_usage").add(interfaceName + "_sent");
            }

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
            dbColumns.put("cpu_usage", new ArrayList<String>());
            for (int i = 0; i < SysInfoGatherer.this.cpuLoadTicks.length; i++) {
                dbColumns.get("cpu_usage").add("cpu" + Integer.toString(i + 1));
            }

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
            dbColumns.put("memory_usage", new ArrayList<String>());
            dbColumns.get("memory_usage").add("used_to_total");

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
            dbColumns.put("disk_usage", new ArrayList<String>());
            dbColumns.get("disk_usage").add("used_to_total");

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
            dbColumns.put("dns_latency", new ArrayList<String>());
            for (String domain : domains) {
                String cqlDomainRepr = "ip_" + domain.replace('.', '_');
                dbColumns.get("dns_latency").add(cqlDomainRepr);
            }

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
            SysInfoGatherer.this.db = new SysInfoCQL(contactPoint, namespace, username, password);
            return this;
        }

        public Builder initLogCQL(String contactPoint, String namespace) {
            return initLogCQL(contactPoint, namespace, "", "");
        }

        public Builder initLogCQL(String contactPoint) {
            return initLogCQL(contactPoint, "SysInfoDefault", "", "");
        }

        public SysInfoGatherer build() {
            if (SysInfoGatherer.this.db != null) {
                for (var tableColumns : dbColumns.entrySet()) {
                    SysInfoGatherer.this.db.initTable(tableColumns.getKey(), tableColumns.getValue());
                }
            }
            return SysInfoGatherer.this;
        }
    }
}
