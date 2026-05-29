package com.hub.gisdatahub.opendata.collect.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hub.gisdatahub.opendata.collect.service.DataCollectService;

@Component
public class DataCollectScheduler {

    private final DataCollectService dataCollectService;

    public DataCollectScheduler(DataCollectService dataCollectService) {
        this.dataCollectService = dataCollectService;
    }

    @Scheduled(cron = "${MOIS_RESIDENT_POPULATION_COLLECT_CRON:0 0 3 * * *}", zone = "Asia/Seoul")
    public void collectDailyResidentPopulation() {
        dataCollectService.collectDailyResidentPopulation();
    }

    @Scheduled(cron = "${seoul.open-api.sdot-visitor-collect-cron:0 20 3 * * *}", zone = "Asia/Seoul")
    public void collectDailySdotVisitorCount() {
        dataCollectService.collectDailySdotVisitorCount();
    }
}
