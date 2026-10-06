package com.intellibank;

import com.intellibank.config.AppConfig;
import com.intellibank.config.DatabaseConfig;
import com.intellibank.controller.*;
import com.intellibank.filter.AuthFilter;
import com.intellibank.filter.GlobalExceptionHandlerFilter;
import com.intellibank.filter.SecurityHeaderFilter;
import com.intellibank.util.DatabaseSeeder;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.EnumSet;

public class IntelliBankApplication {
    private static final Logger logger = LoggerFactory.getLogger(IntelliBankApplication.class);

    public static void main(String[] args) {
        // Handle CLI flags
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if ("--seed".equalsIgnoreCase(arg)) {
                    logger.info("Executing database seed command from CLI flag...");
                    DatabaseSeeder.seedDatabase();
                    logger.info("Seeding completed successfully.");
                }
            }
        }

        int port = AppConfig.getServerPort();
        logger.info("Starting IntelliBank Intelligent Banking & Fraud Detection Application on port {}...", port);

        // Verify DB Connection on Startup
        if (!DatabaseConfig.testConnection()) {
            logger.error("CRITICAL: Failed to connect to MySQL database at {}. Application cannot start.", AppConfig.getDbUrl());
            System.err.println("CRITICAL ERROR: Unable to establish MySQL database connection!");
            System.err.println("Verify that MySQL service is running and credentials in config.properties are correct.");
            System.exit(1);
        }
        logger.info("MySQL database connection verified successfully.");

        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Register Global Filters
        EnumSet<DispatcherType> dispatches = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD);
        context.addFilter(new FilterHolder(new SecurityHeaderFilter()), "/*", dispatches);
        context.addFilter(new FilterHolder(new GlobalExceptionHandlerFilter()), "/*", dispatches);
        context.addFilter(new FilterHolder(new AuthFilter()), "/api/*", dispatches);

        // Register REST API Servlets
        context.addServlet(new ServletHolder(new AuthServlet()), "/api/auth/*");
        context.addServlet(new ServletHolder(new CustomerServlet()), "/api/customers/*");
        context.addServlet(new ServletHolder(new AccountServlet()), "/api/accounts/*");
        context.addServlet(new ServletHolder(new TransactionServlet()), "/api/transactions/*");
        context.addServlet(new ServletHolder(new FraudServlet()), "/api/fraud/*");
        context.addServlet(new ServletHolder(new RiskServlet()), "/api/risk/*");
        context.addServlet(new ServletHolder(new AnalyticsServlet()), "/api/analytics/*");
        context.addServlet(new ServletHolder(new NotificationServlet()), "/api/notifications/*");
        context.addServlet(new ServletHolder(new GlobalSearchServlet()), "/api/search");
        context.addServlet(new ServletHolder(new ReportServlet()), "/api/reports/*");
        context.addServlet(new ServletHolder(new AuditLogServlet()), "/api/audit-log");
        context.addServlet(new ServletHolder(new UserServlet()), "/api/users/*");
        context.addServlet(new ServletHolder(new HealthServlet()), "/api/health");

        // Static Asset Handler for SPA (HTML5/CSS3/Vanilla JS)
        URL staticUrl = IntelliBankApplication.class.getClassLoader().getResource("static");
        if (staticUrl != null) {
            context.setBaseResource(Resource.newResource(staticUrl));
            ServletHolder staticHolder = new ServletHolder("default", DefaultServlet.class);
            staticHolder.setInitParameter("dirAllowed", "false");
            staticHolder.setInitParameter("pathInfoOnly", "true");
            context.addServlet(staticHolder, "/*");
            logger.info("Serving static frontend files from classpath: /static");
        } else {
            logger.warn("Static resources folder '/static' not found on classpath.");
        }

        server.setHandler(context);

        try {
            server.start();
            logger.info("=================================================================");
            logger.info("IntelliBank Server started successfully!");
            logger.info("URL: http://localhost:{}/", port);
            logger.info("Demo Credentials:");
            logger.info("  Admin: admin / Admin@12345");
            logger.info("  Analyst: analyst / Analyst@12345");
            logger.info("  Viewer: viewer / Viewer@12345");
            logger.info("=================================================================");
            server.join();
        } catch (Exception e) {
            logger.error("Server startup failed", e);
            System.exit(1);
        }
    }
}
