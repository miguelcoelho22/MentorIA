package br.com.mentoria.student;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class DownstreamHttp {

    private static final String CORRELATION_ID = "X-Correlation-Id";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final JdkClientHttpRequestFactory requestFactory;

    DownstreamHttp() {
        requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requestFactory.setReadTimeout(TIMEOUT);
    }

    RestClient.Builder builder(String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                        String correlationId = attributes.getRequest().getHeader(CORRELATION_ID);
                        if (correlationId != null) {
                            request.getHeaders().set(CORRELATION_ID, correlationId);
                        }
                    }
                    return execution.execute(request, body);
                });
    }
}
