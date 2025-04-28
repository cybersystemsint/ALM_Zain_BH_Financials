/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tb_Asset_Allocation;
import com.telkom.co.ke.almoptics.repository.AssetAllocationRepository;
import com.telkom.co.ke.almoptics.services.AssetAllocationService;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class AssetAllocationServiceImplementor implements AssetAllocationService {
  @Autowired
  private AssetAllocationRepository assetAllocationRepository;
  
  public List<tb_Asset_Allocation> findAll() {
    return this.assetAllocationRepository.findAll();
  }
  
  public tb_Asset_Allocation save(tb_Asset_Allocation boards) {
    return (tb_Asset_Allocation)this.assetAllocationRepository.save(boards);
  }
  
  public List<tb_Asset_Allocation> findBystatus(String paramString) {
    return this.assetAllocationRepository.findBystatus(paramString);
  }
  
  public tb_Asset_Allocation findByLocationId(String paramString) {
    return this.assetAllocationRepository.findByLocationId(paramString);
  }
  
  public tb_Asset_Allocation findByPersonId(String paramString) {
    return this.assetAllocationRepository.findByPersonId(paramString);
  }
  
  public tb_Asset_Allocation findByAssetCode(String paramString) {
    return this.assetAllocationRepository.findByAssetCode(paramString);
  }
}
