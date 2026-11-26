package br.com.mentoria.teacher;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class BffWebConfiguration implements WebMvcConfigurer {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ID_ATTRIBUTE = "userId";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String header = request.getHeader(USER_ID_HEADER);
                if (header != null && header.length() == 36) {
                    try {
                        UUID id = UUID.fromString(header);
                        if (id.toString().equalsIgnoreCase(header)) {
                            request.setAttribute(USER_ID_ATTRIBUTE, id);
                            return true;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // The same 401 applies to each invalid identity.
                    }
                }
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id ausente ou inválido.");
            }
        }).addPathPatterns(TeacherController.PREFIX + "/**");
    }
}
