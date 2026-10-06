package com.intellibank.service;

import com.intellibank.dao.AccountDao;
import com.intellibank.dao.CustomerDao;
import com.intellibank.dao.FraudAlertDao;
import com.intellibank.exception.BusinessException;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.FraudAlert;

import java.math.BigDecimal;
import java.util.List;

public class CustomerService {

    private final CustomerDao customerDao;
    private final AccountDao accountDao;
    private final FraudAlertDao alertDao;

    public CustomerService() {
        this.customerDao = new CustomerDao();
        this.accountDao = new AccountDao();
        this.alertDao = new FraudAlertDao();
    }

    public CustomerService(CustomerDao customerDao, AccountDao accountDao, FraudAlertDao alertDao) {
        this.customerDao = customerDao;
        this.accountDao = accountDao;
        this.alertDao = alertDao;
    }

    public boolean deactivateCustomer(Long customerId) {
        Customer c = customerDao.findById(customerId)
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer not found"));

        // Safety check 1: Check non-zero balances on customer accounts
        List<Account> accounts = accountDao.findByCustomerId(customerId);
        for (Account a : accounts) {
            if (a.getBalance() != null && a.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("CUSTOMER_HAS_NON_ZERO_BALANCE", String.format("Cannot deactivate customer %s: Account %s has a non-zero balance of $%s", c.getFullName(), a.getAccountNumber(), a.getBalance().toPlainString()));
            }
        }

        // Safety check 2: Check open / under review fraud alerts
        List<FraudAlert> openAlerts = alertDao.search(c.getCustomerNumber(), "OPEN", null, 0, 10);
        List<FraudAlert> reviewAlerts = alertDao.search(c.getCustomerNumber(), "UNDER_REVIEW", null, 0, 10);

        if (!openAlerts.isEmpty() || !reviewAlerts.isEmpty()) {
            throw new BusinessException("CUSTOMER_HAS_PENDING_ALERTS", String.format("Cannot deactivate customer %s: Customer has %d active fraud alert(s) under investigation", c.getFullName(), (openAlerts.size() + reviewAlerts.size())));
        }

        c.setStatus("DEACTIVATED");
        return customerDao.update(c);
    }
}
