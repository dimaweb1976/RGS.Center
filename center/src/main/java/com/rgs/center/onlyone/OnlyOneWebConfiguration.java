package com.rgs.center.onlyone;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers backend enforcement for resources controlled by the exclusive lease.
 *
 * <p>The interceptor itself permits read-only methods and validates the lease
 * for state-changing methods. The lease-management API is excluded to avoid a
 * circular requirement: clients must be able to acquire, inspect, extend, and
 * release the lease without already passing the protected-resource check.</p>
 *
 * <p>Enforcement is enabled by default. Setting
 * {@code rgs.only-one.enforce=false} is intended only for explicitly configured
 * test or maintenance scenarios.</p>
 */
@Configuration
@ConditionalOnProperty(
        name = "rgs.only-one.enforce",
        havingValue = "true",
        matchIfMissing = true
)
class OnlyOneWebConfiguration implements WebMvcConfigurer {

    private final OnlyOneLockInterceptor lockInterceptor;

    OnlyOneWebConfiguration(OnlyOneLockInterceptor lockInterceptor) {
        this.lockInterceptor = lockInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Keep this list aligned with every controller capable of changing
        // device, measurement, or process state.
        registry.addInterceptor(lockInterceptor)
                .addPathPatterns(
                        "/measurement-emulator/**",
                        "/gammapro-boson/**",
                        "/barcode-scanner/**",
                        "/manipulator/**",
                        "/api/**"
                )
                .excludePathPatterns("/api/onlyOne/**");
    }
}
