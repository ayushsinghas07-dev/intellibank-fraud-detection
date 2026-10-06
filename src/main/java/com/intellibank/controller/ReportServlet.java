package com.intellibank.controller;

import com.intellibank.dao.AccountDao;
import com.intellibank.dao.CustomerDao;
import com.intellibank.dao.FraudAlertDao;
import com.intellibank.dao.TransactionDao;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.FraudAlert;
import com.intellibank.model.Transaction;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class ReportServlet extends HttpServlet {
    private final CustomerDao customerDao = new CustomerDao();
    private final AccountDao accountDao = new AccountDao();
    private final TransactionDao transactionDao = new TransactionDao();
    private final FraudAlertDao alertDao = new FraudAlertDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        resp.setContentType("text/csv;charset=UTF-8");

        PrintWriter writer = resp.getWriter();

        if (path != null && path.contains("customers")) {
            resp.setHeader("Content-Disposition", "attachment; filename=\"customers-report.csv\"");
            writer.println("Customer Number,First Name,Last Name,Email,Phone,KYC Status,Risk Category,Status,Location");
            List<Customer> list = customerDao.search(req.getParameter("query"), req.getParameter("status"), req.getParameter("riskCategory"), 0, 5000);
            for (Customer c : list) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        escapeCsv(c.getCustomerNumber()),
                        escapeCsv(c.getFirstName()),
                        escapeCsv(c.getLastName()),
                        escapeCsv(c.getEmail()),
                        escapeCsv(c.getPhone()),
                        escapeCsv(c.getKycStatus()),
                        escapeCsv(c.getRiskCategory()),
                        escapeCsv(c.getStatus()),
                        escapeCsv(c.getHomeLocation())
                );
            }
        } else if (path != null && path.contains("accounts")) {
            resp.setHeader("Content-Disposition", "attachment; filename=\"accounts-report.csv\"");
            writer.println("Account Number,Customer Name,Type,Balance,Daily Limit,Status");
            List<Account> list = accountDao.search(req.getParameter("query"), req.getParameter("status"), req.getParameter("accountType"), 0, 5000);
            for (Account a : list) {
                writer.printf("%s,%s,%s,%s,%s,%s%n",
                        escapeCsv(a.getAccountNumber()),
                        escapeCsv(a.getCustomerName()),
                        escapeCsv(a.getAccountType()),
                        a.getBalance() != null ? a.getBalance().toPlainString() : "0.00",
                        a.getDailyLimit() != null ? a.getDailyLimit().toPlainString() : "0.00",
                        escapeCsv(a.getStatus())
                );
            }
        } else if (path != null && path.contains("fraud-alerts")) {
            resp.setHeader("Content-Disposition", "attachment; filename=\"fraud-alerts-report.csv\"");
            writer.println("Alert Number,Customer Name,Transaction Ref,Risk Score,Risk Level,Status,Created At");
            List<FraudAlert> list = alertDao.search(req.getParameter("query"), req.getParameter("status"), req.getParameter("riskLevel"), 0, 5000);
            for (FraudAlert fa : list) {
                writer.printf("%s,%s,%s,%d,%s,%s,%s%n",
                        escapeCsv(fa.getAlertNumber()),
                        escapeCsv(fa.getCustomerName()),
                        escapeCsv(fa.getTransactionReference()),
                        fa.getRiskScore(),
                        escapeCsv(fa.getRiskLevel()),
                        escapeCsv(fa.getStatus()),
                        fa.getCreatedAt() != null ? fa.getCreatedAt().toString() : ""
                );
            }
        } else {
            // Default: Transactions export
            resp.setHeader("Content-Disposition", "attachment; filename=\"transactions-report.csv\"");
            writer.println("Reference Number,Customer,Source Account,Destination Account,Amount,Type,Channel,Status,Risk Score,Risk Level,Fraud Status,Timestamp");
            List<Transaction> list = transactionDao.search(
                    req.getParameter("query"), req.getParameter("type"), req.getParameter("channel"),
                    req.getParameter("status"), req.getParameter("riskLevel"), req.getParameter("startDate"),
                    req.getParameter("endDate"), 0, 5000
            );
            for (Transaction t : list) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s%n",
                        escapeCsv(t.getReferenceNumber()),
                        escapeCsv(t.getCustomerName()),
                        escapeCsv(t.getSourceAccountNumber()),
                        escapeCsv(t.getDestinationAccountNumber() != null ? t.getDestinationAccountNumber() : "N/A"),
                        t.getAmount() != null ? t.getAmount().toPlainString() : "0.00",
                        escapeCsv(t.getTransactionType()),
                        escapeCsv(t.getChannel()),
                        escapeCsv(t.getStatus()),
                        t.getRiskScore(),
                        escapeCsv(t.getRiskLevel()),
                        escapeCsv(t.getFraudStatus()),
                        t.getTransactionTimestamp() != null ? t.getTransactionTimestamp().toString() : ""
                );
            }
        }
    }

    private String escapeCsv(String input) {
        if (input == null) return "";
        if (input.contains(",") || input.contains("\"") || input.contains("\n")) {
            return "\"" + input.replace("\"", "\"\"") + "\"";
        }
        return input;
    }
}
