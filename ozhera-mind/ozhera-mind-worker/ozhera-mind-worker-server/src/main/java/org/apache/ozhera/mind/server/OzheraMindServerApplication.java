package org.apache.ozhera.mind.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.apache.ozhera.mind"})
@MapperScan("org.apache.ozhera.mind.service.dao.mapper")
public class OzheraMindServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OzheraMindServerApplication.class, args);
    }

}
