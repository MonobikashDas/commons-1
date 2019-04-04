package io.mosip.registrationprocessor.externalStage;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.mosip.registrationprocessor.externalStage.stage.ExternalStage;

/**
 * External Stage application
 *
 */
public class ExternalStageApplication {

	/**
	 * main method to launch external stage application
	 * @param args
	 */
	public static void main(String[] args) {
		AnnotationConfigApplicationContext configApplicationContext = new AnnotationConfigApplicationContext();
		configApplicationContext.scan(
				  "io.mosip.registrationprocessor.externalStage.config",
				  "io.mosip.registration.processor.core.config",
				  "io.mosip.registration.processor.rest.client.config");
		configApplicationContext.refresh();
		ExternalStage externalStage = (ExternalStage) configApplicationContext.getBean(ExternalStage.class);
		externalStage.deployVerticle();
	}

}
