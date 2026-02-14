package com.zain.bh.alm.financials.service;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.AssetJournal;

@Service
public class AssetJournalBusinessService {

	private final AssetJournalService assetJournalService;

	public AssetJournalBusinessService(AssetJournalService assetJournalService) {
		this.assetJournalService = assetJournalService;
	}

	public void createJournal(String assetCode, String activity, String details, String activityBy, String locationId) {
		AssetJournal journal = new AssetJournal();
		journal.setAssetCode(assetCode);
		journal.setActivity(activity);
		journal.setDetails(details);
		journal.setActivityBy(activityBy);
		journal.setLocationId(locationId);
		journal.setTrackingDate(new Date());
		journal.setRecordDatetime(new Date());
		assetJournalService.save(journal);
	}
}
