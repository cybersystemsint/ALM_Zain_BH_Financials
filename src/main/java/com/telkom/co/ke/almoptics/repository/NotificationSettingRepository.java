package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.models.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByEnabled(boolean enabled);

    List<NotificationSetting> findByDepartment(String department);
}