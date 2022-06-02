package com.sysmetrics.web.metrics;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;

import com.sysmetrics.main.SysInfoCQL;
import com.sysmetrics.main.SysInfoDB;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MetricsController {

    private SysInfoDB db;

    @Value("${db.contact:127.0.0.1}")
    private String dbContact;
    @Value("${db.namespace:SysData}")
    private String dbNamespace;
    @Value("${db.username:}")
    private String dbUsername;
    @Value("${db.password:}")
    private String dbPassword;

    @PostConstruct
    public void init() {
        db = new SysInfoCQL(dbContact, dbNamespace, dbUsername, dbPassword);
    }

    @GetMapping("/data")
    public String metricsCharts(
            @RequestParam(value = "from", required = true) @DateTimeFormat(pattern = "yyyyMMdd-HHmmss") LocalDateTime from,
            @RequestParam(value = "to", required = true) @DateTimeFormat(pattern = "yyyyMMdd-HHmmss") LocalDateTime to,
            @RequestParam(value = "groups", required = false) List<String> groups,
            Model model) {
        var existingGroups = Arrays.asList(db.getGroups());
        if ((groups == null) || groups.isEmpty()) {
            groups = existingGroups;
        } else {
            if (groups.stream().anyMatch((group) -> {
                return !existingGroups.contains(group);
            })) {
                return "index";
            }
        }

        var metrics = db.selectTimeRange(from, to, groups);
        var metricLabels = new HashMap<String, Collection<String>>();
        for (var groupEntry : metrics.entrySet()) {
            var group = groupEntry.getKey();
            for (var timestampEntry : groupEntry.getValue().values()) {
                metricLabels.put(group, timestampEntry.keySet());
            }
        }
        model.addAttribute("metrics", metrics);
        model.addAttribute("metricLabels", metricLabels);
        return "show-metrics";
    }

}
