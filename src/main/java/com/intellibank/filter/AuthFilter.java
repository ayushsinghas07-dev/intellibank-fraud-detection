package com.intellibank.filter;

import com.google.gson.Gson;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.User;
import com.intellibank.security.JwtProvider;
import io.jsonwebtoken.Claims;
import com.intellibank.util.JsonUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class AuthFilter implements Filter {
    private static final Gson gson = JsonUtil.getGson();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Allow public endpoints & static assets
        if (isPublicEndpoint(path) || "OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Validate Authorization Header
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendJsonError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid Authorization header. Please log in.");
            return;
        }

        String token = authHeader.substring(7);
        Claims claims = JwtProvider.validateAndParseToken(token);
        if (claims == null) {
            sendJsonError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "EXPIRED_TOKEN", "Session expired or invalid token. Please log in again.");
            return;
        }

        User user = new User();
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number num) {
            user.setId(num.longValue());
        }
        user.setUsername(claims.getSubject());
        user.setEmail(claims.get("email", String.class));
        user.setRole(claims.get("role", String.class));
        user.setFullName(claims.get("fullName", String.class));

        httpRequest.setAttribute("currentUser", user);

        // Enforce Server-Side RBAC
        if (!isAuthorized(user.getRole(), path, httpRequest.getMethod())) {
            sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied: User role '" + user.getRole() + "' does not have permission to execute this operation.");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/login") ||
               path.startsWith("/api/health") ||
               !path.startsWith("/api/");
    }

    private boolean isAuthorized(String role, String path, String method) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return true; // Admin has full access to everything
        }

        if ("FRAUD_ANALYST".equalsIgnoreCase(role)) {
            // Analyst cannot manage users or edit rule configurations
            if (path.startsWith("/api/users")) return false;
            if (path.startsWith("/api/fraud/rules") && "PUT".equalsIgnoreCase(method)) return false;
            return true;
        }

        if ("VIEWER".equalsIgnoreCase(role)) {
            // Viewer is strictly read-only (GET requests only, excluding sensitive write APIs)
            if (!"GET".equalsIgnoreCase(method)) {
                // Allow notification mark as read for convenience or restrict? Restrict write money/alert ops.
                if (path.startsWith("/api/notifications/") && "POST".equalsIgnoreCase(method)) return true;
                return false;
            }
            if (path.startsWith("/api/users")) return false;
            return true;
        }

        return false;
    }

    private void sendJsonError(HttpServletResponse response, int statusCode, String errorCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> apiResp = ApiResponse.error(errorCode, message);
        response.getWriter().write(gson.toJson(apiResp));
    }
}
