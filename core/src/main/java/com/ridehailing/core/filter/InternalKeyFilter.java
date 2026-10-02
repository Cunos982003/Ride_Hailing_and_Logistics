package com.ridehailing.core.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalKeyFilter extends OncePerRequestFilter {
  private final String internalKey;

  public InternalKeyFilter(@Value("${internal.key}") String internalKey) {
    this.internalKey = internalKey;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    if (path.startsWith("/internal/")) {
      String providedKey = request.getHeader("X-Internal-Key");

      if (providedKey == null || !providedKey.equals(internalKey)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response
            .getWriter()
            .write("{\"error\":\"FORBIDDEN\",\"message\":\"Invalid internal key\"}");
        return;
      }

      // Set authentication for internal requests
      var authorities = List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"));
      var authentication = new UsernamePasswordAuthenticationToken("internal", null, authorities);
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
  }
}
