package com.intellibank.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.User;
import com.intellibank.service.AuthService;
import com.intellibank.util.JsonUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AuthServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final AuthService authService = new AuthService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        resp.setContentType("application/json;charset=UTF-8");

        if ("/login".equalsIgnoreCase(path) || path == null || "/".equals(path)) {
            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);
            String username = body.has("username") ? body.get("username").getAsString() : (body.has("email") ? body.get("email").getAsString() : "");
            String password = body.has("password") ? body.get("password").getAsString() : "";
            String ip = req.getRemoteAddr();

            String token = authService.login(username, password, ip);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("tokenType", "Bearer");

            resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
        } else if ("/logout".equalsIgnoreCase(path)) {
            resp.getWriter().write(gson.toJson(ApiResponse.success("Logged out successfully")));
        } else {
            resp.setStatus(404);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Auth endpoint not found")));
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        resp.setContentType("application/json;charset=UTF-8");

        if ("/me".equalsIgnoreCase(path)) {
            User user = (User) req.getAttribute("currentUser");
            if (user != null) {
                user.setPasswordHash(null); // Never expose password hash
                resp.getWriter().write(gson.toJson(ApiResponse.success(user)));
            } else {
                resp.setStatus(401);
                resp.getWriter().write(gson.toJson(ApiResponse.error("UNAUTHORIZED", "Not logged in")));
            }
        } else {
            resp.setStatus(404);
            resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Endpoint not found")));
        }
    }
}
