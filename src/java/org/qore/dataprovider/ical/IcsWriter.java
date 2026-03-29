/*  IcsWriter.java Copyright 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

package org.qore.dataprovider.ical;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Repeat;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Trigger;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.qore.jni.Hash;

/**
 * Helper class for writing ICS (iCalendar) files from Qore.
 * Creates a VCALENDAR with VEVENT components.
 */
public class IcsWriter implements Closeable {
    /** iCalendar UTC date-time format: yyyyMMdd'T'HHmmss'Z' */
    private static final DateTimeFormatter ICS_UTC_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private Calendar calendar;
    private String contentType = "events";
    private int eventCount = 0;
    private int todoCount = 0;

    /**
     * Creates a new IcsWriter with a default VCALENDAR wrapper.
     */
    public IcsWriter() {
        calendar = new Calendar();
        calendar.add(new ProdId("-//Qore Technologies//IcsDataProvider//EN"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
    }

    /**
     * Creates a new IcsWriter with a content type selection.
     *
     * @param contentType the type of component to write: "events" or "todos"
     */
    public IcsWriter(String contentType) {
        this();
        this.contentType = contentType != null ? contentType : "events";
    }

    /**
     * Writes a VEVENT from a Hash record.
     *
     * Expected keys: uid, summary, dtstart, dtend, duration, location, description,
     * organizer, attendees, status, rrule
     *
     * @param data the event data as a Hash
     */
    public void writeEvent(Hash data) {
        VEvent event = new VEvent();

        // uid - generate one if not provided
        Object uidVal = data.get("uid");
        if (uidVal != null && !uidVal.toString().isEmpty()) {
            event.add(new Uid(uidVal.toString()));
        } else {
            event.add(new Uid(UUID.randomUUID().toString()));
        }

        // summary
        Object summaryVal = data.get("summary");
        if (summaryVal != null) {
            event.add(new Summary(summaryVal.toString()));
        }

        // dtstart (required)
        Object dtStartVal = data.get("dtstart");
        if (dtStartVal != null) {
            event.add(new DtStart(formatAsIcsUtcDate(dtStartVal)));
        }

        // dtend (optional)
        Object dtEndVal = data.get("dtend");
        if (dtEndVal != null) {
            event.add(new DtEnd(formatAsIcsUtcDate(dtEndVal)));
        }

        // duration (optional)
        Object durationVal = data.get("duration");
        if (durationVal != null && !durationVal.toString().isEmpty()) {
            event.add(new Duration(java.time.Duration.parse(durationVal.toString())));
        }

        // location (optional)
        Object locationVal = data.get("location");
        if (locationVal != null && !locationVal.toString().isEmpty()) {
            event.add(new Location(locationVal.toString()));
        }

        // description (optional)
        Object descriptionVal = data.get("description");
        if (descriptionVal != null && !descriptionVal.toString().isEmpty()) {
            event.add(new Description(descriptionVal.toString()));
        }

        // organizer (optional)
        Object organizerVal = data.get("organizer");
        if (organizerVal != null && !organizerVal.toString().isEmpty()) {
            String orgStr = organizerVal.toString();
            try {
                if (!orgStr.toLowerCase().startsWith("mailto:")) {
                    orgStr = "mailto:" + orgStr;
                }
                event.add(new Organizer(URI.create(orgStr)));
            } catch (Exception e) {
                // Skip invalid organizer URI
            }
        }

        // attendees (optional) - list of strings
        // Qore JNI bridge may pass lists as Object[] arrays or java.util.List
        Object attendeesVal = data.get("attendees");
        Object[] attendeeArr = null;
        if (attendeesVal instanceof Object[]) {
            attendeeArr = (Object[]) attendeesVal;
        } else if (attendeesVal instanceof List) {
            attendeeArr = ((List<?>) attendeesVal).toArray();
        }
        if (attendeeArr != null) {
            for (Object att : attendeeArr) {
                if (att != null) {
                    String attStr = att.toString();
                    try {
                        if (!attStr.toLowerCase().startsWith("mailto:")) {
                            attStr = "mailto:" + attStr;
                        }
                        event.add(new Attendee(URI.create(attStr)));
                    } catch (Exception e) {
                        // Skip invalid attendee URI
                    }
                }
            }
        }

        // status (optional)
        Object statusVal = data.get("status");
        if (statusVal != null && !statusVal.toString().isEmpty()) {
            event.add(new Status(statusVal.toString()));
        }

        // rrule (optional)
        Object rruleVal = data.get("rrule");
        if (rruleVal != null && !rruleVal.toString().isEmpty()) {
            event.add(new RRule<ZonedDateTime>(rruleVal.toString()));
        }

        // categories (optional) - list of strings
        Object categoriesVal = data.get("categories");
        Object[] categoryArr = null;
        if (categoriesVal instanceof Object[]) {
            categoryArr = (Object[]) categoriesVal;
        } else if (categoriesVal instanceof List) {
            categoryArr = ((List<?>) categoriesVal).toArray();
        }
        if (categoryArr != null && categoryArr.length > 0) {
            Categories cats = new Categories();
            for (Object cat : categoryArr) {
                if (cat != null && !cat.toString().isEmpty()) {
                    cats.addCategory(cat.toString());
                }
            }
            event.add(cats);
        }

        // classification (optional) - CLASS property
        Object classVal = data.get("classification");
        if (classVal != null && !classVal.toString().isEmpty()) {
            event.add(new Clazz(classVal.toString()));
        }

        // url (optional)
        Object urlVal = data.get("url");
        if (urlVal != null && !urlVal.toString().isEmpty()) {
            try {
                event.add(new Url(URI.create(urlVal.toString())));
            } catch (Exception e) {
                // Skip invalid URL
            }
        }

        // created (optional)
        Object createdVal = data.get("created");
        if (createdVal != null) {
            event.add(new Created(formatAsIcsUtcDate(createdVal)));
        }

        // last_modified (optional)
        Object lastModVal = data.get("last_modified");
        if (lastModVal != null) {
            event.add(new LastModified(formatAsIcsUtcDate(lastModVal)));
        }

        // transp (optional) - OPAQUE or TRANSPARENT
        Object transpVal = data.get("transp");
        if (transpVal != null && !transpVal.toString().isEmpty()) {
            event.add(new Transp(transpVal.toString()));
        }

        // sequence (optional)
        Object seqVal = data.get("sequence");
        if (seqVal != null) {
            try {
                int seqInt;
                if (seqVal instanceof Number) {
                    seqInt = ((Number) seqVal).intValue();
                } else {
                    seqInt = Integer.parseInt(seqVal.toString().trim());
                }
                event.add(new Sequence(seqInt));
            } catch (NumberFormatException e) {
                // Skip invalid sequence value
            }
        }

        // alarms (optional) - list of alarm hashes
        Object alarmsVal = data.get("alarms");
        Object[] alarmArr = null;
        if (alarmsVal instanceof Object[]) {
            alarmArr = (Object[]) alarmsVal;
        } else if (alarmsVal instanceof List) {
            alarmArr = ((List<?>) alarmsVal).toArray();
        }
        if (alarmArr != null) {
            for (Object alarmObj : alarmArr) {
                if (alarmObj == null) {
                    continue;
                }
                Hash alarmData;
                if (alarmObj instanceof Hash) {
                    alarmData = (Hash) alarmObj;
                } else {
                    continue;
                }

                VAlarm alarm = new VAlarm();

                // action (required)
                Object actionVal = alarmData.get("action");
                if (actionVal != null && !actionVal.toString().isEmpty()) {
                    alarm.add(new Action(actionVal.toString()));
                }

                // trigger (required)
                Object triggerVal = alarmData.get("trigger");
                if (triggerVal != null && !triggerVal.toString().isEmpty()) {
                    alarm.add(new Trigger(new net.fortuna.ical4j.model.ParameterList(),
                        triggerVal.toString()));
                }

                // description (optional)
                Object alarmDescVal = alarmData.get("description");
                if (alarmDescVal != null && !alarmDescVal.toString().isEmpty()) {
                    alarm.add(new Description(alarmDescVal.toString()));
                }

                // duration (optional)
                Object alarmDurVal = alarmData.get("duration");
                if (alarmDurVal != null && !alarmDurVal.toString().isEmpty()) {
                    try {
                        alarm.add(new Duration(java.time.Duration.parse(alarmDurVal.toString())));
                    } catch (Exception e) {
                        // Skip invalid duration
                    }
                }

                // repeat (optional)
                Object repeatVal = alarmData.get("repeat");
                if (repeatVal != null) {
                    try {
                        int repeatInt;
                        if (repeatVal instanceof Number) {
                            repeatInt = ((Number) repeatVal).intValue();
                        } else {
                            repeatInt = Integer.parseInt(repeatVal.toString().trim());
                        }
                        alarm.add(new Repeat(repeatInt));
                    } catch (NumberFormatException e) {
                        // Skip invalid repeat value
                    }
                }

                event.add(alarm);
            }
        }

        calendar.add(event);
        ++eventCount;
    }

    /**
     * Writes a VTODO from a Hash record.
     *
     * Expected keys: uid, summary, dtstart, due, completed, percent_complete, priority,
     * description, organizer, attendees, status, rrule, categories, classification, url,
     * created, last_modified, sequence, alarms
     *
     * @param data the todo data as a Hash
     */
    public void writeTodo(Hash data) {
        VToDo todo = new VToDo();

        // uid - generate one if not provided
        Object uidVal = data.get("uid");
        if (uidVal != null && !uidVal.toString().isEmpty()) {
            todo.add(new Uid(uidVal.toString()));
        } else {
            todo.add(new Uid(UUID.randomUUID().toString()));
        }

        // summary
        Object summaryVal = data.get("summary");
        if (summaryVal != null) {
            todo.add(new Summary(summaryVal.toString()));
        }

        // dtstart (optional for todos)
        Object dtStartVal = data.get("dtstart");
        if (dtStartVal != null) {
            todo.add(new DtStart(formatAsIcsUtcDate(dtStartVal)));
        }

        // due (optional) - todo-specific
        Object dueVal = data.get("due");
        if (dueVal != null) {
            todo.add(new Due(formatAsIcsUtcDate(dueVal)));
        }

        // completed (optional) - todo-specific
        Object completedVal = data.get("completed");
        if (completedVal != null) {
            todo.add(new Completed(formatAsIcsUtcDate(completedVal)));
        }

        // percent_complete (optional) - todo-specific
        Object pctVal = data.get("percent_complete");
        if (pctVal != null) {
            try {
                int pctInt;
                if (pctVal instanceof Number) {
                    pctInt = ((Number) pctVal).intValue();
                } else {
                    pctInt = Integer.parseInt(pctVal.toString().trim());
                }
                todo.add(new PercentComplete(pctInt));
            } catch (NumberFormatException e) {
                // Skip invalid percent_complete value
            }
        }

        // priority (optional) - todo-specific
        Object priorityVal = data.get("priority");
        if (priorityVal != null) {
            try {
                int priInt;
                if (priorityVal instanceof Number) {
                    priInt = ((Number) priorityVal).intValue();
                } else {
                    priInt = Integer.parseInt(priorityVal.toString().trim());
                }
                todo.add(new Priority(priInt));
            } catch (NumberFormatException e) {
                // Skip invalid priority value
            }
        }

        // description (optional)
        Object descriptionVal = data.get("description");
        if (descriptionVal != null && !descriptionVal.toString().isEmpty()) {
            todo.add(new Description(descriptionVal.toString()));
        }

        // organizer (optional)
        Object organizerVal = data.get("organizer");
        if (organizerVal != null && !organizerVal.toString().isEmpty()) {
            String orgStr = organizerVal.toString();
            try {
                if (!orgStr.toLowerCase().startsWith("mailto:")) {
                    orgStr = "mailto:" + orgStr;
                }
                todo.add(new Organizer(URI.create(orgStr)));
            } catch (Exception e) {
                // Skip invalid organizer URI
            }
        }

        // attendees (optional) - list of strings
        Object attendeesVal = data.get("attendees");
        Object[] attendeeArr = null;
        if (attendeesVal instanceof Object[]) {
            attendeeArr = (Object[]) attendeesVal;
        } else if (attendeesVal instanceof List) {
            attendeeArr = ((List<?>) attendeesVal).toArray();
        }
        if (attendeeArr != null) {
            for (Object att : attendeeArr) {
                if (att != null) {
                    String attStr = att.toString();
                    try {
                        if (!attStr.toLowerCase().startsWith("mailto:")) {
                            attStr = "mailto:" + attStr;
                        }
                        todo.add(new Attendee(URI.create(attStr)));
                    } catch (Exception e) {
                        // Skip invalid attendee URI
                    }
                }
            }
        }

        // status (optional)
        Object statusVal = data.get("status");
        if (statusVal != null && !statusVal.toString().isEmpty()) {
            todo.add(new Status(statusVal.toString()));
        }

        // rrule (optional)
        Object rruleVal = data.get("rrule");
        if (rruleVal != null && !rruleVal.toString().isEmpty()) {
            todo.add(new RRule<ZonedDateTime>(rruleVal.toString()));
        }

        // categories (optional) - list of strings
        Object categoriesVal = data.get("categories");
        Object[] categoryArr = null;
        if (categoriesVal instanceof Object[]) {
            categoryArr = (Object[]) categoriesVal;
        } else if (categoriesVal instanceof List) {
            categoryArr = ((List<?>) categoriesVal).toArray();
        }
        if (categoryArr != null && categoryArr.length > 0) {
            Categories cats = new Categories();
            for (Object cat : categoryArr) {
                if (cat != null && !cat.toString().isEmpty()) {
                    cats.addCategory(cat.toString());
                }
            }
            todo.add(cats);
        }

        // classification (optional) - CLASS property
        Object classVal = data.get("classification");
        if (classVal != null && !classVal.toString().isEmpty()) {
            todo.add(new Clazz(classVal.toString()));
        }

        // url (optional)
        Object urlVal = data.get("url");
        if (urlVal != null && !urlVal.toString().isEmpty()) {
            try {
                todo.add(new Url(URI.create(urlVal.toString())));
            } catch (Exception e) {
                // Skip invalid URL
            }
        }

        // created (optional)
        Object createdVal = data.get("created");
        if (createdVal != null) {
            todo.add(new Created(formatAsIcsUtcDate(createdVal)));
        }

        // last_modified (optional)
        Object lastModVal = data.get("last_modified");
        if (lastModVal != null) {
            todo.add(new LastModified(formatAsIcsUtcDate(lastModVal)));
        }

        // sequence (optional)
        Object seqVal = data.get("sequence");
        if (seqVal != null) {
            try {
                int seqInt;
                if (seqVal instanceof Number) {
                    seqInt = ((Number) seqVal).intValue();
                } else {
                    seqInt = Integer.parseInt(seqVal.toString().trim());
                }
                todo.add(new Sequence(seqInt));
            } catch (NumberFormatException e) {
                // Skip invalid sequence value
            }
        }

        // alarms (optional) - list of alarm hashes
        Object alarmsVal = data.get("alarms");
        Object[] alarmArr = null;
        if (alarmsVal instanceof Object[]) {
            alarmArr = (Object[]) alarmsVal;
        } else if (alarmsVal instanceof List) {
            alarmArr = ((List<?>) alarmsVal).toArray();
        }
        if (alarmArr != null) {
            for (Object alarmObj : alarmArr) {
                if (alarmObj == null) {
                    continue;
                }
                Hash alarmData;
                if (alarmObj instanceof Hash) {
                    alarmData = (Hash) alarmObj;
                } else {
                    continue;
                }

                VAlarm alarm = new VAlarm();

                // action (required)
                Object actionVal = alarmData.get("action");
                if (actionVal != null && !actionVal.toString().isEmpty()) {
                    alarm.add(new Action(actionVal.toString()));
                }

                // trigger (required)
                Object triggerVal = alarmData.get("trigger");
                if (triggerVal != null && !triggerVal.toString().isEmpty()) {
                    alarm.add(new Trigger(new net.fortuna.ical4j.model.ParameterList(),
                        triggerVal.toString()));
                }

                // description (optional)
                Object alarmDescVal = alarmData.get("description");
                if (alarmDescVal != null && !alarmDescVal.toString().isEmpty()) {
                    alarm.add(new Description(alarmDescVal.toString()));
                }

                // duration (optional)
                Object alarmDurVal = alarmData.get("duration");
                if (alarmDurVal != null && !alarmDurVal.toString().isEmpty()) {
                    try {
                        alarm.add(new Duration(java.time.Duration.parse(alarmDurVal.toString())));
                    } catch (Exception e) {
                        // Skip invalid duration
                    }
                }

                // repeat (optional)
                Object repeatVal = alarmData.get("repeat");
                if (repeatVal != null) {
                    try {
                        int repeatInt;
                        if (repeatVal instanceof Number) {
                            repeatInt = ((Number) repeatVal).intValue();
                        } else {
                            repeatInt = Integer.parseInt(repeatVal.toString().trim());
                        }
                        alarm.add(new Repeat(repeatInt));
                    } catch (NumberFormatException e) {
                        // Skip invalid repeat value
                    }
                }

                todo.add(alarm);
            }
        }

        calendar.add(todo);
        ++todoCount;
    }

    /**
     * Writes a record based on the current content type.
     * Dispatches to writeEvent() or writeTodo() based on the contentType.
     *
     * @param data the record data as a Hash
     */
    public void writeRecord(Hash data) {
        if ("todos".equals(contentType)) {
            writeTodo(data);
        } else {
            writeEvent(data);
        }
    }

    /**
     * Writes the calendar to a file.
     *
     * @param path the output file path
     * @throws IOException if an I/O error occurs
     */
    public void writeToFile(String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            CalendarOutputter outputter = new CalendarOutputter();
            outputter.output(calendar, fos);
        }
    }

    /**
     * Writes the calendar to a Java OutputStream.
     *
     * @param stream the output stream
     * @throws IOException if an I/O error occurs
     */
    public void writeToStream(OutputStream stream) throws IOException {
        CalendarOutputter outputter = new CalendarOutputter();
        outputter.output(calendar, stream);
    }

    /**
     * Writes the calendar to a Qore OutputStream.
     *
     * @param stream the Qore output stream
     * @throws IOException if an I/O error occurs
     */
    public void writeToStream(qore.Qore.OutputStream stream) throws IOException {
        writeToStream(new org.qore.jni.QoreOutputStreamWrapper(stream));
    }

    /**
     * Returns the calendar data as bytes.
     *
     * @return the ICS data as a byte array
     * @throws IOException if an I/O error occurs
     */
    public byte[] getBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CalendarOutputter outputter = new CalendarOutputter();
            outputter.output(calendar, out);
            return out.toByteArray();
        }
    }

    /**
     * Formats a date value (ZonedDateTime, LocalDateTime, LocalDate, or String) as an iCalendar
     * UTC date-time string in "yyyyMMdd'T'HHmmss'Z'" format.
     *
     * @param dateVal the date value to format
     * @return the formatted iCalendar UTC date-time string
     */
    private static String formatAsIcsUtcDate(Object dateVal) {
        if (dateVal instanceof ZonedDateTime) {
            return ((ZonedDateTime) dateVal).withZoneSameInstant(ZoneOffset.UTC).format(ICS_UTC_FORMAT);
        } else if (dateVal instanceof LocalDateTime) {
            return ((LocalDateTime) dateVal).atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC).format(ICS_UTC_FORMAT);
        } else if (dateVal instanceof LocalDate) {
            return ((LocalDate) dateVal).atStartOfDay(ZoneOffset.UTC).format(ICS_UTC_FORMAT);
        } else {
            // Treat as string; return as-is if it looks like an ICS date, otherwise use current time
            String str = dateVal.toString();
            if (str.matches("\\d{8}T\\d{6}Z?")) {
                return str;
            }
            return ZonedDateTime.now(ZoneOffset.UTC).format(ICS_UTC_FORMAT);
        }
    }

    /**
     * Returns the number of events written.
     */
    public int getEventCount() {
        return eventCount;
    }

    /**
     * Returns the number of todos written.
     */
    public int getTodoCount() {
        return todoCount;
    }

    @Override
    public void close() throws IOException {
        calendar = null;
    }
}
