/*
 * Copyright (C) 2019 Axel Müller <axel.mueller@avanux.de>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package de.avanux.smartapplianceenabler.integration;

import de.avanux.smartapplianceenabler.TestBase;
import de.avanux.smartapplianceenabler.appliance.Appliance;
import de.avanux.smartapplianceenabler.appliance.ApplianceManager;
import de.avanux.smartapplianceenabler.appliance.RunningTimeMonitor;
import de.avanux.smartapplianceenabler.control.Control;
import de.avanux.smartapplianceenabler.control.MockSwitch;
import de.avanux.smartapplianceenabler.control.StartingCurrentSwitch;
import de.avanux.smartapplianceenabler.control.StartingCurrentSwitchDefaults;
import de.avanux.smartapplianceenabler.meter.Meter;
import de.avanux.smartapplianceenabler.schedule.TimeframeIntervalHandler;
import de.avanux.smartapplianceenabler.schedule.TimeframeIntervalState;
import de.avanux.smartapplianceenabler.semp.webservice.*;
import de.avanux.smartapplianceenabler.appliance.ApplianceBuilder;
import de.avanux.smartapplianceenabler.util.DateTimeProvider;
import de.avanux.smartapplianceenabler.webservice.ApplianceStatus;
import de.avanux.smartapplianceenabler.webservice.SaeController;
import org.joda.time.Interval;
import org.joda.time.LocalDateTime;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest extends TestBase {

    private Logger logger = LoggerFactory.getLogger(IntegrationTest.class);
    private SaeController saeController = new SaeController();
    private SempController sempController = new SempController();
    String applianceId = "F-001";

    public IntegrationTest() {
        ApplianceManager.getInstanceWithoutTimer();
    }

    @Test
    public void testSwitchOnAndOff() {
        int maxRuntime = 7200;
        LocalDateTime timeInitial = toToday(10, 0, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withMockSwitch(false)
                .withSchedule(10, 0, 18, 0, null, maxRuntime)
                .withSempBuilderOperation(sempBuilder -> sempBuilder.withMaxPowerConsumption(applianceId, 2000))
                .build(true);
        Control control = appliance.getControl();
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        timeframeIntervalHandler.fillQueue(timeInitial);

        log("Check initial values");
        tick(appliance, timeInitial);
        assertFalse(control.isOn());
        assertEquals(2, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(0));
        assertTimeframeIntervalRuntime(toIntervalTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(1));
        assertPlanningRequest(timeInitial,
                new Timeframe(applianceId,0,
                        toSecondsFromNow(timeInitial, 0, 18, 0, 0),
                        7199, 7200),
                new Timeframe(applianceId,
                        toSecondsFromNow(timeInitial, 1, 10, 0, 0),
                        toSecondsFromNow(timeInitial, 1, 18, 0, 0),
                        7199, 7200)
        );

        logger.debug("########## First switching cycle");

        LocalDateTime timeSwitchOn = toToday(11, 0, 0);
        log("Switch on");
        sempController.em2Device(timeSwitchOn, createEM2Device(applianceId,true));
        ApplianceStatus applianceStatusAfterSwitchOn = getApplianceStatus(timeSwitchOn);
        assertTrue(applianceStatusAfterSwitchOn.isOn());
        assertPlanningRequest(timeSwitchOn,
                new Timeframe(applianceId,0,
                        toSecondsFromNow(timeSwitchOn, 0, 18, 0, 0),
                        7199, 7200),
                new Timeframe(applianceId,
                        toSecondsFromNow(timeSwitchOn, 1, 10, 0, 0),
                        toSecondsFromNow(timeSwitchOn, 1, 18, 0, 0),
                        7199, 7200)
        );

        log("Switch off");
        LocalDateTime timeSwitchOff = toToday(12, 0, 0);
        tick(appliance, timeSwitchOff);
        sempController.em2Device(timeSwitchOff, createEM2Device(applianceId,false));
        assertPlanningRequest(timeSwitchOff,
                new Timeframe(applianceId,0,
                        toSecondsFromNow(timeSwitchOff, 0, 18, 0, 0),
                        3599, 3600),
                new Timeframe(applianceId,
                        toSecondsFromNow(timeSwitchOff, 1, 10, 0, 0),
                        toSecondsFromNow(timeSwitchOff, 1, 18, 0, 0),
                        7199, 7200)
        );
        assertFalse(getApplianceStatus(timeSwitchOff).isOn());

        logger.debug("########## Second switching cycle");

        log("Switch on");
        timeSwitchOn = toToday(16, 0, 0);
        sempController.em2Device(timeSwitchOn, createEM2Device(applianceId,true));

        log("Check values after switch on");
        tick(appliance, timeSwitchOn);
        assertTrue(control.isOn());
        assertEquals(2, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(0));
        assertTimeframeIntervalRuntime(toIntervalTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(1));
        assertTrue(getApplianceStatus(timeSwitchOn).isOn());

        log("Switch off");
        timeSwitchOff = toToday(17, 0, 0);
        tick(appliance, timeSwitchOff);
        sempController.em2Device(timeSwitchOff, createEM2Device(applianceId,false));
        assertPlanningRequest(timeSwitchOff,
                new Timeframe(applianceId,
                        toSecondsFromNow(timeSwitchOff, 1, 10, 0, 0),
                        toSecondsFromNow(timeSwitchOff, 1, 18, 0, 0),
                        7199, 7200)
        );
        assertFalse(getApplianceStatus(timeSwitchOff).isOn());
    }

    @Test
    public void testClickGoLight() {
        int maxRuntimeSet = 1800;
        int maxRuntimeSchedule = 3600;
        LocalDateTime timeInitial = toToday(11, 0, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withMockSwitch(false)
                .withSchedule(10, 0, 18, 0, null, maxRuntimeSchedule)
                .withSempBuilderOperation(sempBuilder -> sempBuilder.withMaxPowerConsumption(applianceId, 2000))
                .build(true);
        Control control = appliance.getControl();
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        timeframeIntervalHandler.fillQueue(timeInitial);

        log("Check initial values");
        assertFalse(control.isOn());
        assertEquals(3, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntimeSet, true, timeframeIntervalHandler.getQueue().get(0));
        assertTimeframeIntervalRuntime(toIntervalTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntimeSet, true, timeframeIntervalHandler.getQueue().get(1));
        assertTimeframeIntervalRuntime(toIntervalDayAfterTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntimeSet, true, timeframeIntervalHandler.getQueue().get(2));

        log("Click go light");
        assertEquals(3600, saeController.suggestRuntime(applianceId).intValue());
        saeController.setRuntimeDemand(timeInitial, applianceId, maxRuntimeSet);
        sempController.em2Device(timeInitial, createEM2Device(applianceId,true));
        assertEquals(3, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntimeSet, true, timeframeIntervalHandler.getQueue().get(0));
        assertTimeframeIntervalRuntime(toIntervalTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntimeSet, true, timeframeIntervalHandler.getQueue().get(1));
        assertTimeframeIntervalRuntime(toIntervalDayAfterTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntimeSet, true, timeframeIntervalHandler.getQueue().get(2));
        assertPlanningRequest(timeInitial,
                new Timeframe(applianceId,
                        0,
                        toSecondsFromNow(timeInitial, 0, 18, 0, 0),
                        1799, 1800),
                new Timeframe(applianceId,
                        toSecondsFromNow(timeInitial, 1, 10, 0, 0),
                        toSecondsFromNow(timeInitial, 1, 18, 0, 0),
                        3599, maxRuntimeSchedule),
                new Timeframe(applianceId,
                        toSecondsFromNow(timeInitial, 2, 10, 0, 0),
                        toSecondsFromNow(timeInitial, 2, 18, 0, 0),
                        3599, maxRuntimeSchedule)
        );
        assertTrue(getApplianceStatus(timeInitial).isOn());

        LocalDateTime timeAfterExpiration = toToday(11, 30, 1);
        log("After go light timeframe expired - tick 1");
        // let the appliance switch off but timeframe interval stays in queue since control is still switiched on
        tick(appliance, timeAfterExpiration);
        log("After go light timeframe expired - tick 2");
        // timeframe interval gets removed from queue since control is switiched off
        tick(appliance, timeAfterExpiration);
        assertEquals(2, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntimeSchedule, true, timeframeIntervalHandler.getQueue().get(0));
        assertTimeframeIntervalRuntime(toIntervalDayAfterTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntimeSchedule, true, timeframeIntervalHandler.getQueue().get(1));
        assertPlanningRequest(timeAfterExpiration,
                new Timeframe(applianceId,
                        toSecondsFromNow(timeAfterExpiration, 1, 10, 0, 0),
                        toSecondsFromNow(timeAfterExpiration, 1, 18, 0, 0),
                        3599, maxRuntimeSchedule),
                new Timeframe(applianceId,
                        toSecondsFromNow(timeAfterExpiration, 2, 10, 0, 0),
                        toSecondsFromNow(timeAfterExpiration, 2, 18, 0, 0),
                        3599, maxRuntimeSchedule)
        );
        assertFalse(getApplianceStatus(timeAfterExpiration).isOn());
    }

    @Test
    public void testSwitchOnAndOff_startingCurrentDetectedDuringTimeframeInterval() {
        int maxRuntime = 3600;
        LocalDateTime timeInitial = toToday(11, 0, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withMockSwitch(true)
                .withMockMeter()
                .withSchedule(10, 0, 18, 0, null, maxRuntime)
                .build(true);
        StartingCurrentSwitch control = (StartingCurrentSwitch) appliance.getControl();
        Meter meter = appliance.getMeter();
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        timeframeIntervalHandler.fillQueue(timeInitial);

        log("Check initial values");
        assertFalse(control.isOn());
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, false, timeframeIntervalHandler.getQueue().get(0));

        log("Detect starting current");
        LocalDateTime timeStartingCurrent = toToday(11, 30, 0);
        Mockito.when(meter.getAveragePower()).thenReturn(StartingCurrentSwitchDefaults.getPowerThreshold() + 1);
        control.detectStartingCurrent(timeStartingCurrent, meter);
        control.detectStartingCurrent(timeStartingCurrent, meter);
        tick(appliance, timeStartingCurrent);
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(1, sempController.createDevice2EM(timeInitial).getPlanningRequest().size());
        assertPlanningRequest(timeStartingCurrent,
                new Timeframe(applianceId,
                        0,
                        toSecondsFromNow(timeStartingCurrent, 0, 18, 0, 0),
                        maxRuntime - 1, maxRuntime)
        );
        assertFalse(getApplianceStatus(timeStartingCurrent).isOn());

        log("Switch on");
        LocalDateTime timeSwitchOn = toToday(12, 0, 0);
        sempController.em2Device(timeSwitchOn, createEM2Device(applianceId,true));
        assertTrue(getApplianceStatus(timeSwitchOn).isOn());

        log("Switch off");
        LocalDateTime timeSwitchOff = toToday(13, 0, 0);
        tick(appliance, timeSwitchOff);
        tick(appliance, timeSwitchOff);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, false, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(0, sempController.createDevice2EM(timeInitial).getPlanningRequest().size());
        assertFalse(getApplianceStatus(timeStartingCurrent).isOn());

        log("Detect starting current");
        timeStartingCurrent = toToday(14, 30, 0);
        Mockito.when(meter.getAveragePower()).thenReturn(StartingCurrentSwitchDefaults.getPowerThreshold() + 1);
        control.detectStartingCurrent(timeStartingCurrent, meter);
        control.detectStartingCurrent(timeStartingCurrent, meter);
        tick(appliance, timeStartingCurrent);
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(1, sempController.createDevice2EM(timeInitial).getPlanningRequest().size());
        assertPlanningRequest(timeStartingCurrent,
                new Timeframe(applianceId,
                        0,
                        toSecondsFromNow(timeStartingCurrent, 0, 18, 0, 0),
                        maxRuntime - 1, maxRuntime)
        );
        assertFalse(getApplianceStatus(timeStartingCurrent).isOn());

        log("Switch on");
        timeSwitchOn = toToday(15, 0, 0);
        sempController.em2Device(timeSwitchOn, createEM2Device(applianceId,true));
        assertTrue(getApplianceStatus(timeSwitchOn).isOn());

        log("Switch off");
        timeSwitchOff = toToday(16, 0, 0);
        tick(appliance, timeSwitchOff);
        tick(appliance, timeSwitchOff);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, false, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(0, sempController.createDevice2EM(timeInitial).getPlanningRequest().size());
        assertFalse(getApplianceStatus(timeStartingCurrent).isOn());

        log("No timeframe should exist after timeframe end");
        LocalDateTime timeAfterTimeframeEnd = toToday(18, 1, 0);
        tick(appliance, timeAfterTimeframeEnd);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntime, false, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(0, sempController.createDevice2EM(timeAfterTimeframeEnd).getPlanningRequest().size());
    }

    @Test
    public void testSwitchOn_timeframeIntervalJustSufficient() {
        int maxRuntime = 3600;
        LocalDateTime timeInitial = toToday(11, 0, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withMockSwitch(true)
                .withMockMeter()
                .withSchedule(10, 0, 18, 0, null, maxRuntime)
                .build(true);
        StartingCurrentSwitch control = (StartingCurrentSwitch) appliance.getControl();
        Meter meter = appliance.getMeter();
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        timeframeIntervalHandler.fillQueue(timeInitial);

        log("Check initial values");
        assertFalse(control.isOn());
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, false, timeframeIntervalHandler.getQueue().get(0));

        log("Detect starting current");
        LocalDateTime timeStartingCurrent = toToday(16, 30, 0);
        Mockito.when(meter.getAveragePower()).thenReturn(StartingCurrentSwitchDefaults.getPowerThreshold() + 1);
        control.detectStartingCurrent(timeStartingCurrent, meter);
        control.detectStartingCurrent(timeStartingCurrent, meter);
        tick(appliance, timeStartingCurrent);
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(1, sempController.createDevice2EM(timeInitial).getPlanningRequest().size());
        assertPlanningRequest(timeStartingCurrent,
                new Timeframe(applianceId,
                        0,
                        toSecondsFromNow(timeStartingCurrent, 0, 18, 0, 0),
                        maxRuntime - 1, maxRuntime)
        );
        assertFalse(getApplianceStatus(timeStartingCurrent).isOn());

        log("Switch on");
        LocalDateTime timeSwitchOn = toToday(17, 0, 0);
        sempController.em2Device(timeSwitchOn, createEM2Device(applianceId, true));
        assertTrue(getApplianceStatus(timeSwitchOn).isOn());

        log("No timeframe should exist after timeframe end");
        LocalDateTime timeBeforeTimeframeEnd = toToday(17, 59, 0);
        tick(appliance, timeBeforeTimeframeEnd);
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(1, sempController.createDevice2EM(timeBeforeTimeframeEnd).getPlanningRequest().size());
        assertPlanningRequest(timeStartingCurrent,
                new Timeframe(applianceId,
                        0,
                        toSecondsFromNow(timeStartingCurrent, 0, 18, 0, 0),
                        maxRuntime - 1, maxRuntime)
        );

        log("No timeframe should exist after timeframe end");
        LocalDateTime timeAfterTimeframeEnd = toToday(18, 1, 0);
        tick(appliance, timeAfterTimeframeEnd);
        tick(appliance, timeAfterTimeframeEnd);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalTomorrow(10, 0, 0, 18, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntime, false, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(0, sempController.createDevice2EM(timeAfterTimeframeEnd).getPlanningRequest().size());
        assertFalse(getApplianceStatus(timeStartingCurrent).isOn());
    }

    @Test
    public void testNoSwitchOn_startingCurrentDetectedDuringTimeframeInterval() {
        int maxRuntime = 3600;
        LocalDateTime timeInitial = toToday(11, 29, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withMockSwitch(true)
                .withMockMeter()
                .withSchedule(10, 0, 13, 0, null, maxRuntime)
                .build(true);
        StartingCurrentSwitch control = (StartingCurrentSwitch) appliance.getControl();
        Meter meter = appliance.getMeter();
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        timeframeIntervalHandler.fillQueue(timeInitial);

        log("Check initial values");
        assertFalse(control.isOn());
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalRuntime(toIntervalToday(10, 0, 0, 13, 0, 0),
                TimeframeIntervalState.ACTIVE, null, maxRuntime, false, timeframeIntervalHandler.getQueue().get(0));

        log("Check initial timeframe interval not sufficient anymore");
        LocalDateTime timeNotSufficient = toToday(12, 1, 0);
        tick(appliance, timeNotSufficient);

        log("Detect starting current");
        LocalDateTime timeStartingCurrent = toToday(12, 30, 0);
        Mockito.when(meter.getAveragePower()).thenReturn(StartingCurrentSwitchDefaults.getPowerThreshold() + 1);
        control.detectStartingCurrent(timeStartingCurrent, meter);
        control.detectStartingCurrent(timeStartingCurrent, meter);
        tick(appliance, timeStartingCurrent);
        assertTimeframeIntervalRuntime(toIntervalTomorrow(10, 0, 0, 13, 0, 0),
                TimeframeIntervalState.QUEUED, null, maxRuntime, true, timeframeIntervalHandler.getQueue().get(0));
    }

    private void log(String message) {
        logger.debug("*********** " + message);
    }

    private ApplianceStatus getApplianceStatus(LocalDateTime now) {
        List<ApplianceStatus> applianceStatuses = saeController.getApplianceStatus(now);
        return applianceStatuses.get(0);
    }

    private EM2Device createEM2Device(String applianceId, boolean on) {
        DeviceControl deviceControl = new DeviceControl();
        deviceControl.setDeviceId(applianceId);
        deviceControl.setOn(on);

        List<DeviceControl> deviceControls = new ArrayList<>();
        deviceControls.add(deviceControl);

        EM2Device em2Device = new EM2Device();
        em2Device.setDeviceControl(deviceControls);
        return em2Device;
    }

    private void assertPlanningRequest(LocalDateTime now, Timeframe... expectedTimeframes) {
        List<PlanningRequest> planningRequests = sempController.createDevice2EM(now).getPlanningRequest();
        assertEquals(1, planningRequests.size());
        List<Timeframe> timeframes = planningRequests.get(0).getTimeframes();
        assertEquals(expectedTimeframes.length, timeframes.size());
        for(int i=0; i<expectedTimeframes.length; i++) {
            assertEquals(expectedTimeframes[i], timeframes.get(i));
        }
    }
}
