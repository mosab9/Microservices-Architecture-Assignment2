package com.tus.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableScheduling
public class SwaggerConfig {

    private final DiscoveryClient discoveryClient;
    private final SwaggerUiConfigProperties swaggerUiConfigProperties;

    // Services that expose OpenAPI docs
    private static final Set<String> SWAGGER_SERVICES = Set.of("order-service", "inventory-service");

    public SwaggerConfig(DiscoveryClient discoveryClient, SwaggerUiConfigProperties swaggerUiConfigProperties) {
        this.discoveryClient = discoveryClient;
        this.swaggerUiConfigProperties = swaggerUiConfigProperties;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Online Food Ordering System - API Gateway")
                        .description("API Gateway with JWT Authentication. Use /auth/login to get a token.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com")));
    }

    @Scheduled(fixedDelay = 30000) // Refresh every 30 seconds
    public void refreshSwaggerUrls() {
        Set<SwaggerUrl> urls = new HashSet<>();

        // Always add the gateway's own API docs
        SwaggerUrl gatewayUrl = new SwaggerUrl();
        gatewayUrl.setName("API Gateway (Auth)");
        gatewayUrl.setUrl("/v3/api-docs");
        urls.add(gatewayUrl);

        // Dynamically add services from Eureka
        List<String> services = discoveryClient.getServices();

        for (String serviceId : services) {
            if (SWAGGER_SERVICES.contains(serviceId.toLowerCase())) {
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
                if (!instances.isEmpty()) {
                    ServiceInstance instance = instances.get(0);
                    String url = instance.getUri().toString() + "/v3/api-docs";

                    SwaggerUrl swaggerUrl = new SwaggerUrl();
                    swaggerUrl.setName(formatServiceName(serviceId));
                    swaggerUrl.setUrl(url);
                    urls.add(swaggerUrl);
                }
            }
        }

        swaggerUiConfigProperties.setUrls(urls);
    }

    private String formatServiceName(String serviceId) {
        // Convert "order-service" to "Order Service"
        String[] parts = serviceId.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return sb.toString();
    }
}
