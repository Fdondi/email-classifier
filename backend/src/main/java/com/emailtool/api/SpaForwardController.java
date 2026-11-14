package com.emailtool.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Lets a browser refresh or bookmark an Angular route when the UI is served by this backend. */
@Controller
public class SpaForwardController {

    @GetMapping({"/runs", "/spam", "/hidden", "/email/{id}"})
    public String forward() {
        return "forward:/index.html";
    }
}
