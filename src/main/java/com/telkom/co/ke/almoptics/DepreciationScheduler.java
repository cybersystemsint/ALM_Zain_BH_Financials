package com.telkom.co.ke.almoptics;

import com.telkom.co.ke.almoptics.services.DepreciationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DepreciationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DepreciationScheduler.class);

    @Autowired
    private DepreciationService depreciationService;

    /**
     * Scheduled task to calculate depreciation for all active financial reports.
     * Runs on the last day of each month at 23:59 PM in Africa/Nairobi timezone.
     */
    @Scheduled(cron = "${depreciation.scheduler.cron:0 59 23 L * ?}", zone = "Africa/Nairobi")
    public void calculateMonthlyDepreciation() {
        logger.info("Scheduled depreciation calculation triggered at {}", java.time.LocalDateTime.now());
        depreciationService.calculateMonthlyDepreciation();
    }
}