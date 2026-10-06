package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellibank.dao.UserDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.exception.BusinessException;
import com.intellibank.model.User;
import com.intellibank.util.PasswordUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class UserServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        if (path == null || "/".equals(path)) {
            List<User> users = userDao.findAll();
            for (User u : users) u.setPasswordHash(null); // Clear hashes
            resp.getWriter().write(gson.toJson(ApiResponse.success(users)));
        } else {
            try {
                Long id = Long.parseLong(path.substring(1));
                Optional<User> uOpt = userDao.findById(id);
                if (uOpt.isPresent()) {
                    User u = uOpt.get();
                    u.setPasswordHash(null);
                    resp.getWriter().write(gson.toJson(ApiResponse.success(u)));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write(gson.toJson(ApiResponse.error("USER_NOT_FOUND", "User not found")));
                }
            } catch (NumberFormatException e) {
                resp.setStatus(400);
                resp.getWriter().write(gson.toJson(ApiResponse.error("INVALID_ID", "Invalid user ID")));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        User currentUser = (User) req.getAttribute("currentUser");

        if (path != null && path.endsWith("/reset-password")) {
            Long id = Long.parseLong(path.split("/")[1]);
            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);
            String newPassword = body.get("newPassword").getAsString();

            String hash = PasswordUtil.hashPassword(newPassword);
            boolean updated = userDao.updatePassword(id, hash);
            resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
        } else if (path != null && path.endsWith("/status")) {
            Long id = Long.parseLong(path.split("/")[1]);
            if (id.equals(currentUser.getId())) {
                throw new BusinessException("SELF_DEACTIVATION_PROHIBITED", "You cannot deactivate or demote your own account.");
            }
            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);
            String newStatus = body.get("status").getAsString();

            Optional<User> uOpt = userDao.findById(id);
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                u.setStatus(newStatus);
                boolean updated = userDao.update(u);
                resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
            }
        } else if (path == null || "/".equals(path)) {
            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);
            User u = new User();
            u.setUsername(body.get("username").getAsString());
            u.setEmail(body.get("email").getAsString());
            u.setPasswordHash(PasswordUtil.hashPassword(body.get("password").getAsString()));
            u.setRole(body.get("role").getAsString());
            u.setFullName(body.get("fullName").getAsString());
            u.setStatus(body.has("status") ? body.get("status").getAsString() : "ACTIVE");

            User created = userDao.create(u);
            created.setPasswordHash(null);
            resp.getWriter().write(gson.toJson(ApiResponse.success(created)));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        User currentUser = (User) req.getAttribute("currentUser");

        if (path != null && path.length() > 1) {
            Long id = Long.parseLong(path.substring(1));
            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);

            Optional<User> uOpt = userDao.findById(id);
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                String newRole = body.has("role") ? body.get("role").getAsString() : u.getRole();
                String newStatus = body.has("status") ? body.get("status").getAsString() : u.getStatus();

                // Self demotion / deactivation check
                if (id.equals(currentUser.getId())) {
                    if (!newRole.equals(currentUser.getRole())) {
                        throw new BusinessException("SELF_DEMOTION_PROHIBITED", "You cannot demote your own user role.");
                    }
                    if (!"ACTIVE".equalsIgnoreCase(newStatus)) {
                        throw new BusinessException("SELF_DEACTIVATION_PROHIBITED", "You cannot deactivate your own account.");
                    }
                }

                u.setEmail(body.has("email") ? body.get("email").getAsString() : u.getEmail());
                u.setRole(newRole);
                u.setFullName(body.has("fullName") ? body.get("fullName").getAsString() : u.getFullName());
                u.setStatus(newStatus);

                boolean updated = userDao.update(u);
                resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
            }
        }
    }
}
