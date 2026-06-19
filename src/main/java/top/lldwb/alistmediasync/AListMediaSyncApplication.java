package top.lldwb.alistmediasync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AListMediaSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(AListMediaSyncApplication.class, args);
    }

}
