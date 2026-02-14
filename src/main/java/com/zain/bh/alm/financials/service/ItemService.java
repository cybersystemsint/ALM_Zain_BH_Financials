package com.zain.bh.alm.financials.service;

import java.util.List;

import com.zain.bh.alm.financials.entity.Item;

public interface ItemService {

    List<Item> findAll();
    Item findByItemCode(String itemCode);
}
