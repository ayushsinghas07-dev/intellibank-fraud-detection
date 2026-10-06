package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.intellibank.dao.AuditLogDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.AuditLog;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuditLogServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final AuditLogDao auditLogDao = new AuditLogDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        String username = req.getParameter("username");
        String action = req.getParameter("action");
        String entityType = req.getParameter("entityType");
        String startDate = req.getParameter("startDate");
        String endDate = req.getParameter("endDate");

        int page = parseInt(req.getParameter("page"), 1);
        int pageSize = parseInt(req.getParameter("pageSize"), 20);
        int offset = (page - 1) * pageSize;

        List<AuditLog> list = auditLogDao.search(username, action, entityType, startDate, endDate, offset, pageSize);
        int total = auditLogDao.countSearch(username, action, entityType, startDate, endDate);

        Map<String, Object> data = new HashMap<>();
        data.put("auditLogs", list);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("total", total);
        data.put("totalPages", (int) Math.ceil((double) total / pageSize));

        resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
    }

    private int parseInt(String val, int def) {
        if (val == null) return def;
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }
}
