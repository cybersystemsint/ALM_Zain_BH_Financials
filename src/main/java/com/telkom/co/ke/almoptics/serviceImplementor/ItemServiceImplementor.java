/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;


import com.telkom.co.ke.almoptics.entities.tb_Item;
import com.telkom.co.ke.almoptics.repository.ItemRepository;
import com.telkom.co.ke.almoptics.services.ItemService;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class ItemServiceImplementor implements ItemService {
  @Autowired
  private ItemRepository ItemRepository;
  
  public List<tb_Item> findAll() {
    return this.ItemRepository.findAll();
  }
  
  public tb_Item findByItemCode(String paramString) {
    return this.ItemRepository.findByItemCode(paramString);
  }
}
