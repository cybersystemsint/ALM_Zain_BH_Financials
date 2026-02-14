package com.zain.bh.alm.financials.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.bh.alm.financials.entity.Notification;
import com.zain.bh.alm.financials.repository.NotificationRepository;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    @Value("${notification.email.from:noreply@example.com}")
    private String emailFrom;

    @Value("${notification.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${email.service.enabled:false}")
    private boolean emailServiceEnabled;

    private JavaMailSender emailSender;
    private boolean emailSenderAvailable = false;

    public NotificationService(ApplicationContext applicationContext, NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
        try {
            this.emailSender = applicationContext.getBean(JavaMailSender.class);
            this.emailSenderAvailable = true;
            LOGGER.info("Email service initialized successfully");
        } catch (NoSuchBeanDefinitionException e) {
            LOGGER.warn("JavaMailSender not available. Email notifications will be disabled: {}", e.getMessage());
            this.emailSenderAvailable = false;
        }
    }

    @Transactional
    public void sendNotification(String subject, String message) {
        Notification notification = new Notification();
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setSent(false);

        try {
            if (!notificationsEnabled) {
                LOGGER.info("Notifications are disabled in configuration. Notification will be saved but not sent.");
            } else if (!emailSenderAvailable || !emailServiceEnabled) {
                LOGGER.info("Email sender not available or disabled. Notification will be saved but not sent.");
            } else {
                sendEmail(emailFrom, subject, message);
                notification.setSent(true);
            }
            notificationRepository.save(notification);
            LOGGER.info("Notification processed: {}", subject);
        } catch (Exception e) {
            LOGGER.error("Failed to process notification: {}", e.getMessage(), e);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void sendNotificationToRecipients(List<String> recipients, String subject, String message) {
        if (recipients == null || recipients.isEmpty()) {
            LOGGER.warn("No recipients provided for notification: {}", subject);
            return;
        }

        Notification notification = new Notification();
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setSent(false);
        notification.setCustomRecipients(String.join(",", recipients));

        try {
            if (!notificationsEnabled) {
                LOGGER.info("Notifications are disabled in configuration. Notification will be saved but not sent.");
            } else if (!emailSenderAvailable || !emailServiceEnabled) {
                LOGGER.info("Email sender not available or disabled. Notification will be saved but not sent.");
            } else {
                for (String recipient : recipients) {
                    sendEmail(recipient, subject, message);
                }
                notification.setSent(true);
            }
            notificationRepository.save(notification);
            LOGGER.info("Custom notification processed: {}", subject);
        } catch (Exception e) {
            LOGGER.error("Failed to process custom notification: {}", e.getMessage(), e);
            notificationRepository.save(notification);
        }
    }

    private void sendEmail(String to, String subject, String text) {
        if (!emailSenderAvailable || !emailServiceEnabled) {
            LOGGER.warn("Attempted to send email but email service is not available or disabled");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            emailSender.send(message);
            LOGGER.debug("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            LOGGER.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    @Transactional
    public int resendFailedNotifications() {
        LOGGER.info("Checking for failed notifications to resend");
        List<Notification> failedNotifications = notificationRepository.findBySentFalse();
        int resendCount = 0;

        if (failedNotifications.isEmpty()) {
            LOGGER.info("No failed notifications found to resend");
            return 0;
        }

        if (!notificationsEnabled) {
            LOGGER.info("Notifications are disabled in configuration. No notifications will be resent.");
            return 0;
        }

        if (!emailSenderAvailable || !emailServiceEnabled) {
            LOGGER.info("Email sender not available or disabled. No notifications will be resent.");
            return 0;
        }

        for (Notification notification : failedNotifications) {
            try {
                if (notification.getCustomRecipients() != null && !notification.getCustomRecipients().isEmpty()) {
                    String[] recipients = notification.getCustomRecipients().split(",");
                    for (String recipient : recipients) {
                        sendEmail(recipient.trim(), notification.getSubject(), notification.getMessage());
                    }
                } else {
                    sendEmail(emailFrom, notification.getSubject(), notification.getMessage());
                }
                notification.setSent(true);
                notification.setUpdatedAt(LocalDateTime.now());
                notificationRepository.save(notification);
                resendCount++;
                LOGGER.info("Successfully resent notification: {}", notification.getSubject());
            } catch (Exception e) {
                LOGGER.error("Failed to resend notification {}: {}", notification.getId(), e.getMessage(), e);
            }
        }

        LOGGER.info("Resent {} out of {} failed notifications", resendCount, failedNotifications.size());
        return resendCount;
    }

    public boolean isEmailServiceAvailable() {
        return emailSenderAvailable && emailServiceEnabled;
    }
}
