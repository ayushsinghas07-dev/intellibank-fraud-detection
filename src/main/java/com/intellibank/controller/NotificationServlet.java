package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.intellibank.dao.NotificationDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.Notification;
import com.intellibank.model.User;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final NotificationDao notificationDao = new NotificationDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User currentUser = (User) req.getAttribute("currentUser");

        List<Notification> list = notificationDao.findForUserOrRole(currentUser.getId(), currentUser.getRole(), 30);
        int unreadCount = notificationDao.countUnreadForUserOrRole(currentUser.getId(), currentUser.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("notifications", list);
        data.put("unreadCount", unreadCount);

        resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        User currentUser = (User) req.getAttribute("currentUser");

        if (path != null && path.endsWith("/read-all")) {
            boolean updated = notificationDao.markAllAsReadForUserOrRole(currentUser.getId(), currentUser.getRole());
            resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
        } else if (path != null && path.length() > 1) {
            try {
                String idStr = path.substring(1).replace("/read", "");
                Long id = Long.parseLong(idStr);
                boolean updated = notificationDao.markAsRead(id);
                resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
            } catch (Exception e) {
                resp.setStatus(400);
                resp.getWriter().write(gson.toJson(ApiResponse.error("INVALID_ID", e.getMessage())));
            }
        }
    }
}
