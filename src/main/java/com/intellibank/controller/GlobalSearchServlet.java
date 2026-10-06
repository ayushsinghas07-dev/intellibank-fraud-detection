package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.intellibank.dao.AccountDao;
import com.intellibank.dao.CustomerDao;
import com.intellibank.dao.FraudAlertDao;
import com.intellibank.dao.TransactionDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.FraudAlert;
import com.intellibank.model.Transaction;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalSearchServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final CustomerDao customerDao = new CustomerDao();
    private final AccountDao accountDao = new AccountDao();
    private final TransactionDao transactionDao = new TransactionDao();
    private final FraudAlertDao alertDao = new FraudAlertDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String q = req.getParameter("q");

        if (q == null || q.trim().isEmpty()) {
            resp.getWriter().write(gson.toJson(ApiResponse.success(Map.of())));
            return;
        }

        q = q.trim();
        List<Customer> customers = customerDao.search(q, null, null, 0, 5);
        List<Account> accounts = accountDao.search(q, null, null, 0, 5);
        List<Transaction> transactions = transactionDao.search(q, null, null, null, null, null, null, 0, 5);
        List<FraudAlert> alerts = alertDao.search(q, null, null, 0, 5);

        Map<String, Object> data = new HashMap<>();
        data.put("customers", customers);
        data.put("accounts", accounts);
        data.put("transactions", transactions);
        data.put("alerts", alerts);

        resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
    }
}
