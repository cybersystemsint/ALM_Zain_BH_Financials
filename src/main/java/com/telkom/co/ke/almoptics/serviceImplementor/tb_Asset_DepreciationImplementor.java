/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;


import com.telkom.co.ke.almoptics.entities.tb_Asset_Depreciation;
import com.telkom.co.ke.almoptics.repository.tb_Asset_DepreciationRepository;
import com.telkom.co.ke.almoptics.services.tb_Asset_DepreciationService;
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
public class tb_Asset_DepreciationImplementor implements tb_Asset_DepreciationService {

    @Autowired
    private tb_Asset_DepreciationRepository tb_Asset_DepreciationRepository;

    public List<tb_Asset_Depreciation> findAll() {
        return this.tb_Asset_DepreciationRepository.findAll();
    }

    public tb_Asset_Depreciation save(tb_Asset_Depreciation boards) {
        return (tb_Asset_Depreciation) this.tb_Asset_DepreciationRepository.save(boards);
    }

    public tb_Asset_Depreciation findByAssetCode(String paramString) {
        return this.tb_Asset_DepreciationRepository.findByAssetCode(paramString);
    }
}
