package com.tus.apigateway.config;

import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.context.slf4j.MDCScopeDecorator;
import brave.http.HttpTracing;
import brave.propagation.B3Propagation;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import brave.jakarta.servlet.TracingFilter;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

@Configuration
public class TracingConfig {

    @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}")
    private String zipkinEndpoint;

    @Value("${spring.application.name:api-gateway}")
    private String serviceName;

    @Bean
    public Tracing tracing() {
        var sender = URLConnectionSender.create(zipkinEndpoint);
        var zipkinSpanHandler = AsyncZipkinSpanHandler.create(sender);

        return Tracing.newBuilder()
                .localServiceName(serviceName)
                .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
                        .addScopeDecorator(MDCScopeDecorator.get())
                        .build())
                .propagationFactory(B3Propagation.FACTORY)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .addSpanHandler(zipkinSpanHandler)
                .build();
    }

    @Bean
    public HttpTracing httpTracing(Tracing tracing) {
        return HttpTracing.create(tracing);
    }

    @Bean
    public Filter tracingFilter(HttpTracing httpTracing) {
        return TracingFilter.create(httpTracing);
    }

    @Bean
    public ClientHttpRequestInterceptor tracingInterceptor(Tracing tracing) {
        return (request, body, execution) -> {
            var span = tracing.tracer().currentSpan();
            if (span != null) {
                var context = span.context();
                request.getHeaders().set("X-B3-TraceId", context.traceIdString());
                request.getHeaders().set("X-B3-SpanId", context.spanIdString());
                request.getHeaders().set("X-B3-Sampled", "1");
                if (context.parentIdString() != null) {
                    request.getHeaders().set("X-B3-ParentSpanId", context.parentIdString());
                }
            }
            return execution.execute(request, body);
        };
    }
}
