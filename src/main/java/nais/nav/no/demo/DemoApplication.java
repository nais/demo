package nais.nav.no.demo;

import ch.qos.logback.classic.LoggerContext;
import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import io.prometheus.client.spring.boot.EnableSpringBootMetricsCollector;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Optional;


@SpringBootApplication
@RestController
@EnablePrometheusEndpoint
@EnableSpringBootMetricsCollector
@EnableScheduling
public class DemoApplication {
    private static final Logger log = LoggerFactory.getLogger("auditLogger");


    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @RequestMapping(value="/hello",method = RequestMethod.GET)
    public String hello(@RequestParam(value="name", defaultValue = "World") String name) {
        return String.format("Hello, %s!", name);
    }

    @RequestMapping(value="/isAlive",method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public String isAlive() {
        return "Alive";
    }

    @RequestMapping(value="/isReady",method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public String isReady() {
        return "Ready";
    }


    @Scheduled(fixedRate = 5000)
    public void log() {
        log.info(" BrukerId=\"Z990691\" ber om tilgang til action=\"create\" for fnr=\"111111111\" i domene=\"sak\". Tilgang gis med decision=\"Permit\"");
    }


}
