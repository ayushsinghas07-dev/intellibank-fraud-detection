package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellibank.dao.AccountDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.Account;
import com.intellibank.model.Transaction;
import com.intellibank.model.User;
import com.intellibank.service.TransactionService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AccountServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final AccountDao accountDao = new AccountDao();
    private final TransactionService transactionService = new TransactionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        if (path == null || "/".equals(path)) {
            String query = req.getParameter("query");
            String status = req.getParameter("status");
            String accountType = req.getParameter("accountType");
            String customerIdStr = req.getParameter("customerId");

            if (customerIdStr != null && !customerIdStr.trim().isEmpty()) {
                List<Account> accounts = accountDao.findByCustomerId(Long.parseLong(customerIdStr.trim()));
                resp.getWriter().write(gson.toJson(ApiResponse.success(accounts)));
                return;
            }

            int page = parseInt(req.getParameter("page"), 1);
            int pageSize = parseInt(req.getParameter("pageSize"), 15);
            int offset = (page - 1) * pageSize;

            List<Account> list = accountDao.search(query, status, accountType, offset, pageSize);
            int total = accountDao.countSearch(query, status, accountType);

            Map<String, Object> data = new HashMap<>();
            data.put("accounts", list);
            data.put("page", page);
            data.put("pageSize", pageSize);
            data.put("total", total);
            data.put("totalPages", (int) Math.ceil((double) total / pageSize));

            resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
        } else {
            try {
                Long id = Long.parseLong(path.substring(1));
                Optional<Account> aOpt = accountDao.findById(id);
                if (aOpt.isPresent()) {
                    resp.getWriter().write(gson.toJson(ApiResponse.success(aOpt.get())));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write(gson.toJson(ApiResponse.error("ACCOUNT_NOT_FOUND", "Account not found")));
                }
            } catch (NumberFormatException e) {
                resp.setStatus(400);
                resp.getWriter().write(gson.toJson(ApiResponse.error("INVALID_ID", "Invalid account ID format")));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        User currentUser = (User) req.getAttribute("currentUser");

        if ("/deposit".equalsIgnoreCase(path) || "/withdraw".equalsIgnoreCase(path) || "/transfer".equalsIgnoreCase(path)) {
            JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);

            Transaction txnRequest = new Transaction();
            txnRequest.setSourceAccountId(body.get("sourceAccountId").getAsLong());
            if (body.has("destinationAccountId") && !body.get("destinationAccountId").isJsonNull()) {
                txnRequest.setDestinationAccountId(body.get("destinationAccountId").getAsLong());
            }
            txnRequest.setAmount(body.get("amount").getAsBigDecimal());
            txnRequest.setTransactionType("/deposit".equalsIgnoreCase(path) ? "DEPOSIT" : ("/withdraw".equalsIgnoreCase(path) ? "WITHDRAWAL" : "TRANSFER"));
            txnRequest.setChannel(body.has("channel") ? body.get("channel").getAsString() : "WEB");
            txnRequest.setMerchantName(body.has("merchantName") ? body.get("merchantName").getAsString() : "IntelliBank Digital");
            txnRequest.setMerchantCategory(body.has("merchantCategory") ? body.get("merchantCategory").getAsString() : "Banking");
            txnRequest.setLocation(body.has("location") ? body.get("location").getAsString() : "New York, US");
            txnRequest.setIdempotencyKey(req.getHeader("Idempotency-Key"));

            Transaction result = transactionService.processTransaction(txnRequest, currentUser);
            resp.getWriter().write(gson.toJson(ApiResponse.success(result)));

        } else if (path != null && path.endsWith("/freeze")) {
            Long id = Long.parseLong(path.split("/")[1]);
            boolean updated = accountDao.updateStatus(id, "FROZEN");
            resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
        } else if (path != null && path.endsWith("/unfreeze")) {
            Long id = Long.parseLong(path.split("/")[1]);
            boolean updated = accountDao.updateStatus(id, "ACTIVE");
            resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
        } else if (path == null || "/".equals(path)) {
            Account a = gson.fromJson(req.getReader(), Account.class);
            if (a.getAccountNumber() == null) {
                a.setAccountNumber("ACC-" + (100000 + (int)(Math.random() * 900000)));
            }
            Account created = accountDao.create(a);
            resp.getWriter().write(gson.toJson(ApiResponse.success(created)));
        }
    }

    private int parseInt(String val, int def) {
        if (val == null) return def;
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }
}
