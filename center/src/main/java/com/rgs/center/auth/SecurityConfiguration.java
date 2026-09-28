package com.rgs.center.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.RequestMatcherDelegatingAccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

/**
 * Configures stateless browser-token authentication for RGSCenter.
 *
 * <p>Spring HTTP sessions, the standard form-login filter, HTTP Basic and the
 * standard logout handler are disabled. {@link AuthTokenFilter} restores the
 * authenticated user independently for every request from the token cookie.</p>
 */
@Configuration
class SecurityConfiguration {

    /** BCrypt encoder used both when checking and when creating password hashes. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Defines public endpoints, CSRF handling and the position of the custom
     * token filter in the Spring Security filter chain.
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthTokenFilter authTokenFilter,
            ApiSecurityErrorHandler apiSecurityErrorHandler,
            @Value("${rgs.auth.cookie-secure:false}") boolean secureCookies
    ) throws Exception {
        CookieCsrfTokenRepository csrfRepository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(builder -> builder
                .sameSite("Strict")
                .secure(secureCookies)
                .path("/"));

        var apiMatcher = PathPatternRequestMatcher.pathPattern("/api/**");
        var delegatedAccessDeniedHandlers = new LinkedHashMap<
                RequestMatcher,
                AccessDeniedHandler
                >();
        delegatedAccessDeniedHandlers.put(apiMatcher, apiSecurityErrorHandler);
        var accessDeniedHandler = new RequestMatcherDelegatingAccessDeniedHandler(
                delegatedAccessDeniedHandlers,
                new AccessDeniedHandlerImpl()
        );
        var htmlEntryPoint = new LoginUrlAuthenticationEntryPoint("/auth/index.html");
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, exception) -> {
                    if (apiMatcher.matches(request)) {
                        apiSecurityErrorHandler.commence(
                                request,
                                response,
                                exception
                        );
                    } else {
                        htmlEntryPoint.commence(request, response, exception);
                    }
                };

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Logout only revokes the current SameSite=Strict cookie token.
                        // Ignoring CSRF here also makes logout reliable from an older tab.
                        .ignoringRequestMatchers(
                                "/auth/logout",
                                "/api/**", //onlyOne/**",
                                "/gammapro-boson/**"
                                //"/api/barcode/**",
                                //"/api/scales/**",
                                //"/api/dobot/**"
                        ))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(authorize -> authorize
                        
                        /*
                         * In the future. If we'll need to raise security - we'll split:
                         * all pages - permitAll, all APIs - only 4 authenticated (but all calls for API in front should be done with 'AUTH' line in header)
                         * 
                         */
                        /*
                        .requestMatchers(
                                //BY
                                "/auth/**",                                
                                "/common/**",
                                "/process/**",
                                
                                "/login",
                                "/auth/login",
                                "/api/onlyOne/**",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico",
                                "/error"
                        ).permitAll()*/
                        
                        // Role authorities are attached by AuthTokenFilter. At present all                        
                        // authenticated roles have access to the same protected endpoints.
                        //.anyRequest().authenticated())
                        
                        //BUT HERE WE'LL ALLOW ALL FOR ALL!
                        .anyRequest().permitAll())
                
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
