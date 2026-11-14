package com.scout.springserver;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringserverApplication {

    public static void main(String[] args) {
        // Drizzle treats every `timestamp` (without time zone) column as UTC.
        // Pinning the JVM to UTC makes the Postgres JDBC driver open sessions
        // in UTC too, so DEFAULT now() values and the ones written here match
        // what the Express server has been writing.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SpringserverApplication.class, args);
    }
}
