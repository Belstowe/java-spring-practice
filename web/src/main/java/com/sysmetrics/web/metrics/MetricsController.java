package com.sysmetrics.web.metrics;

import java.time.LocalDateTime;

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
    public String metrics(
            @RequestParam(value = "from", required = true) @DateTimeFormat(pattern = "yyyyMMdd-HHmmss") LocalDateTime from,
            @RequestParam(value = "to", required = true) @DateTimeFormat(pattern = "yyyyMMdd-HHmmss") LocalDateTime to,
            Model model) {
        return "data";
    }

}
