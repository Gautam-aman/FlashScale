package com.example.FlashScale.controller.testapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {

    @GetMapping("/ping")
    public PingResponse ping(){
        return new PingResponse("FLASHSCALE" , "UP");
    }

    public record PingResponse(String name , String status){}

}
