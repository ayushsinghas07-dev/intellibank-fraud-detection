package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.intellibank.dao.AccountDao;
import com.intellibank.dao.CustomerDao;
import com.intellibank.dao.RiskScoreDao;
import com.intellibank.dao.TransactionDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.fraud.FraudDetectionService;
import com.intellibank.fraud.FraudDetectionService.FraudResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.RiskScore;
import com.intellibank.model.Transaction;
import com.intellibank.risk.RiskScoringService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RiskServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final RiskScoreDao riskScoreDao = new RiskScoreDao();
    private final TransactionDao transactionDao = new TransactionDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final AccountDao accountDao = new AccountDao();
    private final FraudDetectionService fraudDetectionService = new FraudDetectionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        if (path != null && path.startsWith("/analyze/")) {
            try {
                Long txnId = Long.parseLong(path.substring("/analyze/".length()));
                Optional<RiskScore> rsOpt = riskScoreDao.findByTransactionId(txnId);
                Optional<Transaction> tOpt = transactionDao.findById(txnId);

                if (tOpt.isPresent()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("transaction", tOpt.get());
                    data.put("riskScore", rsOpt.orElse(null));
                    resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
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
        String path = req.getPathInfo();

        if ("/simulate".equalsIgnoreCase(path)) {
            Transaction transientTxn = gson.fromJson(req.getReader(), Transaction.class);

            Account srcAcc = accountDao.findById(transientTxn.getSourceAccountId()).orElse(null);
            Customer cust = (srcAcc != null) ? customerDao.findById(srcAcc.getCustomerId()).orElse(null) : null;
            List<Transaction> history = (srcAcc != null) ? transactionDao.findByAccountId(srcAcc.getId(), 50) : List.of();

            // Run real engine transiently (0 DB modifications)
            FraudResult fraudResult = fraudDetectionService.evaluateTransaction(transientTxn, cust, srcAcc, history);
            int score = fraudResult.getTotalScore();
            String level = RiskScoringService.calculateRiskLevel(score);
            String action = RiskScoringService.getRecommendedAction(level);
            String explanation = RiskScoringService.buildPlainLanguageExplanation(score, level, fraudResult.getTriggeredRules());

            Map<String, Object> data = new HashMap<>();
            data.put("score", score);
            data.put("level", level);
            data.put("recommendedAction", action);
            data.put("explanation", explanation);
            data.put("triggeredRules", fraudResult.getTriggeredRules());
            data.put("allRulesEvaluated", fraudResult.getAllResults());

            resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
        }
    }
}
