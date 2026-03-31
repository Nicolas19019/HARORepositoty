package com.Aplication.HARO;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HaroApplication {

	public static void main(String[] args) {
		SpringApplication.run(HaroApplication.class, args);
	}

}
