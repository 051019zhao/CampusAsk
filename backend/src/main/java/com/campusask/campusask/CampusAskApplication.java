package com.campusask.campusask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusAskApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAskApplication.class, args);
    }

}
