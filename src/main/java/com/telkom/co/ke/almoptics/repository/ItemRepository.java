/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_Item;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author jgithu
 */

@Repository
public interface ItemRepository extends JpaRepository<tb_Item, Long> {
  List<tb_Item> findAll();
  
  tb_Item findByItemCode(String paramString);
}

