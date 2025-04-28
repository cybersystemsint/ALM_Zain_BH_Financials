/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

/**
 *
 * @author jgithu
 */

import com.telkom.co.ke.almoptics.entities.tb_Asset_Allocation;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface AssetAllocationService {
  List<tb_Asset_Allocation> findAll();
  
  tb_Asset_Allocation save(tb_Asset_Allocation paramtb_Asset_Allocation);
  
  List<tb_Asset_Allocation> findBystatus(String paramString);
  
  tb_Asset_Allocation findByLocationId(String paramString);
  
  tb_Asset_Allocation findByPersonId(String paramString);
  
  tb_Asset_Allocation findByAssetCode(String paramString);
}
