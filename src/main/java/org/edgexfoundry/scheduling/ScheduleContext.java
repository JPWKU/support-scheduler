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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.edgexfoundry.domain.meta.Schedule;
import org.edgexfoundry.domain.meta.ScheduleEvent;

public class ScheduleContext {

  private static final org.edgexfoundry.support.logging.client.EdgeXLogger logger =
      org.edgexfoundry.support.logging.client.EdgeXLoggerFactory
          .getEdgeXLogger(ScheduleContext.class);

  private static final String ERR_SCH_EVENT = "schedule event ";

  // Using for various things; run once, name, etc.
  Schedule schedule;

  // Duration for hhmmssSSS
  private Duration duration;

  // Period for YYMMDD
  private Period period;

  // start time
  private ZonedDateTime startTime;

  // end time
  private ZonedDateTime endTime;

  // next time for this schedule to execute
  private ZonedDateTime nextTime;

  // track execution iterations
  private long iterations;

  // maximum times to execute, 0 is infinite
  private long maxIterations;

  // events to execute - event id to event
  private LinkedHashMap<String, ScheduleEvent> scheduleEvents;

  public ScheduleContext(Schedule schedule) {
    scheduleEvents = new LinkedHashMap<>();
    reset(schedule);
  }

  // Two schedules are equal if the have the same schedule id
  // used to find a schedule (and update/delete)
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ScheduleContext) {
      ScheduleContext sc = (ScheduleContext) obj;
      return this.schedule.getId() == sc.getId();
    }
    return false;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    final int primeMult = 53;

    return new HashCodeBuilder(prime, primeMult).append(startTime).append(endTime).toHashCode();
  }

  public void reset(Schedule schedule) {
    if ((this.schedule != null) && (this.schedule.getName() != schedule.getName())) {
      scheduleEvents.clear();
    }
    this.schedule = schedule;
    // update this if/when iterations are added to the schedule
    this.maxIterations = (schedule.getRunOnce()) ? 1 : 0;
    this.iterations = 0;

    String start = schedule.getStart();
    String end = schedule.getEnd();
    // if start is empty, then use now (need to think about ever-spawning tasks)
    if (start == null || start.isEmpty()) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Schedule.DATETIME_FORMATS[0])
          .withZone(ZoneId.systemDefault());
      start = formatter.format(Instant.now());
    }
    this.startTime = parseTime(start);

    // if end is empty, then use max
    if (end == null || end.isEmpty()) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Schedule.DATETIME_FORMATS[0])
          .withZone(ZoneId.systemDefault());
      end = formatter.format(ZonedDateTime.of(LocalDateTime.MAX, ZoneId.systemDefault()));
    }
    this.endTime = parseTime(end);

    // get the period and duration from the frequency string
    parsePeriodAndDuration(schedule.getFrequency());

    // setup the next time the schedule will run
    this.nextTime = initNextTime(startTime, ZonedDateTime.now(), period, duration);

    // clear any schedule events as required

    logger.debug("reset() " + this.toString());
  }

  private ZonedDateTime parseTime(String time) {
    DateTimeFormatter dtf =
        DateTimeFormatter.ofPattern(Schedule.DATETIME_FORMATS[0]).withZone(ZoneId.systemDefault());
    ZonedDateTime zdt = null;
    try {
      zdt = ZonedDateTime.parse(time, dtf);
    } catch (DateTimeParseException e) {
      logger.error("parseTime() failed to parse '" + time + "'");
    }
    return zdt;
  }

  private ZonedDateTime initNextTime(ZonedDateTime start, ZonedDateTime now, Period period, Duration duration) {
    // if the start time is in the future next will just be start
    ZonedDateTime next = start;
    // if the start time is in the past, increment until we find the next time to execute
    // cannot call isComplete() here as it depends on nextTime
    if (startTime.compareTo(now) <= 0 && !schedule.getRunOnce()) {
      // TODO: optimize the below. consider a one-second timer case...
      // For example if only a single unit, e.g. only minutes, then can optimize relative to start
      // if there are more than one unit, then the loop may be best as it will be difficult
      while (next.compareTo(now) <= 0) {
        next = next.plus(period);
        next = next.plus(duration);
      }
    }
    return next;
  }

  private void parsePeriodAndDuration(String frequency) {

    int periodStart = frequency.indexOf('P');
    int timeStart = frequency.indexOf('T');
    String freq = frequency;

    this.period = Period.ZERO;
    this.duration = Duration.ZERO;

    // Parse out the period (date)
    try {
      // If there is a duration 'T', remove it
      if (timeStart != -1)
        freq = frequency.substring(periodStart, timeStart);
      this.period = Period.parse(freq);
    } catch (IndexOutOfBoundsException | DateTimeParseException e) {
      logger.error("parsePeriodAndDuration() failed to parse period from '" + freq + "'");
    }

    // Parse out the duration (time)
    try {
      // Make sure there is both a 'P' and 'T'
      if (periodStart != -1 && timeStart != -1) {
        freq = frequency.substring(timeStart, frequency.length());
        this.duration = Duration.parse("P" + freq);
      }
    } catch (IndexOutOfBoundsException | DateTimeParseException e) {
      logger.error("parsePeriodAndDuration() failed to parse duration from 'P" + freq + "'");
    }
  }

  public boolean isComplete() {
    return isComplete(ZonedDateTime.now());
  }

  private boolean isComplete(ZonedDateTime now) {
    // - start time is in the past and it's a run-once
    // - next time is greater than end time
    // - maxIterations is defined and iterations >= maxIterations
    boolean complete = ((startTime.compareTo(now) < 0 && schedule.getRunOnce())
        || (nextTime.compareTo(endTime) > 0)
        || ((maxIterations != 0) && (iterations >= maxIterations)));
    return (complete);
  }

  public String getName() {
    return schedule.getName();
  }

  public String getId() {
    return schedule.getId();
  }

  public String getInfo() {
    return schedule.getId() + " '" + schedule.getName() + "'";
  }

  public ZonedDateTime getStartTime() {
    return startTime;
  }

  public ZonedDateTime getEndTime() {
    return endTime;
  }

  public ZonedDateTime getNextTime() {
    return nextTime;
  }

  public long getMaxIterations() {
    return maxIterations;
  }

  public void updateNextTime() {
    if (!isComplete()) {
      nextTime = nextTime.plus(period);
      nextTime = nextTime.plus(duration);
    }
  }

  public long getIterations() {
    return iterations;
  }

  public Map<String, ScheduleEvent> getScheduleEvents() {
    return scheduleEvents;
  }

  public void updateIterations() {
    if (!isComplete())
      iterations = iterations + 1;
  }

  @Override
  public String toString() {
    return "ScheduleContext [id =" + getId() + " name=" + getName() + ", start="
        + startTime.toString() + ", end=" + endTime.toString() + ", next=" + nextTime.toString()
        + ", complete=" + isComplete() + "]";
  }

  public boolean addScheduleEvent(ScheduleEvent scheduleEvent) {
    logger.info("adding schedule event " + scheduleEvent.getId() + " '" + scheduleEvent.getName()
        + "' to schedule " + getInfo());
    if (scheduleEvents.containsKey(scheduleEvent.getId())) {
      logger.error(
          ERR_SCH_EVENT + scheduleEvent.getId() + " " + scheduleEvent.getName() + " exists.");
      return false;
    }
    scheduleEvents.put(scheduleEvent.getId(), scheduleEvent);
    logger.info("added schedule event " + scheduleEvent.getId() + " '" + scheduleEvent.getName()
        + "' to schedule " + getInfo());
    return true;
  }

  public boolean updateScheduleEvent(ScheduleEvent scheduleEvent) {
    logger.info("updating schedule event " + scheduleEvent.getId() + " '" + scheduleEvent.getName()
        + "' of schedule " + getInfo());
    if (!scheduleEvents.containsKey(scheduleEvent.getId())) {
      logger.error(
          ERR_SCH_EVENT + scheduleEvent.getId() + " '" + scheduleEvent.getName() + " not found");
      return false;
    }
    scheduleEvents.put(scheduleEvent.getId(), scheduleEvent);
    logger.info("updated schedule event " + scheduleEvent.getId() + " '" + scheduleEvent.getName()
        + "' of schedule " + getInfo());
    return true;
  }

  public boolean removeScheduleEventById(String id) {
    logger.info("removing schedule event " + id);
    if (!scheduleEvents.containsKey(id)) {
      logger.error(ERR_SCH_EVENT + id + " not found");
      return false;
    }
    scheduleEvents.remove(id);
    logger.info("removed schedule event " + id);
    return true;
  }

}
