/*  IcsIterator.java Copyright 2026 Qore Technologies, s.r.o.

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

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TemporalAdapter;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.Attendee;
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
import net.fortuna.ical4j.model.property.Repeat;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Trigger;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Closeable;
import java.lang.reflect.Field;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.qore.jni.Hash;
import org.qore.jni.QoreException;

/**
 * Iterator for reading ICS (iCalendar) files.
 * Parses VEVENT components from iCalendar data and returns them as Hash records.
 * Implements Closeable to ensure proper resource cleanup.
 */
public class IcsIterator extends qore.Qore.AbstractIterator implements Closeable {
    // Basic iCalendar date formats for parsing
    private static final DateTimeFormatter BASIC_UTC_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter BASIC_LOCAL_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter BASIC_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd");

    private Calendar calendar;
    private String contentType = "events";
    private List<VEvent> events;
    private List<VToDo> todos;
    private int currentIndex = -1;
    private Hash currentValue = null;
    private long count = 0;

    static {
        // Enable relaxed parsing for ical4j to handle various date formats
        System.setProperty("ical4j.parsing.relaxed", "true");
        System.setProperty("ical4j.unfolding.relaxed", "true");
        System.setProperty("ical4j.compatibility.outlook", "true");
    }

    /**
     * Creates a new IcsIterator from a Java InputStream.
     *
     * @param stream the input stream containing ICS data
     * @throws IOException if an I/O error occurs
     * @throws ParserException if the ICS data cannot be parsed
     */
    public IcsIterator(java.io.InputStream stream) throws Throwable {
        this(stream, "events");
    }

    /**
     * Creates a new IcsIterator from a Java InputStream with content type selection.
     *
     * @param stream the input stream containing ICS data
     * @param contentType the type of component to extract: "events" or "todos"
     * @throws IOException if an I/O error occurs
     * @throws ParserException if the ICS data cannot be parsed
     */
    public IcsIterator(java.io.InputStream stream, String contentType) throws Throwable {
        try {
            this.contentType = contentType != null ? contentType : "events";
            CalendarBuilder builder = new CalendarBuilder();
            calendar = builder.build(stream);
            extractComponents();
        } finally {
            stream.close();
        }
    }

