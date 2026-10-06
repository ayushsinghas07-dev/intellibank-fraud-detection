package com.intellibank.filter;

import com.google.gson.Gson;
import com.intellibank.dto.ApiResponse;
import com.intellibank.exception.BusinessException;
import com.intellibank.util.JsonUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class GlobalExceptionHandlerFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandlerFilter.class);
    private static final Gson gson = JsonUtil.getGson();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (Throwable t) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            Throwable cause = t;
            if (t instanceof ServletException && t.getCause() != null) {
                cause = t.getCause();
            }

            if (cause instanceof BusinessException be) {
                logger.warn("Business exception caught: [{}] {}", be.getErrorCode(), be.getMessage());
                int status = mapErrorCodeToStatus(be.getErrorCode());
                sendErrorResponse(httpResponse, status, be.getErrorCode(), be.getMessage(), be.getFieldErrors());
            } else {
                logger.error("Unhandled internal server error caught", cause);
                sendErrorResponse(httpResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please try again later.", null);
            }
        }
    }

    private int mapErrorCodeToStatus(String code) {
        if (code == null) return 500;
        return switch (code) {
            case "UNAUTHORIZED", "EXPIRED_TOKEN" -> 401;
            case "FORBIDDEN" -> 403;
            case "NOT_FOUND", "CUSTOMER_NOT_FOUND", "ACCOUNT_NOT_FOUND", "TRANSACTION_NOT_FOUND", "ALERT_NOT_FOUND" -> 404;
            case "CONFLICT", "DUPLICATE_ENTRY" -> 409;
            case "INVALID_AMOUNT", "INVALID_INPUT", "INVALID_CREDENTIALS" -> 400;
            case "INSUFFICIENT_FUNDS", "DAILY_LIMIT_EXCEEDED", "ACCOUNT_FROZEN", "CUSTOMER_HAS_NON_ZERO_BALANCE", "CUSTOMER_HAS_PENDING_ALERTS" -> 422;
            default -> 400;
        };
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String code, String message, java.util.Map<String, String> fieldErrors) throws IOException {
        if (!response.isCommitted()) {
            response.setStatus(statusCode);
            response.setContentType("application/json;charset=UTF-8");
            ApiResponse<Void> apiResp = ApiResponse.error(code, message, fieldErrors);
            response.getWriter().write(gson.toJson(apiResp));
        }
    }
}
