/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;


import com.telkom.co.ke.almoptics.entities.tb_Asset_Journal;
import com.telkom.co.ke.almoptics.repository.AssetJournalRepository;
import com.telkom.co.ke.almoptics.services.AssetJournalService;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
@Transactional
public class AssetJournalImplementor implements AssetJournalService {
  @Autowired
  private AssetJournalRepository assetJournalRepository;
  
  public List<tb_Asset_Journal> findAll() {
    return this.assetJournalRepository.findAll();
  }
  
  public tb_Asset_Journal save(tb_Asset_Journal boards) {
    return (tb_Asset_Journal)this.assetJournalRepository.save(boards);
  }
  
  public tb_Asset_Journal findByAssetCode(String paramString) {
    return this.assetJournalRepository.findByAssetCode(paramString);
  }
}
