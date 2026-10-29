package com.matriz.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo data settings ({@code MATRIZ_SEED_DEMO}, {@code MATRIZ_DEMO_PASSWORD}).
 *
 * @param seed     whether demo data may be inserted (only into an empty database)
 * @param password shared, fictitious password of every demo account
 */
@ConfigurationProperties("matriz.demo")
record DemoProperties(boolean seed, String password) {

	@Override
	public String toString() {
		return "DemoProperties[seed=" + seed + ", password=****]";
	}

}