    /**
     * Creates a new IcsIterator from a Qore InputStream.
     *
     * @param stream the Qore input stream containing ICS data
     * @throws Throwable if an error occurs
     */
    public IcsIterator(qore.Qore.InputStream stream) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), "events");
    }

    /**
     * Creates a new IcsIterator from a Qore InputStream with content type selection.
     *
     * @param stream the Qore input stream containing ICS data
     * @param contentType the type of component to extract: "events" or "todos"
     * @throws Throwable if an error occurs
     */
    public IcsIterator(qore.Qore.InputStream stream, String contentType) throws Throwable {
        this(new org.qore.jni.QoreInputStreamWrapper(stream), contentType);
    }

    /**
     * Creates a new IcsIterator from a file path.
     *
     * @param path the path to the ICS file
     * @throws Throwable if an error occurs
     */
    public IcsIterator(String path) throws Throwable {
        this(new FileInputStream(new File(path)), "events");
    }

    /**
     * Creates a new IcsIterator from a file path with content type selection.
     *
     * @param path the path to the ICS file
     * @param contentType the type of component to extract: "events" or "todos"
     * @throws Throwable if an error occurs
     */
    public IcsIterator(String path, String contentType) throws Throwable {
        this(new FileInputStream(new File(path)), contentType);
    }

    /**
     * Extracts components from the parsed calendar based on the content type.
     */
    private void extractComponents() {
        events = new ArrayList<VEvent>();
        todos = new ArrayList<VToDo>();
        List<CalendarComponent> components = calendar.getComponentList().getAll();
        for (CalendarComponent comp : components) {
            if ("events".equals(contentType) && comp instanceof VEvent) {
                events.add((VEvent) comp);
            } else if ("todos".equals(contentType) && comp instanceof VToDo) {
                todos.add((VToDo) comp);
            }
        }
    }

    /**
     * Converts a VEvent to a Hash record.
     */
    private Hash eventToHash(VEvent event) {
        Hash hash = new Hash();

        // uid (required)
        Optional<Uid> uid = event.getProperty(Property.UID);
        hash.put("uid", uid.isPresent() ? uid.get().getValue() : null);

        // summary (required)
        Optional<Summary> summary = event.getProperty(Property.SUMMARY);
        hash.put("summary", summary.isPresent() ? summary.get().getValue() : null);

        // dtstart (required)
        Optional<DtStart> dtStart = event.getProperty(Property.DTSTART);
        if (dtStart.isPresent()) {
            hash.put("dtstart", parseDateProperty(dtStart.get()));
        } else {
            hash.put("dtstart", null);
        }

        // dtend (optional)
        Optional<DtEnd> dtEnd = event.getProperty(Property.DTEND);
        if (dtEnd.isPresent()) {
            hash.put("dtend", parseDateProperty(dtEnd.get()));
        } else {
            hash.put("dtend", null);
        }

        // duration (optional)
        Optional<Duration> duration = event.getProperty(Property.DURATION);
        hash.put("duration", duration.isPresent() ? duration.get().getValue() : null);

        // location (optional)
        Optional<Location> location = event.getProperty(Property.LOCATION);
        hash.put("location", location.isPresent() ? location.get().getValue() : null);

        // description (optional)
        Optional<Description> description = event.getProperty(Property.DESCRIPTION);
        hash.put("description", description.isPresent() ? description.get().getValue() : null);

        // organizer (optional)
        Optional<Organizer> organizer = event.getProperty(Property.ORGANIZER);
        if (organizer.isPresent()) {
            String orgValue = safeGetValue(organizer.get());
            // Strip mailto: prefix if present
            if (orgValue != null && orgValue.toLowerCase().startsWith("mailto:")) {
                orgValue = orgValue.substring(7);
            }
            hash.put("organizer", orgValue);
        } else {
            hash.put("organizer", null);
        }

        // attendees (optional) - list of strings
        // Use getPropertyList() to avoid generic type issues with getProperties()
        ArrayList<String> attendeeList = new ArrayList<String>();
        for (Property prop : event.getPropertyList().getAll()) {
            if (Property.ATTENDEE.equals(prop.getName())) {
                String val = safeGetValue(prop);
                // Strip mailto: prefix if present
                if (val != null && val.toLowerCase().startsWith("mailto:")) {
                    val = val.substring(7);
                }
                attendeeList.add(val);
            }
        }
        hash.put("attendees", attendeeList.isEmpty() ? null : attendeeList);

        // status (optional)
        Optional<Status> status = event.getProperty(Property.STATUS);
        hash.put("status", status.isPresent() ? safeGetValue(status.get()) : null);

        // rrule (optional) - iterate through all properties to find RRULE
        String rruleValue = null;
        for (Property prop : event.getPropertyList().getAll()) {
            if (Property.RRULE.equals(prop.getName())) {
                rruleValue = safeGetValue(prop);
                break;
            }
        }
        hash.put("rrule", rruleValue);

        // categories (optional) - list of strings from all CATEGORIES properties
        ArrayList<String> categoryList = new ArrayList<String>();
        for (Property prop : event.getPropertyList().getAll()) {
            if (Property.CATEGORIES.equals(prop.getName())) {
                String catValue = safeGetValue(prop);
                if (catValue != null && !catValue.isEmpty()) {
                    for (String cat : catValue.split(",")) {
                        String trimmed = cat.trim();
                        if (!trimmed.isEmpty()) {
                            categoryList.add(trimmed);
                        }
                    }
                }
            }
        }
        hash.put("categories", categoryList.isEmpty() ? null : categoryList);

        // classification (optional) - CLASS property (PUBLIC/PRIVATE/CONFIDENTIAL)
        Optional<Clazz> clazz = event.getProperty(Property.CLASS);
        hash.put("classification", clazz.isPresent() ? safeGetValue(clazz.get()) : null);

        // url (optional)
        Optional<Url> url = event.getProperty(Property.URL);
        hash.put("url", url.isPresent() ? safeGetValue(url.get()) : null);

        // created (optional)
        Optional<Created> created = event.getCreated();
        if (created.isPresent()) {
            hash.put("created", parseDateProperty(created.get()));
        } else {
            hash.put("created", null);
        }

        // last_modified (optional)
        Optional<LastModified> lastModified = event.getLastModified();
        if (lastModified.isPresent()) {
            hash.put("last_modified", parseDateProperty(lastModified.get()));
        } else {
            hash.put("last_modified", null);
        }

        // transp (optional) - OPAQUE or TRANSPARENT
        Optional<Transp> transp = event.getProperty(Property.TRANSP);
        hash.put("transp", transp.isPresent() ? safeGetValue(transp.get()) : null);

        // sequence (optional)
        Optional<Sequence> sequence = event.getSequence();
        if (sequence.isPresent()) {
            try {
                hash.put("sequence", sequence.get().getSequenceNo());
            } catch (Exception e) {
                String seqStr = safeGetValue(sequence.get());
                if (seqStr != null) {
                    try {
                        hash.put("sequence", Integer.parseInt(seqStr.trim()));
                    } catch (NumberFormatException nfe) {
                        hash.put("sequence", null);
                    }
                } else {
                    hash.put("sequence", null);
                }
            }
        } else {
            hash.put("sequence", null);
        }

        // alarms (optional) - list of alarm hashes
        List<VAlarm> alarms = event.getAlarms();
        if (alarms != null && !alarms.isEmpty()) {
            ArrayList<Hash> alarmList = new ArrayList<Hash>();
            for (VAlarm alarm : alarms) {
                Hash alarmHash = new Hash();

                // action (required for VALARM)
                Optional<Action> action = alarm.getAction();
                alarmHash.put("action", action.isPresent() ? safeGetValue(action.get()) : null);

                // trigger (required for VALARM)
                Optional<Trigger> trigger = alarm.getTrigger();
                alarmHash.put("trigger", trigger.isPresent() ? safeGetValue(trigger.get()) : null);

                // description (optional)
                Optional<Description> alarmDesc = alarm.getProperty(Property.DESCRIPTION);
                alarmHash.put("description",
                    alarmDesc.isPresent() ? safeGetValue(alarmDesc.get()) : null);

                // duration (optional)
                Optional<Duration> alarmDuration = alarm.getDuration();
                alarmHash.put("duration",
                    alarmDuration.isPresent() ? safeGetValue(alarmDuration.get()) : null);

                // repeat (optional)
                Optional<Repeat> alarmRepeat = alarm.getRepeat();
                if (alarmRepeat.isPresent()) {
                    try {
                        alarmHash.put("repeat", alarmRepeat.get().getCount());
                    } catch (Exception e) {
                        alarmHash.put("repeat", null);
                    }
                } else {
                    alarmHash.put("repeat", null);
                }

                alarmList.add(alarmHash);
            }
            hash.put("alarms", alarmList);
        } else {
            hash.put("alarms", null);
        }

        return hash;
    }

    /**
     * Converts a VToDo to a Hash record.
     * Extracts shared fields (uid, summary, dtstart, etc.) plus todo-specific fields
     * (due, completed, percent_complete, priority).
     */
    private Hash todoToHash(VToDo todo) {
        Hash hash = new Hash();

        // uid (required)
        Optional<Uid> uid = todo.getProperty(Property.UID);
        hash.put("uid", uid.isPresent() ? uid.get().getValue() : null);

        // summary (required)
        Optional<Summary> summary = todo.getProperty(Property.SUMMARY);
        hash.put("summary", summary.isPresent() ? summary.get().getValue() : null);

        // dtstart (optional for todos)
        Optional<DtStart> dtStart = todo.getProperty(Property.DTSTART);
        if (dtStart.isPresent()) {
            hash.put("dtstart", parseDateProperty(dtStart.get()));
        } else {
            hash.put("dtstart", null);
        }

        // due (optional) - todo-specific
        @SuppressWarnings("unchecked")
        Optional<Due<?>> due = (Optional<Due<?>>) (Optional<?>) todo.getProperty(Property.DUE);
        if (due.isPresent()) {
            hash.put("due", parseDateProperty(due.get()));
        } else {
            hash.put("due", null);
        }

        // completed (optional) - todo-specific
        Optional<Completed> completed = todo.getDateCompleted();
        if (completed.isPresent()) {
            hash.put("completed", parseDateProperty(completed.get()));
        } else {
            hash.put("completed", null);
        }

        // percent_complete (optional) - todo-specific
        Optional<PercentComplete> pctComplete = todo.getProperty(Property.PERCENT_COMPLETE);
        if (pctComplete.isPresent()) {
            try {
                hash.put("percent_complete", pctComplete.get().getPercentage());
            } catch (Exception e) {
                String pctStr = safeGetValue(pctComplete.get());
                if (pctStr != null) {
                    try {
                        hash.put("percent_complete", Integer.parseInt(pctStr.trim()));
                    } catch (NumberFormatException nfe) {
                        hash.put("percent_complete", null);
                    }
                } else {
                    hash.put("percent_complete", null);
                }
            }
        } else {
            hash.put("percent_complete", null);
        }

        // priority (optional) - todo-specific
        Optional<Priority> priority = todo.getProperty(Property.PRIORITY);
        if (priority.isPresent()) {
            try {
                hash.put("priority", priority.get().getLevel());
            } catch (Exception e) {
                String priStr = safeGetValue(priority.get());
                if (priStr != null) {
                    try {
                        hash.put("priority", Integer.parseInt(priStr.trim()));
                    } catch (NumberFormatException nfe) {
                        hash.put("priority", null);
                    }
                } else {
                    hash.put("priority", null);
                }
            }
        } else {
            hash.put("priority", null);
        }

        // description (optional)
        Optional<Description> description = todo.getProperty(Property.DESCRIPTION);
        hash.put("description", description.isPresent() ? description.get().getValue() : null);

        // organizer (optional)
        Optional<Organizer> organizer = todo.getProperty(Property.ORGANIZER);
        if (organizer.isPresent()) {
            String orgValue = safeGetValue(organizer.get());
            // Strip mailto: prefix if present
            if (orgValue != null && orgValue.toLowerCase().startsWith("mailto:")) {
                orgValue = orgValue.substring(7);
            }
            hash.put("organizer", orgValue);
        } else {
            hash.put("organizer", null);
        }

        // attendees (optional) - list of strings
        ArrayList<String> attendeeList = new ArrayList<String>();
        for (Property prop : todo.getPropertyList().getAll()) {
            if (Property.ATTENDEE.equals(prop.getName())) {
                String val = safeGetValue(prop);
                // Strip mailto: prefix if present
                if (val != null && val.toLowerCase().startsWith("mailto:")) {
                    val = val.substring(7);
                }
                attendeeList.add(val);
            }
        }
        hash.put("attendees", attendeeList.isEmpty() ? null : attendeeList);

        // status (optional)
        Optional<Status> status = todo.getProperty(Property.STATUS);
        hash.put("status", status.isPresent() ? safeGetValue(status.get()) : null);

        // rrule (optional)
        String rruleValue = null;
        for (Property prop : todo.getPropertyList().getAll()) {
            if (Property.RRULE.equals(prop.getName())) {
                rruleValue = safeGetValue(prop);
                break;
            }
        }
        hash.put("rrule", rruleValue);

        // categories (optional) - list of strings from all CATEGORIES properties
        ArrayList<String> categoryList = new ArrayList<String>();
        for (Property prop : todo.getPropertyList().getAll()) {
            if (Property.CATEGORIES.equals(prop.getName())) {
                String catValue = safeGetValue(prop);
                if (catValue != null && !catValue.isEmpty()) {
                    for (String cat : catValue.split(",")) {
                        String trimmed = cat.trim();
                        if (!trimmed.isEmpty()) {
                            categoryList.add(trimmed);
                        }
                    }
                }
            }
        }
        hash.put("categories", categoryList.isEmpty() ? null : categoryList);

        // classification (optional) - CLASS property (PUBLIC/PRIVATE/CONFIDENTIAL)
        Optional<Clazz> clazz = todo.getProperty(Property.CLASS);
        hash.put("classification", clazz.isPresent() ? safeGetValue(clazz.get()) : null);

        // url (optional)
        Optional<Url> url = todo.getProperty(Property.URL);
        hash.put("url", url.isPresent() ? safeGetValue(url.get()) : null);

        // created (optional)
        Optional<Created> created = todo.getCreated();
        if (created.isPresent()) {
            hash.put("created", parseDateProperty(created.get()));
        } else {
            hash.put("created", null);
        }

        // last_modified (optional)
        Optional<LastModified> lastModified = todo.getLastModified();
        if (lastModified.isPresent()) {
            hash.put("last_modified", parseDateProperty(lastModified.get()));
        } else {
            hash.put("last_modified", null);
        }

        // sequence (optional)
        Optional<Sequence> sequence = todo.getSequence();
        if (sequence.isPresent()) {
            try {
                hash.put("sequence", sequence.get().getSequenceNo());
            } catch (Exception e) {
                String seqStr = safeGetValue(sequence.get());
                if (seqStr != null) {
                    try {
                        hash.put("sequence", Integer.parseInt(seqStr.trim()));
                    } catch (NumberFormatException nfe) {
                        hash.put("sequence", null);
                    }
                } else {
                    hash.put("sequence", null);
                }
            }
        } else {
            hash.put("sequence", null);
        }

        // alarms (optional) - list of alarm hashes
        List<VAlarm> alarms = todo.getAlarms();
        if (alarms != null && !alarms.isEmpty()) {
            ArrayList<Hash> alarmList = new ArrayList<Hash>();
            for (VAlarm alarm : alarms) {
                Hash alarmHash = new Hash();

                // action (required for VALARM)
                Optional<Action> action = alarm.getAction();
                alarmHash.put("action", action.isPresent() ? safeGetValue(action.get()) : null);

                // trigger (required for VALARM)
                Optional<Trigger> trigger = alarm.getTrigger();
                alarmHash.put("trigger", trigger.isPresent() ? safeGetValue(trigger.get()) : null);

                // description (optional)
                Optional<Description> alarmDesc = alarm.getProperty(Property.DESCRIPTION);
                alarmHash.put("description",
                    alarmDesc.isPresent() ? safeGetValue(alarmDesc.get()) : null);

                // duration (optional)
                Optional<Duration> alarmDuration = alarm.getDuration();
                alarmHash.put("duration",
                    alarmDuration.isPresent() ? safeGetValue(alarmDuration.get()) : null);

                // repeat (optional)
                Optional<Repeat> alarmRepeat = alarm.getRepeat();
                if (alarmRepeat.isPresent()) {
                    try {
                        alarmHash.put("repeat", alarmRepeat.get().getCount());
                    } catch (Exception e) {
                        alarmHash.put("repeat", null);
                    }
                } else {
                    alarmHash.put("repeat", null);
                }

                alarmList.add(alarmHash);
            }
            hash.put("alarms", alarmList);
        } else {
            hash.put("alarms", null);
        }

        return hash;
    }

    /**
     * Safely parses a date property, handling ical4j 4.x parsing quirks.
     *
     * iCal4j 4.x has an issue where getDate() and getValue() can fail with
     * DateTimeParseException when dates are stored via the TemporalAdapter.
     * This method tries the normal API first, then uses reflection to access the raw
     * date string from the internal TemporalAdapter, and finally falls back to
     * extracting the date string from the property's toString() output.
     */
    @SuppressWarnings("unchecked")
    private ZonedDateTime parseDateProperty(net.fortuna.ical4j.model.property.DateProperty<?> prop) {
        // Try the normal API first
        try {
            Temporal temporal = prop.getDate();
            return temporalToZonedDateTime(temporal);
        } catch (Exception e) {
            // Fall through to reflection approach
        }

        // Use reflection to access the internal TemporalAdapter's raw valueString
        try {
            Field dateField = net.fortuna.ical4j.model.property.DateProperty.class
                .getDeclaredField("date");
            dateField.setAccessible(true);
            Object adapter = dateField.get(prop);
            if (adapter != null) {
                // Access TemporalAdapter.valueString - the raw date string
                Field valueStringField = net.fortuna.ical4j.model.TemporalAdapter.class
                    .getDeclaredField("valueString");
                valueStringField.setAccessible(true);
                String rawDateStr = (String) valueStringField.get(adapter);
                if (rawDateStr != null && !rawDateStr.isEmpty()) {
                    return parseIcsDateString(rawDateStr);
                }
            }
        } catch (Exception e2) {
            // Fall through to last resort
        }

        // Last resort: extract date from toString() output (format: "DTSTART:20260315T100000Z")
        // Avoids getValue() which chains through the same broken TemporalAdapter path
        return extractDateFromPropertyString(prop.toString());
    }

    /**
     * Safely gets the value of a property, falling back to extracting from toString().
     */
    private String safeGetValue(Property prop) {
        try {
            return prop.getValue();
        } catch (Exception e) {
            return extractValueFromPropertyString(prop.toString());
        }
    }

    /**
     * Extracts the value part from a property's toString() output.
     * The format is "PROPNAME;PARAMS:VALUE\r\n" or "PROPNAME:VALUE\r\n".
     */
    private String extractValueFromPropertyString(String propStr) {
        if (propStr == null) {
            return null;
        }
        int colonIdx = propStr.indexOf(':');
        if (colonIdx >= 0 && colonIdx < propStr.length() - 1) {
            return propStr.substring(colonIdx + 1).replaceAll("[\\r\\n]+$", "");
        }
        return null;
    }

    /**
     * Extracts and parses the date value from a property's toString() output.
     * The format is "PROPNAME;PARAMS:VALUE" or "PROPNAME:VALUE".
     */
    private ZonedDateTime extractDateFromPropertyString(String propStr) {
        if (propStr == null) {
            return null;
        }
        // Find the value after the last colon
        int colonIdx = propStr.lastIndexOf(':');
        if (colonIdx >= 0 && colonIdx < propStr.length() - 1) {
            String dateStr = propStr.substring(colonIdx + 1).trim();
            // Remove trailing \r\n if present
            dateStr = dateStr.replaceAll("[\\r\\n]+$", "");
            return parseIcsDateString(dateStr);
        }
        return null;
    }

    /**
     * Parses an iCalendar date/time string into a ZonedDateTime.
     * Handles formats: 20260315T100000Z, 20260315T100000, 20260315
     */
    private ZonedDateTime parseIcsDateString(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            // Format: 20260315T100000Z (basic UTC)
            if (dateStr.endsWith("Z") && dateStr.length() == 16) {
                LocalDateTime ldt = LocalDateTime.parse(dateStr,
                    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
                return ldt.atZone(ZoneOffset.UTC);
            }
            // Format: 20260315T100000 (basic local)
            if (dateStr.length() == 15 && dateStr.contains("T")) {
                LocalDateTime ldt = LocalDateTime.parse(dateStr,
                    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
                return ldt.atZone(ZoneId.systemDefault());
            }
            // Format: 20260315 (date only)
            if (dateStr.length() == 8 && !dateStr.contains("T")) {
                LocalDate ld = LocalDate.parse(dateStr,
                    DateTimeFormatter.ofPattern("yyyyMMdd"));
                return ld.atStartOfDay(ZoneId.systemDefault());
            }
            // Try standard ISO format
            return ZonedDateTime.parse(dateStr);
        } catch (DateTimeParseException e2) {
            return null;
        }
    }

    /**
     * Converts a Temporal value to a ZonedDateTime for Qore date compatibility.
     */
    private ZonedDateTime temporalToZonedDateTime(Temporal temporal) {
        if (temporal instanceof ZonedDateTime) {
            return (ZonedDateTime) temporal;
        } else if (temporal instanceof Instant) {
            return ((Instant) temporal).atZone(ZoneOffset.UTC);
        } else if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).atZone(ZoneId.systemDefault());
        } else if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay(ZoneId.systemDefault());
        }
        // Fallback: try to create a ZonedDateTime from the temporal
        try {
            return ZonedDateTime.from(temporal);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the number of VEVENT components in the calendar.
     */
    public int getEventCount() {
        return events != null ? events.size() : 0;
    }

    /**
     * Returns the number of components based on the current content type.
     */
    public int getComponentCount() {
        if ("todos".equals(contentType)) {
            return todos != null ? todos.size() : 0;
        }
        return events != null ? events.size() : 0;
    }

    /**
     * Returns the number of records iterated so far.
     */
    public long getCount() {
        return count;
    }

    @Override
    public boolean next() {
        ++currentIndex;
        if ("todos".equals(contentType)) {
            if (currentIndex < todos.size()) {
                currentValue = todoToHash(todos.get(currentIndex));
                ++count;
                return true;
            }
        } else {
            if (currentIndex < events.size()) {
                currentValue = eventToHash(events.get(currentIndex));
                ++count;
                return true;
            }
        }
        currentValue = null;
        return false;
    }

    @Override
    public Hash getValue() throws QoreException {
        if (currentValue == null) {
            throw new QoreException("INVALID-ITERATOR", "iterator is not valid; next() must return true before " +
                "calling this method");
        }
        return currentValue;
    }

    @Override
    public boolean valid() {
        return currentValue != null;
    }

    @Override
    public void close() throws IOException {
        calendar = null;
        events = null;
        todos = null;
        currentValue = null;
    }
}
