/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_Asset_Allocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author jgithu
 */


@Repository
public interface AssetAllocationRepository extends JpaRepository<tb_Asset_Allocation, Long> {
  List<tb_Asset_Allocation> findAll();
  
  tb_Asset_Allocation findByLocationId(String paramString);
  
  tb_Asset_Allocation findByAssetCode(String paramString);
  
  tb_Asset_Allocation findByPersonId(String paramString);
  
  List<tb_Asset_Allocation> findBystatus(String paramString);
}
