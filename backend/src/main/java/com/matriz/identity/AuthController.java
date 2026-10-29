package com.matriz.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.shared.ApiException;
import com.matriz.shared.ProblemCode;

/** Session lifecycle: CSRF token, sign-in, sign-out and current user ({@code /auth/*} in openapi.yaml). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final SignInService signInService;

	private final SignInRateLimiter rateLimiter;

	private final SecurityContextRepository securityContextRepository;

	private final CookieCsrfTokenRepository csrfTokenRepository;

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder
		.getContextHolderStrategy();

	AuthController(SignInService signInService, SignInRateLimiter rateLimiter,
			SecurityContextRepository securityContextRepository, CookieCsrfTokenRepository csrfTokenRepository) {
		this.signInService = signInService;
		this.rateLimiter = rateLimiter;
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	/** Issues the XSRF-TOKEN cookie (the token is loaded, and the cookie written, by the CSRF filter). */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/sign-in")
	CurrentUserResponse signIn(@Valid @RequestBody SignInRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		rateLimiter.acquire(request.getRemoteAddr());
		AuthenticatedUser user = signInService.signIn(body.email(), body.password());

		// Session fixation protection: always start a brand-new session (new id and creation time).
		HttpSession previous = request.getSession(false);
		if (previous != null) {
			previous.invalidate();
		}
		request.getSession(true);

		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities(user)));
		contextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		// Rotate the CSRF token at authentication.
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
		return CurrentUserResponse.of(user);
	}

	@PostMapping("/sign-out")
	ResponseEntity<Void> signOut(Authentication authentication, HttpServletRequest request,
			HttpServletResponse response) {
		SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
		logoutHandler.setSecurityContextRepository(securityContextRepository);
		logoutHandler.logout(request, response, authentication);
		csrfTokenRepository.saveToken(null, request, response);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
		return signInService.currentUser(principal.id()).map(CurrentUserResponse::of).orElseThrow(() -> {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			return new ApiException(ProblemCode.UNAUTHENTICATED);
		});
	}

	private static List<GrantedAuthority> authorities(AuthenticatedUser user) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
		if (user.admin()) {
			authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
		}
		return authorities;
	}

	record SignInRequest(
			@NotBlank(message = "Email is required.") @Email(message = "Email must be a valid email address.")
			@Size(max = 254, message = "Email must have at most 254 characters.") String email,
			@NotEmpty(message = "Password is required.")
			@Size(max = 128, message = "Password must have at most 128 characters.") String password) {

		@Override
		public String toString() {
			return "SignInRequest[email=" + email + ", password=******]";
		}

	}

	record CurrentUserResponse(UUID id, String name, String email, boolean isAdmin) {

		static CurrentUserResponse of(AuthenticatedUser user) {
			return new CurrentUserResponse(user.id(), user.name(), user.email(), user.admin());
		}

	}

}
