package com.matriz.identity;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The signed-in user, taken from the server-side session. Other modules obtain
 * it with {@code @AuthenticationPrincipal AuthenticatedUser} and must use it as
 * the only source of the caller's identity (never ids sent by the client).
 *
 * @param id    user id
 * @param email sign-in email
 * @param name  display name
 * @param admin platform Administrator ({@code is_admin}): sees and manages every project
 */
public record AuthenticatedUser(UUID id, String email, String name, boolean admin)
		implements AuthenticatedPrincipal, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** The user id; also stored by Spring Session as the session's principal name. */
	@Override
	public String getName() {
		return id.toString();
	}

}
