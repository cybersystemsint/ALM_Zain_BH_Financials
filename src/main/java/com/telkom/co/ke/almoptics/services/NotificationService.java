package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.Notification;
import com.telkom.co.ke.almoptics.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for sending notifications about system events,
 * particularly related to asset status changes.
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final ApplicationContext applicationContext;
    private final NotificationRepository notificationRepository;

    @Value("${notification.email.from:noreply@example.com}")
    private String emailFrom;

    @Value("${notification.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${email.service.enabled:false}")
    private boolean emailServiceEnabled;

    private JavaMailSender emailSender;
    private boolean emailSenderAvailable = false;

    @Autowired
    public NotificationService(
            ApplicationContext applicationContext,
            NotificationRepository notificationRepository) {
        this.applicationContext = applicationContext;
        this.notificationRepository = notificationRepository;

        // Try to get the JavaMailSender bean, but don't fail if it's not available
        try {
            this.emailSender = applicationContext.getBean(JavaMailSender.class);
            this.emailSenderAvailable = true;
            logger.info("Email service initialized successfully");
        } catch (NoSuchBeanDefinitionException e) {
            logger.warn("JavaMailSender not available. Email notifications will be disabled: {}", e.getMessage());
            this.emailSenderAvailable = false;
        }
    }

    /**
     * Sends a notification about an event
     *
     * @param subject The notification subject
     * @param message The notification message
     */
    @Transactional
    public void sendNotification(String subject, String message) {
        // Always log the notification
        Notification notification = new Notification();
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setSent(false);

        try {
            // Skip checking NotificationSetting table; rely on config properties
            if (!notificationsEnabled) {
                logger.info("Notifications are disabled in configuration. Notification will be saved but not sent.");
            } else if (!emailSenderAvailable || !emailServiceEnabled) {
                logger.info("Email sender not available or disabled. Notification will be saved but not sent.");
            } else {
                // Send email to a default recipient (e.g., configured in properties or hardcoded for now)
                sendEmail(emailFrom, subject, message); // Using emailFrom as a fallback recipient
                notification.setSent(true);
            }

            // Save the notification record
            notificationRepository.save(notification);

            logger.info("Notification processed: {}", subject);
        } catch (Exception e) {
            logger.error("Failed to process notification: {}", e.getMessage(), e);
            // Still save the notification record to track the failure
            notificationRepository.save(notification);
            // Avoid re-throwing the exception to prevent transaction rollback
        }
    }

    /**
     * Sends a notification to specific recipient(s)
     *
     * @param recipients List of email addresses
     * @param subject The notification subject
     * @param message The notification message
     */
    @Transactional
    public void sendNotificationToRecipients(List<String> recipients, String subject, String message) {
        if (recipients == null || recipients.isEmpty()) {
            logger.warn("No recipients provided for notification: {}", subject);
            return;
        }

        // Log the notification
        Notification notification = new Notification();
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setSent(false);
        notification.setCustomRecipients(String.join(",", recipients));

        try {
            if (!notificationsEnabled) {
                logger.info("Notifications are disabled in configuration. Notification will be saved but not sent.");
            } else if (!emailSenderAvailable || !emailServiceEnabled) {
                logger.info("Email sender not available or disabled. Notification will be saved but not sent.");
            } else {
                // Send emails to all specified recipients
                for (String recipient : recipients) {
                    sendEmail(recipient, subject, message);
                }
                notification.setSent(true);
            }

            // Save the notification record
            notificationRepository.save(notification);

            logger.info("Custom notification processed: {}", subject);
        } catch (Exception e) {
            logger.error("Failed to process custom notification: {}", e.getMessage(), e);
            // Still save the notification record to track the failure
            notificationRepository.save(notification);
            // Avoid re-throwing the exception to prevent transaction rollback
        }
    }

    /**
     * Sends an email notification
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param text Email body
     */
    private void sendEmail(String to, String subject, String text) {
        if (!emailSenderAvailable || !emailServiceEnabled) {
            logger.warn("Attempted to send email but email service is not available or disabled");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            emailSender.send(message);
            logger.debug("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            // Avoid re-throwing the exception to prevent transaction rollback
        }
    }

    /**
     * Resends failed notifications
     * Can be scheduled or triggered manually
     *
     * @return Number of notifications resent
     */
    @Transactional
    public int resendFailedNotifications() {
        logger.info("Checking for failed notifications to resend");

        // Get notifications that failed to send
        List<Notification> failedNotifications = notificationRepository.findBySentFalse();
        int resendCount = 0;

        if (failedNotifications.isEmpty()) {
            logger.info("No failed notifications found to resend");
            return 0;
        }

        if (!notificationsEnabled) {
            logger.info("Notifications are disabled in configuration. No notifications will be resent.");
            return 0;
        }

        if (!emailSenderAvailable || !emailServiceEnabled) {
            logger.info("Email sender not available or disabled. No notifications will be resent.");
            return 0;
        }

        for (Notification notification : failedNotifications) {
            try {
                // If it has custom recipients, use those
                if (notification.getCustomRecipients() != null && !notification.getCustomRecipients().isEmpty()) {
                    String[] recipients = notification.getCustomRecipients().split(",");
                    for (String recipient : recipients) {
                        sendEmail(recipient.trim(), notification.getSubject(), notification.getMessage());
                    }
                } else {
                    // Send to a default recipient (e.g., emailFrom as fallback)
                    sendEmail(emailFrom, notification.getSubject(), notification.getMessage());
                }

                // Mark as sent
                notification.setSent(true);
                notification.setUpdatedAt(LocalDateTime.now());
                notificationRepository.save(notification);

                resendCount++;
                logger.info("Successfully resent notification: {}", notification.getSubject());
            } catch (Exception e) {
                logger.error("Failed to resend notification {}: {}", notification.getId(), e.getMessage(), e);
            }
        }

        logger.info("Resent {} out of {} failed notifications", resendCount, failedNotifications.size());
        return resendCount;
    }

    /**
     * Checks if email service is available
     *
     * @return true if email service is available, false otherwise
     */
    public boolean isEmailServiceAvailable() {
        return emailSenderAvailable && emailServiceEnabled;
    }
}