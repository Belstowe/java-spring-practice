package sysmetrics;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

public class SysInfoGatherer {
    private List<NetworkIF> networkInterfaces;
    private CentralProcessor cpu;
    private long[][] cpuLoadTicks;
    private GlobalMemory ram;
    private FileStore disk;
    private String[] dnsServers;

    private Map<String, Map<String, Double>> metrics = new HashMap<String, Map<String, Double>>();

    private SysInfoGatherer() {
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
            return this;
        }

        public Builder initCPU() {
            SysInfoGatherer.this.cpu = sysHAL.getProcessor();
            SysInfoGatherer.this.cpuLoadTicks = cpu.getProcessorCpuLoadTicks();
            SysInfoGatherer.this.metrics.put("cpu_usage", new HashMap<String, Double>());
            return this;
        }

        public Builder initMemory() {
            SysInfoGatherer.this.ram = sysHAL.getMemory();
            SysInfoGatherer.this.metrics.put("memory_usage", new HashMap<String, Double>());
            return this;
        }

        public Builder initDisk()
                throws IOException {
            SysInfoGatherer.this.disk = Files.getFileStore(Paths.get("./"));
            SysInfoGatherer.this.metrics.put("disk_usage", new HashMap<String, Double>());
            return this;
        }

        public Builder initDNS(String... domains) {
            SysInfoGatherer.this.dnsServers = domains;
            SysInfoGatherer.this.metrics.put("dns_latency", new HashMap<String, Double>());
            return this;
        }

        public SysInfoGatherer build() {
            return SysInfoGatherer.this;
        }
    }
}
