package br.com.unisinos.despensa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DespensaWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(DespensaWebApplication.class, args);
    }
}
