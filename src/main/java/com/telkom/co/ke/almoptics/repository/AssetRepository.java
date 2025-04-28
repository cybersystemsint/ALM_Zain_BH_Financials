/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.repository;


import com.telkom.co.ke.almoptics.entities.tb_Asset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 *
 * @author jgithu
 */

@Repository
public interface AssetRepository extends JpaRepository<tb_Asset, Long> {
  List<tb_Asset> findAll();
  
  tb_Asset findBySerialNumber(String paramString);
  
  tb_Asset findBySupplierId(String paramString);
  
  tb_Asset findByPoId(String paramString);
  
  tb_Asset findByAssetCode(String paramString);
  
  List<tb_Asset> findByStatus(String paramString);
}

