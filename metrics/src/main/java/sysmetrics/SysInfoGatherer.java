package sysmetrics;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private Map<String, Map<String, Double>> metrics = new HashMap<String, Map<String, Double>>();
    private List<UpdateCallable> updaters = new ArrayList<UpdateCallable>();

    private SysInfoGatherer() {
    }

    public Map<String, Map<String, Double>> updateMetrics() {
        for (UpdateCallable updater : updaters) {
            updater.update();
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
                    String interfaceName = networkInterface.getDisplayName();
                    networkInterface.updateAttributes();
                    metrics.get("network_usage").put(interfaceName + " recv",
                            Double.valueOf(networkInterface.getBytesRecv()));
                    metrics.get("network_usage").put(interfaceName + " sent",
                            Double.valueOf(networkInterface.getBytesSent()));
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
                    metrics.get("cpu_usage").put(Integer.toString(i + 1), recentUsage[i]);
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
                metrics.get("memory_usage").put("used/total", usedToTotal);
            });

            return this;
        }

        public Builder initDisk()
                throws IOException {
            SysInfoGatherer.this.disk = Files.getFileStore(Paths.get("./"));
            SysInfoGatherer.this.metrics.put("disk_usage", new HashMap<String, Double>());

            SysInfoGatherer.this.updaters.add(() -> {
                double usedToTotal;
                try {
                    usedToTotal = 1 - (disk.getUsableSpace() / (double) disk.getTotalSpace());
                } catch (IOException e) {
                    usedToTotal = 1.0;
                }
                metrics.get("disk_usage").put("used/total", usedToTotal);
            });

            return this;
        }

        public Builder initDNS(String... domains) {
            SysInfoGatherer.this.dnsServers = domains;
            SysInfoGatherer.this.metrics.put("dns_latency", new HashMap<String, Double>());

            SysInfoGatherer.this.updaters.add(() -> {
                for (String dnsServer : dnsServers) {
                    try {
                        Optional<Float> timeMonad = PingUtility.check(dnsServer);
                        if (!timeMonad.isPresent()) {
                            metrics.get("dns_latency").put(dnsServer, 0.0);
                            continue;
                        }
                        metrics.get("dns_latency").put(dnsServer, Double.valueOf(timeMonad.get()));
                    } catch (IOException | InterruptedException e) {
                        metrics.get("dns_latency").put(dnsServer, 0.0);
                    }
                }
            });

            return this;
        }

        public SysInfoGatherer build() {
            return SysInfoGatherer.this;
        }
    }
}
