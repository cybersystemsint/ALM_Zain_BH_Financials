/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

/**
 *
 * @author jgithu
 */

import com.telkom.co.ke.almoptics.entities.tb_Asset_Journal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface AssetJournalService {
  List<tb_Asset_Journal> findAll();
  
  tb_Asset_Journal findByAssetCode(String paramString);
  
  tb_Asset_Journal save(tb_Asset_Journal paramtb_Asset_Journal);
}
