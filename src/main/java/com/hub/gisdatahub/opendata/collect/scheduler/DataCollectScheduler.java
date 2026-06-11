package com.hub.gisdatahub.opendata.collect.scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hub.gisdatahub.opendata.collect.service.DashboardGisObservationSyncService;
import com.hub.gisdatahub.opendata.collect.service.DashboardGisOpenApiCollectService;
import com.hub.gisdatahub.opendata.collect.service.DataCollectService;

@Component
public class DataCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataCollectScheduler.class);
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_NUM_OF_ROWS = 1000;
    private static final String DEFAULT_REG_SE_CD = "1";
    private static final DailyDashboardGisSource RESIDENT_POPULATION_SOURCE = new DailyDashboardGisSource(
            "MOIS_ADMM_SEXD_AGE_PPLTN",
            "MOIS_ADMM_SEXD_AGE_PPLTN_MAIN");
    private static final List<DailyDashboardGisSource> DAILY_DASHBOARD_GIS_SOURCES = List.of(
            new DailyDashboardGisSource("STANDARD_LIBRARY", "STANDARD_LIBRARY_MAIN"),
            new DailyDashboardGisSource("STANDARD_URBAN_PARK", "STANDARD_URBAN_PARK_MAIN"),
            new DailyDashboardGisSource("STANDARD_BUS_STOP", "STANDARD_BUS_STOP_MAIN"),
            new DailyDashboardGisSource("MOIS_ADMM_HSMB_HH", "MOIS_ADMM_HSMB_HH_MAIN"),
            new DailyDashboardGisSource("MOIS_ADMM_AVG_AGE", "MOIS_ADMM_AVG_AGE_MAIN"),
            new DailyDashboardGisSource("MOIS_ADMM_POP_CHANGE", "MOIS_ADMM_POP_CHANGE_MAIN"),
            new DailyDashboardGisSource("KMA_VILAGE_FCST", "KMA_VILAGE_FCST_MAIN"),
            new DailyDashboardGisSource("AIRKOREA_AIR_QUALITY", "AIRKOREA_AIR_QUALITY_MAIN"));

    private final DataCollectService dataCollectService;
    private final DashboardGisOpenApiCollectService dashboardGisOpenApiCollectService;
    private final DashboardGisObservationSyncService dashboardGisObservationSyncService;
    private final boolean dailyDashboardGisCollectEnabled;
    private final AtomicBoolean dailyDashboardGisCollectRunning = new AtomicBoolean(false);

    public DataCollectScheduler(
            DataCollectService dataCollectService,
            DashboardGisOpenApiCollectService dashboardGisOpenApiCollectService,
            DashboardGisObservationSyncService dashboardGisObservationSyncService,
            @Value("${dashboard.gis.daily-collect-enabled:true}") boolean dailyDashboardGisCollectEnabled) {
        this.dataCollectService = dataCollectService;
        this.dashboardGisOpenApiCollectService = dashboardGisOpenApiCollectService;
        this.dashboardGisObservationSyncService = dashboardGisObservationSyncService;
        this.dailyDashboardGisCollectEnabled = dailyDashboardGisCollectEnabled;
    }

    public void collectDailyResidentPopulation() {
        dataCollectService.collectDailyResidentPopulation();
    }

    @Scheduled(cron = "${dashboard.gis.daily-collect-cron:0 0 0 * * *}", zone = "Asia/Seoul")
    public void collectDailyDashboardGisData() {
        if (!dailyDashboardGisCollectEnabled) {
            log.info("daily dashboard GIS collection is disabled");
            return;
        }
        if (!dailyDashboardGisCollectRunning.compareAndSet(false, true)) {
            log.warn("daily dashboard GIS collection is already running; skip duplicate execution");
            return;
        }

        int succeeded = 0;
        int failed = 0;
        int skipped = 0;
        try {
            log.info("daily dashboard GIS collection started");

            for (DailyDashboardGisSource source : DAILY_DASHBOARD_GIS_SOURCES) {
                CollectOutcome outcome = collectDashboardOpenApiSource(source);
                succeeded += outcome.succeeded();
                failed += outcome.failed();
                skipped += outcome.skipped();
            }

            CollectOutcome residentPopulationOutcome = collectResidentPopulationDashboardObservations();
            succeeded += residentPopulationOutcome.succeeded();
            failed += residentPopulationOutcome.failed();
            skipped += residentPopulationOutcome.skipped();

            log.info(
                    "daily dashboard GIS collection finished: succeeded={}, failed={}, skipped={}",
                    succeeded,
                    failed,
                    skipped);
        } finally {
            dailyDashboardGisCollectRunning.set(false);
        }
    }

    @Scheduled(cron = "${seoul.open-api.sdot-visitor-collect-cron:0 20 3 * * *}", zone = "Asia/Seoul")
    public void collectDailySdotVisitorCount() {
        dataCollectService.collectDailySdotVisitorCount();
    }

    private CollectOutcome collectDashboardOpenApiSource(DailyDashboardGisSource source) {
        try {
            Map<String, Object> result = dashboardGisOpenApiCollectService.collect(
                    source.sourceCode(),
                    DEFAULT_PAGE_NO,
                    DEFAULT_NUM_OF_ROWS,
                    null,
                    null);
            CollectOutcome outcome = outcomeFromResult(result);
            log.info(
                    "daily dashboard GIS source collected: sourceCode={}, datasetCode={}, result={}",
                    source.sourceCode(),
                    source.datasetCode(),
                    result);
            return outcome;
        } catch (Exception exception) {
            log.error(
                    "daily dashboard GIS source collection failed: sourceCode={}, datasetCode={}, message={}",
                    source.sourceCode(),
                    source.datasetCode(),
                    exception.getMessage(),
                    exception);
            return CollectOutcome.failure();
        }
    }

    private CollectOutcome collectResidentPopulationDashboardObservations() {
        try {
            int savedCount = dataCollectService.collectDailyResidentPopulation();
            Map<String, Object> syncResult = dashboardGisObservationSyncService.syncResidentPopulation(
                    null,
                    DEFAULT_REG_SE_CD);
            log.info(
                    "daily resident population dashboard observations collected: sourceCode={}, datasetCode={}, savedCount={}, syncResult={}",
                    RESIDENT_POPULATION_SOURCE.sourceCode(),
                    RESIDENT_POPULATION_SOURCE.datasetCode(),
                    savedCount,
                    syncResult);
            return CollectOutcome.success();
        } catch (Exception exception) {
            log.error(
                    "daily resident population dashboard observation collection failed: sourceCode={}, datasetCode={}, message={}",
                    RESIDENT_POPULATION_SOURCE.sourceCode(),
                    RESIDENT_POPULATION_SOURCE.datasetCode(),
                    exception.getMessage(),
                    exception);
            return CollectOutcome.failure();
        }
    }

    private CollectOutcome outcomeFromResult(Map<String, Object> result) {
        if (result == null) {
            return CollectOutcome.skip();
        }
        int failed = number(result.get("failed"));
        int skipped = number(result.get("skipped"));
        int noData = number(result.get("noData"));
        int completed = number(result.get("completed"));
        int succeeded = completed > 0 ? 1 : 0;
        int skippedTotal = skipped + noData;
        if (failed > 0) {
            return new CollectOutcome(succeeded, 1, skippedTotal);
        }
        if (succeeded > 0) {
            return new CollectOutcome(1, 0, skippedTotal);
        }
        return new CollectOutcome(0, 0, Math.max(1, skippedTotal));
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private record DailyDashboardGisSource(String sourceCode, String datasetCode) {
    }

    private record CollectOutcome(int succeeded, int failed, int skipped) {
        static CollectOutcome success() {
            return new CollectOutcome(1, 0, 0);
        }

        static CollectOutcome failure() {
            return new CollectOutcome(0, 1, 0);
        }

        static CollectOutcome skip() {
            return new CollectOutcome(0, 0, 1);
        }
    }
}
