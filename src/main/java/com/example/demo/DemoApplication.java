package com.example.demo;


import com.example.demo.server.NettyServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationContext;



@SpringBootApplication
@Slf4j
public class DemoApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(DemoApplication.class);
    }

    public static void main(String[] args) throws Exception {
        try {


            ApplicationContext context = SpringApplication.run(DemoApplication.class, args);
            NettyServer appServer = context.getBean(NettyServer.class);
            if (appServer != null) {
                appServer.run();

            } else {
                log.info("Server is not found!");
            }
    } catch (Exception e) {
        log.error("Error on ServiceMain", e);
    }

    }



}
