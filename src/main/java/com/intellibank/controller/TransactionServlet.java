package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.intellibank.dao.TransactionDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.Transaction;
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

public class TransactionServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final TransactionDao transactionDao = new TransactionDao();
    private final TransactionService transactionService = new TransactionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        if (path == null || "/".equals(path)) {
            String query = req.getParameter("query");
            String type = req.getParameter("type");
            String channel = req.getParameter("channel");
            String status = req.getParameter("status");
            String riskLevel = req.getParameter("riskLevel");
            String startDate = req.getParameter("startDate");
            String endDate = req.getParameter("endDate");

            int page = parseInt(req.getParameter("page"), 1);
            int pageSize = parseInt(req.getParameter("pageSize"), 15);
            int offset = (page - 1) * pageSize;

            List<Transaction> list = transactionDao.search(query, type, channel, status, riskLevel, startDate, endDate, offset, pageSize);
            int total = transactionDao.countSearch(query, type, channel, status, riskLevel, startDate, endDate);

            Map<String, Object> data = new HashMap<>();
            data.put("transactions", list);
            data.put("page", page);
            data.put("pageSize", pageSize);
            data.put("total", total);
            data.put("totalPages", (int) Math.ceil((double) total / pageSize));

            resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
        } else {
            try {
                Long id = Long.parseLong(path.substring(1));
                Optional<Transaction> tOpt = transactionDao.findById(id);
                if (tOpt.isPresent()) {
                    resp.getWriter().write(gson.toJson(ApiResponse.success(tOpt.get())));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write(gson.toJson(ApiResponse.error("TRANSACTION_NOT_FOUND", "Transaction not found")));
                }
            } catch (NumberFormatException e) {
                resp.setStatus(400);
                resp.getWriter().write(gson.toJson(ApiResponse.error("INVALID_ID", "Invalid transaction ID format")));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User currentUser = (User) req.getAttribute("currentUser");

        Transaction txnRequest = gson.fromJson(req.getReader(), Transaction.class);
        if (txnRequest.getIdempotencyKey() == null) {
            txnRequest.setIdempotencyKey(req.getHeader("Idempotency-Key"));
        }

        Transaction result = transactionService.processTransaction(txnRequest, currentUser);
        resp.getWriter().write(gson.toJson(ApiResponse.success(result)));
    }

    private int parseInt(String val, int def) {
        if (val == null) return def;
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }
}
