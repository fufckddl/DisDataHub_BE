package com.hub.gisdatahub.opendata.collect.scheduler;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hub.gisdatahub.opendata.collect.service.DashboardGisObservationSyncService;
import com.hub.gisdatahub.opendata.collect.service.DashboardGisOpenApiCollectService;
import com.hub.gisdatahub.opendata.collect.service.DataCollectService;

@ExtendWith(MockitoExtension.class)
class DataCollectSchedulerTest {

    private static final Map<String, Object> SUCCESS_RESULT = Map.of(
            "completed", 1L,
            "failed", 0L,
            "skipped", 0L,
            "noData", 0L);

    @Mock
    private DataCollectService dataCollectService;

    @Mock
    private DashboardGisOpenApiCollectService dashboardGisOpenApiCollectService;

    @Mock
    private DashboardGisObservationSyncService dashboardGisObservationSyncService;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void collectDailyDashboardGisDataCollectsNineTargetsInOrderWhenEnabled() {
        DataCollectScheduler scheduler = enabledScheduler();
        stubSuccessfulOpenApiCollection();
        when(dataCollectService.collectDailyResidentPopulation()).thenReturn(11);
        when(dashboardGisObservationSyncService.syncResidentPopulation(isNull(), eq("1")))
                .thenReturn(Map.of("insertedCount", 3));

        scheduler.collectDailyDashboardGisData();

        InOrder inOrder = org.mockito.Mockito.inOrder(
                dashboardGisOpenApiCollectService,
                dataCollectService,
                dashboardGisObservationSyncService);
        verifyOpenApiOrder(inOrder, "STANDARD_LIBRARY");
        verifyOpenApiOrder(inOrder, "STANDARD_URBAN_PARK");
        verifyOpenApiOrder(inOrder, "STANDARD_BUS_STOP");
        verifyOpenApiOrder(inOrder, "MOIS_ADMM_HSMB_HH");
        verifyOpenApiOrder(inOrder, "MOIS_ADMM_AVG_AGE");
        verifyOpenApiOrder(inOrder, "MOIS_ADMM_POP_CHANGE");
        verifyOpenApiOrder(inOrder, "KMA_VILAGE_FCST");
        verifyOpenApiOrder(inOrder, "AIRKOREA_AIR_QUALITY");
        inOrder.verify(dataCollectService).collectDailyResidentPopulation();
        inOrder.verify(dashboardGisObservationSyncService).syncResidentPopulation(isNull(), eq("1"));
    }

    @Test
    void collectDailyDashboardGisDataContinuesAfterSourceFailure() {
        DataCollectScheduler scheduler = enabledScheduler();
        stubSuccessfulOpenApiCollection();
        when(dashboardGisOpenApiCollectService.collect(
                eq("STANDARD_URBAN_PARK"),
                eq(1),
                eq(1000),
                isNull(),
                isNull()))
                .thenThrow(new RuntimeException("boom"));
        when(dataCollectService.collectDailyResidentPopulation()).thenReturn(11);
        when(dashboardGisObservationSyncService.syncResidentPopulation(isNull(), eq("1")))
                .thenReturn(Map.of("insertedCount", 3));

        scheduler.collectDailyDashboardGisData();

        verify(dashboardGisOpenApiCollectService).collect(
                eq("STANDARD_BUS_STOP"),
                eq(1),
                eq(1000),
                isNull(),
                isNull());
        verify(dashboardGisOpenApiCollectService).collect(
                eq("AIRKOREA_AIR_QUALITY"),
                eq(1),
                eq(1000),
                isNull(),
                isNull());
        verify(dataCollectService).collectDailyResidentPopulation();
        verify(dashboardGisObservationSyncService).syncResidentPopulation(isNull(), eq("1"));
    }

    @Test
    void collectDailyDashboardGisDataDoesNothingWhenDisabled() {
        DataCollectScheduler scheduler = new DataCollectScheduler(
                dataCollectService,
                dashboardGisOpenApiCollectService,
                dashboardGisObservationSyncService,
                false);

        scheduler.collectDailyDashboardGisData();

        verifyNoInteractions(dashboardGisOpenApiCollectService);
        verify(dataCollectService, never()).collectDailyResidentPopulation();
        verifyNoInteractions(dashboardGisObservationSyncService);
    }

    @Test
    void collectDailyDashboardGisDataSkipsDuplicateExecution() throws Exception {
        DataCollectScheduler scheduler = enabledScheduler();
        CountDownLatch firstCollectionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCollection = new CountDownLatch(1);
        when(dashboardGisOpenApiCollectService.collect(
                eq("STANDARD_LIBRARY"),
                eq(1),
                eq(1000),
                isNull(),
                isNull()))
                .thenAnswer(invocation -> {
                    firstCollectionStarted.countDown();
                    releaseFirstCollection.await(2, TimeUnit.SECONDS);
                    return SUCCESS_RESULT;
                });
        when(dashboardGisOpenApiCollectService.collect(
                org.mockito.ArgumentMatchers.argThat(sourceCode -> !"STANDARD_LIBRARY".equals(sourceCode)),
                eq(1),
                eq(1000),
                isNull(),
                isNull()))
                .thenReturn(SUCCESS_RESULT);
        when(dataCollectService.collectDailyResidentPopulation()).thenReturn(11);
        when(dashboardGisObservationSyncService.syncResidentPopulation(isNull(), eq("1")))
                .thenReturn(Map.of("insertedCount", 3));

        Future<?> firstRun = executorService.submit(scheduler::collectDailyDashboardGisData);
        firstCollectionStarted.await(2, TimeUnit.SECONDS);

        scheduler.collectDailyDashboardGisData();

        releaseFirstCollection.countDown();
        firstRun.get(2, TimeUnit.SECONDS);
        verify(dashboardGisOpenApiCollectService, times(1)).collect(
                eq("STANDARD_LIBRARY"),
                eq(1),
                eq(1000),
                isNull(),
                isNull());
        verify(dataCollectService, times(1)).collectDailyResidentPopulation();
    }

    private DataCollectScheduler enabledScheduler() {
        return new DataCollectScheduler(
                dataCollectService,
                dashboardGisOpenApiCollectService,
                dashboardGisObservationSyncService,
                true);
    }

    private void stubSuccessfulOpenApiCollection() {
        when(dashboardGisOpenApiCollectService.collect(
                org.mockito.ArgumentMatchers.anyString(),
                eq(1),
                eq(1000),
                isNull(),
                isNull()))
                .thenReturn(SUCCESS_RESULT);
    }

    private void verifyOpenApiOrder(InOrder inOrder, String sourceCode) {
        inOrder.verify(dashboardGisOpenApiCollectService).collect(
                eq(sourceCode),
                eq(1),
                eq(1000),
                isNull(),
                isNull());
    }
}
