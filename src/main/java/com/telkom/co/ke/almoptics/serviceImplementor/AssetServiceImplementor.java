/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tb_Asset;
import com.telkom.co.ke.almoptics.repository.AssetRepository;
import com.telkom.co.ke.almoptics.services.AssetService;
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
public class AssetServiceImplementor implements AssetService {

    @Autowired
    private AssetRepository assetsRepository;

    public List<tb_Asset> findAll() {
        return this.assetsRepository.findAll();
    }

    public tb_Asset save(tb_Asset boards) {
        return (tb_Asset) this.assetsRepository.save(boards);
    }

    public tb_Asset findBySerialNumber(String paramString) {
        return this.assetsRepository.findBySerialNumber(paramString);
    }

    public tb_Asset findBySupplierId(String paramString) {
        return this.assetsRepository.findBySupplierId(paramString);
    }

    public tb_Asset findByPoId(String paramString) {
        return this.assetsRepository.findByPoId(paramString);
    }

    public List<tb_Asset> findByStatus(String paramString) {
        return this.assetsRepository.findByStatus(paramString);
    }

    public tb_Asset findByAssetCode(String paramString) {
        return this.assetsRepository.findByAssetCode(paramString);
    }
}
