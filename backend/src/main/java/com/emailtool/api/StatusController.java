package com.emailtool.api;

import com.emailtool.api.dto.SystemStatusDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final SystemStatusService systemStatusService;

    public StatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    public SystemStatusDto status() {
        return systemStatusService.status();
    }
}
