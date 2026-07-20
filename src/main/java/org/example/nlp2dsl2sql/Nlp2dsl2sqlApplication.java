package org.example.nlp2dsl2sql;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("org.example.nlp2dsl2sql.mapper")
@SpringBootApplication
public class Nlp2dsl2sqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(Nlp2dsl2sqlApplication.class, args);
    }

}
