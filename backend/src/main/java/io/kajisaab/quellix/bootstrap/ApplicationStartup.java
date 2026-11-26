package io.kajisaab.quellix.bootstrap;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartup implements ApplicationRunner {

    private final Environment environment;
    private final ConnectionFactory connectionFactory;
    private final ConfigurableApplicationContext applicationContext;

    @PostConstruct
    public void initialize() {
        log.info("PostConstruct called");
    }

    @Override
    public void run(ApplicationArguments args) {

        String applicationName = environment.getProperty(
                "spring.application.name", "Unknown");

        String[] activeProfiles = environment.getActiveProfiles();
        String serverPort = environment.getProperty("server.port");

        ConnectionFactoryMetadata metadata = connectionFactory.getMetadata();

        log.info("");
        log.info("==========================================================");
        log.info("                  APPLICATION STARTUP                     ");
        log.info("==========================================================");
        log.info("  Application Name    : {}", applicationName);
        log.info("  Active Profiles     : {}",
                String.join(", ", activeProfiles));
        log.info("  Total Spring Beans  : {}",
                applicationContext.getBeanDefinitionCount());
        log.info("  Server endpoint     : {}", link("http://localhost:" + serverPort + "/api"));
        log.info("  Swagger endpoint    : {}", link("http://localhost:" + serverPort + "/swagger-ui.html"));

        log.info("----------------------------------------------------------");
        log.info("                  DATABASE CONFIGURATION                  ");
        log.info("----------------------------------------------------------");

        log.info("  Database            : {}", metadata.getName());
        log.info("  Connection Factory  : {}", connectionFactory.getClass().getSimpleName());

        if (connectionFactory instanceof ConnectionPool pool) {

            log.info("  Connection Pool     : Enabled");

            pool.getMetrics().ifPresentOrElse(metrics -> {
                log.info("  Maximum Pool Size   : {}", metrics.getMaxAllocatedSize());
                log.info("  Active Connections : {}", metrics.acquiredSize());
                log.info("  Idle Connections    : {}", metrics.idleSize());
                log.info("  Total Connections  : {}", metrics.allocatedSize());
                log.info("  Pending Acquisitions: {}", metrics.pendingAcquireSize());
            }, () -> log.info("  Pool Metrics        : Not available"));

        } else {
            log.info("  Connection Pool     : Not detected");
        }

        log.info("==========================================================");
        log.info("");
    }

    @PreDestroy
    public void destroy() {
        log.info("Good Bye !!!");
    }

    private static String link(String url) {
        return link(url, url);
    }

    private static String link(String url, String label) {
        return "\u001B]8;;" + url + "\u001B\\" + label + "\u001B]8;;\u001B\\";
    }
}
