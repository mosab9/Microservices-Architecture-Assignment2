package com.tus.apigateway.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Enumeration;
import java.util.Set;

@RestController
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    // Headers that should not be forwarded in error responses
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "transfer-encoding", "content-length", "connection",
            "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "upgrade"
    );

    private final RestTemplate restTemplate;

    // Using Eureka service names instead of hardcoded URLs
    private static final String ORDER_SERVICE_URL = "http://order-service";
    private static final String INVENTORY_SERVICE_URL = "http://inventory-service";
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    private static final String GATEWAY_SECRET_VALUE = "my-gateway-secret-key-2026";

    public ProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RequestMapping(value = "/api/orders/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    @CircuitBreaker(name = "orderService", fallbackMethod = "serviceFallback")
    @Retry(name = "orderService")
    public ResponseEntity<String> proxyOrders(HttpServletRequest request, @RequestBody(required = false) String body) {
        return forwardRequest(request, body, ORDER_SERVICE_URL);
    }

    @RequestMapping(value = "/api/customers/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    @CircuitBreaker(name = "orderService", fallbackMethod = "serviceFallback")
    @Retry(name = "orderService")
    public ResponseEntity<String> proxyCustomers(HttpServletRequest request, @RequestBody(required = false) String body) {
        return forwardRequest(request, body, ORDER_SERVICE_URL);
    }

    @RequestMapping(value = "/api/inventory/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "serviceFallback")
    @Retry(name = "inventoryService")
    public ResponseEntity<String> proxyInventory(HttpServletRequest request, @RequestBody(required = false) String body) {
        return forwardRequest(request, body, INVENTORY_SERVICE_URL);
    }

    private ResponseEntity<String> serviceFallback(HttpServletRequest request, String body, Throwable t) {
        log.error("Circuit breaker fallback triggered for {} - Exception type: {}, Message: {}",
                request.getRequestURI(), t.getClass().getName(), t.getMessage());
        if (t.getCause() != null) {
            log.error("Root cause: {} - {}", t.getCause().getClass().getName(), t.getCause().getMessage());
        }
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\": \"Service is currently unavailable. Please try again later.\", \"details\": \"" + t.getMessage() + "\"}");
    }

    private ResponseEntity<String> forwardRequest(HttpServletRequest request, String body, String targetUrl) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String url = targetUrl + path + (query != null ? "?" + query : "");

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!headerName.equalsIgnoreCase("host") && !headerName.equalsIgnoreCase("content-length")) {
                headers.set(headerName, request.getHeader(headerName));
            }
        }

        // Add gateway secret header to identify requests from the gateway
        headers.set(GATEWAY_SECRET_HEADER, GATEWAY_SECRET_VALUE);

        log.info("Forwarding request to {}", url);

        // Add authenticated user info to headers for downstream services
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            headers.set("X-User-Name", userDetails.getUsername());
            String role = userDetails.getAuthorities().stream()
                    .findFirst()
                    .map(auth -> auth.getAuthority())
                    .orElse("ROLE_USER");
            headers.set("X-User-Role", role);
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            log.debug("Request to {} completed successfully with status {}", url, response.getStatusCode());
            return response;
        } catch (HttpClientErrorException e) {
            // Forward 4xx errors (Bad Request, Not Found, etc.) with original response body
            log.debug("Received 4xx response from {}: {}", url, e.getStatusCode());
            return ResponseEntity
                    .status(e.getStatusCode())
                    .headers(filterHeaders(e.getResponseHeaders()))
                    .body(e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            // Forward 5xx errors with original response body
            log.warn("Received 5xx response from {}: {}", url, e.getStatusCode());
            return ResponseEntity
                    .status(e.getStatusCode())
                    .headers(filterHeaders(e.getResponseHeaders()))
                    .body(e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            // Connection error - let the circuit breaker handle this
            log.error("Connection error to {}: {}", url, e.getMessage());
            throw e;
        }
    }

    private HttpHeaders filterHeaders(HttpHeaders originalHeaders) {
        if (originalHeaders == null) {
            return new HttpHeaders();
        }
        HttpHeaders filteredHeaders = new HttpHeaders();
        originalHeaders.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                filteredHeaders.addAll(name, values);
            }
        });
        return filteredHeaders;
    }
}
