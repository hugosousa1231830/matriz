package com.matriz;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build on forbidden dependencies between application modules (ADR-0001). */
class ModularityTests {

	@Test
	void verifiesModuleStructure() {
		ApplicationModules.of(MatrizApplication.class).verify();
	}

}
