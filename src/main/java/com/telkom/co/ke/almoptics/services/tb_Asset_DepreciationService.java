/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

/**
 *
 * @author jgithu
 */
import com.telkom.co.ke.almoptics.entities.tb_Asset_Depreciation;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface tb_Asset_DepreciationService {
  List<tb_Asset_Depreciation> findAll();
  
  tb_Asset_Depreciation save(tb_Asset_Depreciation paramtb_Asset_Depreciation);
  
  tb_Asset_Depreciation findByAssetCode(String paramString);
}

