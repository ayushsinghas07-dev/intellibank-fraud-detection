package com.intellibank.service;

import com.intellibank.config.AppConfig;
import com.intellibank.dao.AuditLogDao;
import com.intellibank.dao.UserDao;
import com.intellibank.exception.BusinessException;
import com.intellibank.model.AuditLog;
import com.intellibank.model.User;
import com.intellibank.security.JwtProvider;
import com.intellibank.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserDao userDao;
    private final AuditLogDao auditLogDao;

    public AuthService() {
        this.userDao = new UserDao();
        this.auditLogDao = new AuditLogDao();
    }

    public AuthService(UserDao userDao, AuditLogDao auditLogDao) {
        this.userDao = userDao;
        this.auditLogDao = auditLogDao;
    }

    public String login(String usernameOrEmail, String plainPassword, String ipAddress) {
        if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty() || plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new BusinessException("INVALID_CREDENTIALS", "Username/email and password are required");
        }

        String identifier = usernameOrEmail.trim();
        Optional<User> userOpt = identifier.contains("@") ? userDao.findByEmail(identifier) : userDao.findByUsername(identifier);

        if (userOpt.isEmpty()) {
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password");
        }

        User user = userOpt.get();

        // Check Lockout Status
        if ("LOCKED".equalsIgnoreCase(user.getStatus()) || (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(LocalDateTime.now()))) {
            throw new BusinessException("ACCOUNT_LOCKED", "Account is temporarily locked due to multiple failed login attempts. Please try again later.");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException("ACCOUNT_INACTIVE", "Account is inactive. Please contact administrator.");
        }

        // Verify Password
        if (!PasswordUtil.checkPassword(plainPassword, user.getPasswordHash())) {
            int maxAttempts = AppConfig.getMaxLoginAttempts();
            int lockoutMinutes = AppConfig.getLockoutMinutes();
            userDao.incrementFailedAttempts(user.getId(), maxAttempts, lockoutMinutes);

            // Record failed attempt audit
            try {
                AuditLog audit = new AuditLog();
                audit.setUserId(user.getId());
                audit.setUsername(user.getUsername());
                audit.setRole(user.getRole());
                audit.setAction("LOGIN_FAILED");
                audit.setEntityType("USER");
                audit.setEntityId(user.getId());
                audit.setIpAddress(ipAddress);
                auditLogDao.create(null, audit);
            } catch (Exception ignored) {}

            throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password");
        }

        // Record Successful Login
        userDao.recordSuccessfulLogin(user.getId());

        try {
            AuditLog audit = new AuditLog();
            audit.setUserId(user.getId());
            audit.setUsername(user.getUsername());
            audit.setRole(user.getRole());
            audit.setAction("LOGIN_SUCCESS");
            audit.setEntityType("USER");
            audit.setEntityId(user.getId());
            audit.setIpAddress(ipAddress);
            auditLogDao.create(null, audit);
        } catch (Exception ignored) {}

        return JwtProvider.generateToken(user);
    }
}
