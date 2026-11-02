package com.routemate;

import org.springframework.boot.SpringApplication;

public class TestRoutemateBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(RoutemateBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
