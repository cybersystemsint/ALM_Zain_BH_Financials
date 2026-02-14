package com.zain.bh.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.AssetJournal;
import com.zain.bh.alm.financials.repository.AssetJournalRepository;
import com.zain.bh.alm.financials.service.AssetJournalService;

@Service
@Transactional
public class AssetJournalServiceImpl implements AssetJournalService {

    private final AssetJournalRepository assetJournalRepository;

    public AssetJournalServiceImpl(AssetJournalRepository assetJournalRepository) {
        this.assetJournalRepository = assetJournalRepository;
    }

    public List<AssetJournal> findAll() {
        return assetJournalRepository.findAll();
    }

    public AssetJournal save(AssetJournal assetJournal) {
        return assetJournalRepository.save(assetJournal);
    }

    public AssetJournal findByAssetCode(String assetCode) {
        return assetJournalRepository.findByAssetCode(assetCode);
    }
}
