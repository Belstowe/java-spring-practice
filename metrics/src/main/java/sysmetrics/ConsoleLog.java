package sysmetrics;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class ConsoleLog {
    public static void main(String[] args) {
        try {
            SysInfoGatherer sysInfo = SysInfoGatherer.newBuilder()
                    .initNetworkIFs()
                    .initCPU()
                    .initDisk()
                    .initMemory()
                    .initDNS("8.8.8.8", "1.1.1.1", "77.88.8.8")
                    .build();

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
            System.out.println("Возникла ошибка при инициализации метрик!");
            System.err.println(e.getMessage());
        }
    }
}