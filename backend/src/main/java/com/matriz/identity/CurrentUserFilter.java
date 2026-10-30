package com.matriz.identity;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-reads the session's user on every request, so the session never outlives
 * the account state: a deactivated or deleted user loses the session and the
 * request continues unauthenticated (protected endpoints answer {@code 401});
 * a changed name, email or Administrator flag replaces the session's
 * principal at once.
 */
class CurrentUserFilter extends OncePerRequestFilter {

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder
		.getContextHolderStrategy();

	private final SignInService signInService;

	private final SecurityContextRepository securityContextRepository;

	CurrentUserFilter(SignInService signInService, SecurityContextRepository securityContextRepository) {
		this.signInService = signInService;
		this.securityContextRepository = securityContextRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = contextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
			Optional<AuthenticatedUser> current = signInService.currentUser(principal.id());
			if (current.isEmpty()) {
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.invalidate();
				}
				contextHolder.clearContext();
			}
			else if (!current.get().equals(principal)) {
				SecurityContext context = contextHolder.createEmptyContext();
				context.setAuthentication(AuthController.authentication(current.get()));
				contextHolder.setContext(context);
				securityContextRepository.saveContext(context, request, response);
			}
		}
		chain.doFilter(request, response);
	}

}
