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
        // Hibernate builds entity proxies with Byte Buddy, which refuses to run
        // on JDK versions newer than it officially knows about (this machine
        // has JDK 27). The flag lets it run anyway; harmless on older JDKs.
        System.setProperty("net.bytebuddy.experimental", "true");
        SpringApplication.run(SpringserverApplication.class, args);
    }
}
