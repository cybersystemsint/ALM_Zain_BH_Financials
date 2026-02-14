package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.NotificationSetting;

import java.util.List;

@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByEnabled(boolean enabled);

    List<NotificationSetting> findByDepartment(String department);
}
