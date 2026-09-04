package tn.esprit.formation.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Carries the caller's token across the service boundary.
 *
 * Formation asks the USER service who is calling, and that question only has an answer if
 * the outgoing call presents the caller's own token. Feign builds a fresh request that
 * inherits nothing, so the header is copied from the inbound one here — once, rather than
 * in every client method.
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor authorizationRelayInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            // No inbound HTTP request to copy from — a scheduled job or a start-up call.
            if (attributes == null) {
                return;
            }
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        };
    }
}
