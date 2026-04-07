package com.medicaps.icms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IcmsApplication {
    public static void main(String[] args) {
        // Prefer IPv4 for outbound API calls. This helps on networks where Java's IPv6 path
        // can connect unreliably even though the host itself is reachable.
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        SpringApplication.run(IcmsApplication.class, args);
    }
}
