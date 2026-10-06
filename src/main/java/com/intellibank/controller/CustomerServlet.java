package com.intellibank.controller;

import com.google.gson.Gson;
import com.intellibank.dao.CustomerDao;
import com.intellibank.dto.ApiResponse;
import com.intellibank.model.Customer;
import com.intellibank.service.CustomerService;
import com.intellibank.util.JsonUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CustomerServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();
    private final CustomerDao customerDao = new CustomerDao();
    private final CustomerService customerService = new CustomerService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        if (path == null || "/".equals(path)) {
            String query = req.getParameter("query");
            String status = req.getParameter("status");
            String riskCategory = req.getParameter("riskCategory");
            int page = parseInt(req.getParameter("page"), 1);
            int pageSize = parseInt(req.getParameter("pageSize"), 15);
            int offset = (page - 1) * pageSize;

            List<Customer> list = customerDao.search(query, status, riskCategory, offset, pageSize);
            int total = customerDao.countSearch(query, status, riskCategory);

            Map<String, Object> data = new HashMap<>();
            data.put("customers", list);
            data.put("page", page);
            data.put("pageSize", pageSize);
            data.put("total", total);
            data.put("totalPages", (int) Math.ceil((double) total / pageSize));

            resp.getWriter().write(gson.toJson(ApiResponse.success(data)));
        } else {
            try {
                Long id = Long.parseLong(path.substring(1));
                Optional<Customer> cOpt = customerDao.findById(id);
                if (cOpt.isPresent()) {
                    resp.getWriter().write(gson.toJson(ApiResponse.success(cOpt.get())));
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write(gson.toJson(ApiResponse.error("CUSTOMER_NOT_FOUND", "Customer not found")));
                }
            } catch (NumberFormatException e) {
                resp.setStatus(400);
                resp.getWriter().write(gson.toJson(ApiResponse.error("INVALID_ID", "Invalid customer ID format")));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        if (path != null && path.endsWith("/deactivate")) {
            String[] parts = path.split("/");
            Long id = Long.parseLong(parts[1]);
            boolean deactivated = customerService.deactivateCustomer(id);
            resp.getWriter().write(gson.toJson(ApiResponse.success(deactivated)));
        } else if (path == null || "/".equals(path)) {
            Customer c = gson.fromJson(req.getReader(), Customer.class);
            if (c.getCustomerNumber() == null) {
                c.setCustomerNumber("CUST-" + String.format("%05d", (int)(Math.random() * 90000 + 10000)));
            }
            Customer created = customerDao.create(c);
            resp.getWriter().write(gson.toJson(ApiResponse.success(created)));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        if (path != null && path.length() > 1) {
            Long id = Long.parseLong(path.substring(1));
            Customer c = gson.fromJson(req.getReader(), Customer.class);
            c.setId(id);
            boolean updated = customerDao.update(c);
            resp.getWriter().write(gson.toJson(ApiResponse.success(updated)));
        }
    }

    private int parseInt(String val, int def) {
        if (val == null) return def;
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }
}
