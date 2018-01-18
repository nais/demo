package nais.nav.no.demo;

import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import io.prometheus.client.spring.boot.EnableSpringBootMetricsCollector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@SpringBootApplication
@RestController
@EnablePrometheusEndpoint
@EnableSpringBootMetricsCollector
public class DemoApplication {

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


}
