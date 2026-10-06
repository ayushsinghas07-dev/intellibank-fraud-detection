package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellibank.dao.AlertNoteDao;
import com.intellibank.dao.FraudAlertDao;
import com.intellibank.dao.FraudRuleDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.AlertNote;
import com.intellibank.model.FraudAlert;
import com.intellibank.model.FraudRule;
import com.intellibank.model.User;
import com.intellibank.service.TransactionService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FraudServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final FraudAlertDao alertDao = new FraudAlertDao();
    private final FraudRuleDao ruleDao = new FraudRuleDao();
    private final AlertNoteDao noteDao = new AlertNoteDao();
    private final TransactionService transactionService = new TransactionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        if (path != null && path.startsWith("/rules")) {
            List<FraudRule> rules = ruleDao.findAll();
            resp.getWriter().write(gson.toJson(ApiResponse.success(rules)));
            return;
        }

        if (path == null || "/alerts".equalsIgnoreCase(path) || "/".equals(path)) {
            String query = req.getParameter("query");
            String status = req.getParameter("status");
            String riskLevel = req.getParameter("riskLevel");

            int page = parseInt(req.getParameter("page"), 1);
            int pageSize = parseInt(req.getParameter("pageSize"), 15);
            int offset = (page - 1) * pageSize;

            List<FraudAlert> list = alertDao.search(query, status, riskLevel, offset, pageSize);
            int total = alertDao.countSearch(query, status, riskLevel);
            Map<String, Integer> statusCounts = alertDao.getStatusCounts();

            Map<String, Object> data = new HashMap<>();
            data.put("alerts", list);
            data.put("page", page);
            data.put("pageSize", pageSize);
            data.put("total", total);
            data.put("totalPages", (int) Math.ceil((double) total / pageSize));
            data.put("statusCounts", statusCounts);

            resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
        } else if (path.startsWith("/alerts/")) {
            try {
                String[] parts = path.split("/");
                Long id = Long.parseLong(parts[2]);
                Optional<FraudAlert> aOpt = alertDao.findById(id);
                if (aOpt.isPresent()) {
                    FraudAlert alert = aOpt.get();
                    List<AlertNote> notes = noteDao.findByAlertId(id);

                    Map<String, Object> data = new HashMap<>();
                    data.put("alert", alert);
                    data.put("notes", notes);

                    resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write(gson.toJson(ApiResponse.error("ALERT_NOT_FOUND", "Fraud alert not found")));
                }
            } catch (Exception e) {
                resp.setStatus(400);
                resp.getWriter().write(gson.toJson(ApiResponse.error("INVALID_ID", "Invalid alert ID format")));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        User currentUser = (User) req.getAttribute("currentUser");

        if (path != null && path.contains("/status")) {
            String[] parts = path.split("/");
            Long alertId = Long.parseLong(parts[2]);

            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);
            String newStatus = body.get("status").getAsString();
            String noteText = body.has("noteText") ? body.get("noteText").getAsString() : null;
            boolean freezeAccount = body.has("freezeAccount") && body.get("freezeAccount").getAsBoolean();

            boolean success = transactionService.resolveFraudAlert(alertId, newStatus, noteText, freezeAccount, currentUser);
            resp.getWriter().write(gson.toJson(ApiResponse.success(success)));

        } else if (path != null && path.contains("/notes")) {
            String[] parts = path.split("/");
            Long alertId = Long.parseLong(parts[2]);

            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);
            String noteText = body.get("noteText").getAsString();

            AlertNote note = new AlertNote();
            note.setAlertId(alertId);
            note.setUserId(currentUser.getId());
            note.setNoteText(noteText);

            try {
                AlertNote created = noteDao.create(null, note);
                resp.getWriter().write(gson.toJson(ApiResponse.success(created)));
            } catch (Exception e) {
                resp.setStatus(500);
                resp.getWriter().write(gson.toJson(ApiResponse.error("NOTE_FAILED", e.getMessage())));
            }
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        if (path != null && path.startsWith("/rules/")) {
            String code = path.substring("/rules/".length());
            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);

            int points = body.get("points").getAsInt();
            boolean enabled = body.get("enabled").getAsBoolean();
            String thresholdParams = body.get("thresholdParams").isJsonObject() ? gson.toJson(body.get("thresholdParams")) : body.get("thresholdParams").getAsString();

            boolean updated = ruleDao.updateRule(code, points, enabled, thresholdParams);
            resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
        }
    }

    private int parseInt(String val, int def) {
        if (val == null) return def;
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }
}
