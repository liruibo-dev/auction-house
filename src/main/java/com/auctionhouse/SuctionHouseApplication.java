package com.auctionhouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SuctionHouseApplication {

	public static void main(String[] args) {

		SpringApplication.run(SuctionHouseApplication.class, args);
	}

}
