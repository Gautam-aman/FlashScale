package com.example.FlashScale;

import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableScheduling
@SpringBootApplication
public class FlashScaleApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlashScaleApplication.class, args);
	}

}
