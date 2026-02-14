package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findBySentFalse();

    List<Notification> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Notification> findBySubjectContaining(String subjectPart);
}
