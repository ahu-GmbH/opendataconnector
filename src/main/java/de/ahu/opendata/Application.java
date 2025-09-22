package de.ahu.opendata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;

/**
 * The entry point of the Spring Boot application.
 *
 * Use the @PWA annotation make the application installable on phones, tablets
 * and some desktop browsers.
 *
 */
@SuppressWarnings("serial")
@SpringBootApplication
@EnableScheduling
@EnableAsync
@Push
@Theme(value = "opendata-konnektor")
@EnableCaching
public class Application implements AppShellConfigurator {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
