package com.datastax.killrvideo.it;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
@EnableAutoConfiguration
@SpringBootApplication
public class KillrVideoITApplication  {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(KillrVideoITApplication.class);
        app.setWebEnvironment(false);
        app.setBannerMode(Banner.Mode.CONSOLE);
        app.run(args);
    }
}
