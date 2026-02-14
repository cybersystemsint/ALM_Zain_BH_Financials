package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.Item;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    Item findByItemCode(String itemCode);
}

