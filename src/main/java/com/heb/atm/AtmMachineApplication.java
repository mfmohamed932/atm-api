package com.heb.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class AtmMachineApplication {

	public static void main(String[] args) {
		SpringApplication.run(AtmMachineApplication.class, args);
	}

}
