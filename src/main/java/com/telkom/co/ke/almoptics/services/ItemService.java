/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tb_Item;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface ItemService {

    List<tb_Item> findAll();

    tb_Item findByItemCode(String paramString);
}
