package com.challenge;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Resolves "Authorization: Bearer <token>" to a user for every /api call except /api/auth/**. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final UserRepo users;
  @Value("${app.cors-origin}") private String corsOrigin;

  WebConfig(UserRepo users) { this.users = users; }

  @Override public void addInterceptors(InterceptorRegistry reg) {
    reg.addInterceptor(new HandlerInterceptor() {
      @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) throws Exception {
        if (req.getMethod().equals("OPTIONS")) return true;
        String a = req.getHeader("Authorization");
        var u = (a != null && a.startsWith("Bearer ")) ? users.findByToken(a.substring(7)) : java.util.Optional.<AppUser>empty();
        if (u.isEmpty()) { res.sendError(401); return false; }
        req.setAttribute("user", u.get());
        return true;
      }
    }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**");
  }

  @Override public void addCorsMappings(CorsRegistry r) {
    r.addMapping("/api/**").allowedOrigins(corsOrigin).allowedMethods("*").allowedHeaders("*");
  }
}