/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_Asset_Depreciation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author jgithu
 */
@Repository
public interface tb_Asset_DepreciationRepository extends JpaRepository<tb_Asset_Depreciation, Long> {

    List<tb_Asset_Depreciation> findAll();

    tb_Asset_Depreciation findByAssetCode(String paramString);
}
