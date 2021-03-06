/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * @microservice: support-scheduler
 * @author: Marc Hammons, Dell
 * @version: 1.0.0
 *******************************************************************************/

package org.edgexfoundry.scheduling;

import org.edgexfoundry.controller.ScheduleClient;
import org.edgexfoundry.controller.ScheduleEventClient;
import org.edgexfoundry.domain.meta.CallbackAlert;
import org.edgexfoundry.domain.meta.Schedule;
import org.edgexfoundry.domain.meta.ScheduleEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SchedulerCallbackHandler {

  private static final org.edgexfoundry.support.logging.client.EdgeXLogger logger =
      org.edgexfoundry.support.logging.client.EdgeXLoggerFactory
          .getEdgeXLogger(SchedulerCallbackHandler.class);

  @Autowired
  private Scheduler scheduler;

  @Autowired
  private ScheduleClient scheduleClient;

  @Autowired
  private ScheduleEventClient scheduleEventClient;

  public boolean handlePut(CallbackAlert alert) {
    switch (alert.getType()) {
      case SCHEDULE:
        try {
          Schedule schedule = scheduleClient.schedule(alert.getId());
          if (schedule != null)
            scheduler.updateScheduleContext(schedule);
        } catch (Exception e) {
          logger.error("failed to put schedule " + alert.getId() + " " + e);
          return false;
        }
        break;
      case SCHEDULEEVENT:
        try {
          ScheduleEvent scheduleEvent = scheduleEventClient.scheduleEvent(alert.getId());
          if (scheduleEvent != null)
            scheduler.updateScheduleEventInScheduleContext(scheduleEvent);
        } catch (Exception e) {
          logger.error("failed to put schedule event " + alert.getId() + " " + e);
          return false;
        }
        break;
      default:
        break;
    }
    return true;
  }

  public boolean handlePost(CallbackAlert alert) {
    switch (alert.getType()) {
      case SCHEDULE:
        try {
          Schedule schedule = scheduleClient.schedule(alert.getId());
          if (schedule != null)
            scheduler.createScheduleContext(schedule);
        } catch (Exception e) {
          logger.error("failed to post schedule " + alert.getId() + " " + e);
          return false;
        }
        break;
      case SCHEDULEEVENT:
        try {
          ScheduleEvent scheduleEvent = scheduleEventClient.scheduleEvent(alert.getId());
          if (scheduleEvent != null)
            scheduler.addScheduleEventToScheduleContext(scheduleEvent);
        } catch (Exception e) {
          logger.error("failed to post schedule event " + alert.getId() + " " + e);
          return false;
        }
        break;
      default:
        break;
    }
    return true;
  }

  public boolean handleDelete(CallbackAlert alert) {
    switch (alert.getType()) {
      case SCHEDULE:
        try {
          scheduler.removeScheduleById(alert.getId());
        } catch (Exception e) {
          logger.error("failed to delete schedule " + alert.getId() + " " + e);
          return false;
        }
        break;
      case SCHEDULEEVENT:
        try {
          scheduler.removeScheduleEventById(alert.getId());
        } catch (Exception e) {
          logger.error("failed to delete schedule " + alert.getId() + " " + e);
          return false;
        }
        break;
      default:
        break;
    }
    return true;
  }

}
