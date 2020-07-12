package com.swadessoaps.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swadessoaps.dto.AuthenticationResponse;
import com.swadessoaps.dto.LoginRequest;
import com.swadessoaps.dto.RegisterRequest;
import com.swadessoaps.exceptions.SwadesSoapsException;
import com.swadessoaps.model.NotificationEmail;
import com.swadessoaps.model.User;
import com.swadessoaps.model.VerificationToken;
import com.swadessoaps.repository.UserRepository;
import com.swadessoaps.repository.VerificationTokenRepository;
import com.swadessoaps.security.JwtProvider;
import com.swadessoaps.util.Constants;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AuthService {

	private final PasswordEncoder passwordEncoder;
	private final UserRepository userRepository;
	private final VerificationTokenRepository verificationTokenRepository;
	private final MailContentBuilder mailContentBuilder;
	private final MailService mailService;
	private final AuthenticationManager authenticationManager;
	private final JwtProvider jwtProvider;

	@Transactional
	public void signup(RegisterRequest registerRequest) {
		User user = new User();
		user.setEmail(registerRequest.getEmail());
		user.setUsername(registerRequest.getUsername());
		user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
		user.setCreated(Instant.now());
		user.setEnabled(false);

		userRepository.save(user);

		String token = generateVarificationToken(user);
		String message = mailContentBuilder.build(
				"Thank you for signing up to Spring Reddit, please click on the below url to activate your account : "
						+ Constants.ACTIVATION_EMAIL + "/" + token);
		mailService.sendMail(new NotificationEmail("Please Activate your account", user.getEmail(), message));
	}

	private String generateVarificationToken(User user) {
		String token = UUID.randomUUID().toString();
		VerificationToken verifivationToken = new VerificationToken();
		verifivationToken.setToken(token);
		verifivationToken.setUser(user);

		verificationTokenRepository.save(verifivationToken);
		return token;
	}

	public void verifyAccount(String token) {
		Optional<VerificationToken> verificationToken = verificationTokenRepository.findByToken(token);
		verificationToken.orElseThrow(() -> new SwadesSoapsException("Invalid Token"));
		fetchUserAndEnable(verificationToken.get());
	}

	@Transactional
	private void fetchUserAndEnable(VerificationToken verificationToken) {
		String username = verificationToken.getUser().getUsername();
		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new SwadesSoapsException("User Not Found!"));
		user.setEnabled(true);
		userRepository.save(user);
	}

	public AuthenticationResponse login(LoginRequest loginRequest) {
		Authentication authenticate = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
		SecurityContextHolder.getContext().setAuthentication(authenticate);
		String token = jwtProvider.generateToken(authenticate);
		return new AuthenticationResponse(token, loginRequest.getUsername());
	}
}
