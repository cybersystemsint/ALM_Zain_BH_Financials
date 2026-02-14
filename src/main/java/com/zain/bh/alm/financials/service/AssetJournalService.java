package com.zain.bh.alm.financials.service;

import java.util.List;

import com.zain.bh.alm.financials.entity.AssetJournal;

public interface AssetJournalService {

    List<AssetJournal> findAll();

    AssetJournal findByAssetCode(String assetCode);

    AssetJournal save(AssetJournal assetJournal);
}
