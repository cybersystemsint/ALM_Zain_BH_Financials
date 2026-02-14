package com.zain.bh.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.Item;
import com.zain.bh.alm.financials.repository.ItemRepository;
import com.zain.bh.alm.financials.service.ItemService;

@Service
@Transactional
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;

    public ItemServiceImpl(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public Item findByItemCode(String itemCode) {
        return itemRepository.findByItemCode(itemCode);
    }
}
