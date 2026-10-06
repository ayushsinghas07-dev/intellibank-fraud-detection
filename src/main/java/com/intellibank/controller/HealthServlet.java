package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.intellibank.config.DatabaseConfig;
import com.intellibank.dto.ApiResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HealthServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        boolean dbOk = DatabaseConfig.testConnection();

        Map<String, Object> data = new HashMap<>();
        data.put("status", dbOk ? "UP" : "DOWN");
        data.put("database", dbOk ? "CONNECTED" : "DISCONNECTED");
        data.put("timestamp", System.currentTimeMillis());

        if (dbOk) {
            resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
        } else {
            resp.setStatus(503);
            resp.getWriter().write(gson.toJson(ApiResponse.error("DATABASE_DOWN", "Database connectivity test failed", null)));
        }
    }
}
