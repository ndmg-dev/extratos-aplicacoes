package com.mendoncagalvao.extratos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Aplicação de extração de extratos de aplicações financeiras.
 * Reimplementa, em web, o pipeline n8n original (ver {@code SPECS-extratos-aplicacoes.md}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ExtratosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExtratosApplication.class, args);
    }
}
