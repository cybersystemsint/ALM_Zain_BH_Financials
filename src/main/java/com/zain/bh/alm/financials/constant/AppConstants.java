package com.zain.bh.alm.financials.constant;

public final class AppConstants {

	private AppConstants() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static final String RESPONSE_CODE_SUCCESS = "0";
	public static final String RESPONSE_CODE_FAILURE = "1";
	public static final String RESPONSE_CODE_SUCCESS_ALT = "00";
	public static final String RESPONSE_CODE_FAILURE_ALT = "01";

	public static final String STATUS_NEW = "New";
	public static final String STATUS_EXISTING = "Existing";
	public static final String STATUS_CREATED = "created";
	public static final String STATUS_UPDATED = "updated";
	public static final String STATUS_DECOMMISSIONED = "DECOMMISSIONED";

	public static final String DATE_FORMAT_YYYY_MM_DD = "yyyy-MM-dd";
	public static final String DATETIME_FORMAT_PATTERN = "dd/MM/yyyy HH:mm:ss";

	public static final String DUMPS_URL = "http://10.22.28.93:8080/ALM_Inventory_Management/dump/postDumpDetails";
	public static final String WAREHOUSE_UPDATE_URL = "http://10.22.25.92:8080/ALMWarehousing/updateInventoryStatus";
	public static final int DEFAULT_PORT = 8080;
	public static final String WAR_NAME = "alm_bh_financial_application";
}
