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
     * Runs on the 1st day of each month at 00:01 AM in Africa/Nairobi timezone.
     */
    @Scheduled(cron = "${depreciation.scheduler.cron:0 1 0 1 * ?}", zone = "Africa/Nairobi")
    public void calculateMonthlyDepreciation() {
        logger.info("Scheduled depreciation calculation triggered at {}", java.time.LocalDateTime.now());
        depreciationService.calculateMonthlyDepreciation();
    }
}
