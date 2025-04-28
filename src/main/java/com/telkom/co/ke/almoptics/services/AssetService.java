/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tb_Asset;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface AssetService {

    List<tb_Asset> findAll();

    tb_Asset save(tb_Asset paramtb_Asset);

    tb_Asset findBySerialNumber(String paramString);

    tb_Asset findBySupplierId(String paramString);

    tb_Asset findByPoId(String paramString);

    List<tb_Asset> findByStatus(String paramString);

    tb_Asset findByAssetCode(String paramString);
}
