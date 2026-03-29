/*  IcsTestHelper.java Copyright 2026 Qore Technologies, s.r.o.

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
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.component.VAlarm;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.net.URI;
import java.time.ZonedDateTime;

/**
 * Helper class for creating test ICS files for the IcsDataProvider tests.
 */
public class IcsTestHelper {

    /**
     * Creates a simple ICS file with 3 events.
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createSimpleIcs(String path) throws IOException {
        Calendar calendar = createBaseCalendar();

        // Event 1: Team Meeting
        VEvent event1 = new VEvent();
        event1.add(new Uid("event-001@test.example.com"));
        event1.add(new Summary("Team Meeting"));
        event1.add(new DtStart("20260315T100000Z"));
        event1.add(new DtEnd("20260315T110000Z"));
        event1.add(new Location("Conference Room A"));
        event1.add(new Description("Weekly team sync meeting"));
        event1.add(new Organizer(URI.create("mailto:boss@example.com")));
        event1.add(new Status("CONFIRMED"));
        calendar.add(event1);

        // Event 2: Lunch
        VEvent event2 = new VEvent();
        event2.add(new Uid("event-002@test.example.com"));
        event2.add(new Summary("Lunch with Client"));
        event2.add(new DtStart("20260316T120000Z"));
        event2.add(new DtEnd("20260316T133000Z"));
        event2.add(new Location("Downtown Restaurant"));
        event2.add(new Status("TENTATIVE"));
        calendar.add(event2);

        // Event 3: Workshop
        VEvent event3 = new VEvent();
        event3.add(new Uid("event-003@test.example.com"));
        event3.add(new Summary("Workshop"));
        event3.add(new DtStart("20260320T090000Z"));
        event3.add(new DtEnd("20260320T170000Z"));
        event3.add(new Location("Training Center"));
        event3.add(new Description("Full-day development workshop"));
        event3.add(new Attendee(URI.create("mailto:alice@example.com")));
        event3.add(new Attendee(URI.create("mailto:bob@example.com")));
        event3.add(new Status("CONFIRMED"));
        calendar.add(event3);

        writeCalendar(calendar, path);
    }

    /**
     * Creates a simple ICS file and returns the bytes.
     *
     * @return the ICS data as a byte array
     * @throws IOException if an I/O error occurs
     */
    public static byte[] createSimpleIcsBytes() throws IOException {
        Calendar calendar = createBaseCalendar();

        VEvent event1 = new VEvent();
        event1.add(new Uid("bytes-001@test.example.com"));
        event1.add(new Summary("Byte Event 1"));
        event1.add(new DtStart("20260401T140000Z"));
        event1.add(new DtEnd("20260401T150000Z"));
        event1.add(new Location("Room 101"));
        calendar.add(event1);

        VEvent event2 = new VEvent();
        event2.add(new Uid("bytes-002@test.example.com"));
        event2.add(new Summary("Byte Event 2"));
        event2.add(new DtStart("20260402T100000Z"));
        event2.add(new DtEnd("20260402T110000Z"));
        calendar.add(event2);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CalendarOutputter outputter = new CalendarOutputter();
            outputter.output(calendar, out);
            return out.toByteArray();
        }
    }

    /**
     * Creates an ICS file with a recurring event (RRULE).
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createRecurringIcs(String path) throws IOException {
        Calendar calendar = createBaseCalendar();

        VEvent event = new VEvent();
        event.add(new Uid("recurring-001@test.example.com"));
        event.add(new Summary("Weekly Standup"));
        event.add(new DtStart("20260105T090000Z"));
        event.add(new DtEnd("20260105T091500Z"));
        event.add(new Location("Standup Area"));
        event.add(new Description("Daily standup meeting"));
        event.add(new Organizer(URI.create("mailto:scrum-master@example.com")));
        event.add(new RRule<ZonedDateTime>("FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=52"));
        event.add(new Status("CONFIRMED"));
        calendar.add(event);

        writeCalendar(calendar, path);
    }

    /**
     * Creates an empty ICS file (valid VCALENDAR with no events).
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createEmptyIcs(String path) throws IOException {
        Calendar calendar = createBaseCalendar();
        writeCalendar(calendar, path);
    }

    /**
     * Creates an ICS file with events containing all extended fields:
     * categories, classification, url, created, last_modified, transp, sequence, and alarms.
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createFullFieldsIcs(String path) throws IOException {
        Calendar calendar = createFullFieldsCalendar();
        writeCalendar(calendar, path);
    }

    /**
     * Creates an ICS file with events containing all extended fields and returns the bytes.
     *
     * @return the ICS data as a byte array
     * @throws IOException if an I/O error occurs
     */
    public static byte[] createFullFieldsIcsBytes() throws IOException {
        Calendar calendar = createFullFieldsCalendar();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CalendarOutputter outputter = new CalendarOutputter();
            outputter.output(calendar, out);
            return out.toByteArray();
        }
    }

    /**
     * Creates a Calendar with events containing all extended fields.
     */
    private static Calendar createFullFieldsCalendar() {
        Calendar calendar = createBaseCalendar();

        // Event 1: Conference with all extended fields
        VEvent event1 = new VEvent();
        event1.add(new Uid("full-001@test.example.com"));
        event1.add(new Summary("Annual Conference"));
        event1.add(new DtStart("20260601T090000Z"));
        event1.add(new DtEnd("20260601T170000Z"));
        event1.add(new Location("Convention Center"));
        event1.add(new Description("Annual company conference"));
        event1.add(new Organizer(URI.create("mailto:events@example.com")));
        event1.add(new Status("CONFIRMED"));

        // Categories
        Categories cats1 = new Categories();
        cats1.addCategory("CONFERENCE");
        cats1.addCategory("BUSINESS");
        cats1.addCategory("ANNUAL");
        event1.add(cats1);

        // Classification
        event1.add(new Clazz("PUBLIC"));

        // URL
        event1.add(new Url(URI.create("https://example.com/conference")));

        // Created and Last-Modified
        event1.add(new Created("20260101T120000Z"));
        event1.add(new LastModified("20260501T080000Z"));

        // Transp
        event1.add(new Transp("OPAQUE"));

        // Sequence
        event1.add(new Sequence(2));

        // Alarm 1: Display alarm 15 minutes before
        VAlarm alarm1 = new VAlarm();
        alarm1.add(new Action("DISPLAY"));
        alarm1.add(new Trigger(new ParameterList(), "-PT15M"));
        alarm1.add(new Description("Conference starts in 15 minutes"));
        event1.add(alarm1);

        // Alarm 2: Email alarm 1 day before with repeat
        VAlarm alarm2 = new VAlarm();
        alarm2.add(new Action("EMAIL"));
        alarm2.add(new Trigger(new ParameterList(), "-P1D"));
        alarm2.add(new Description("Conference tomorrow"));
        alarm2.add(new Duration(java.time.Duration.parse("PT1H")));
        alarm2.add(new Repeat(2));
        event1.add(alarm2);

        calendar.add(event1);

        // Event 2: Private meeting with minimal extended fields
        VEvent event2 = new VEvent();
        event2.add(new Uid("full-002@test.example.com"));
        event2.add(new Summary("Private Meeting"));
        event2.add(new DtStart("20260602T100000Z"));
        event2.add(new DtEnd("20260602T110000Z"));
        event2.add(new Clazz("PRIVATE"));
        event2.add(new Transp("TRANSPARENT"));
        event2.add(new Sequence(0));
        event2.add(new Created("20260501T090000Z"));
        calendar.add(event2);

        return calendar;
    }

    /**
     * Creates an ICS file with VTODO components.
     *
     * @param path The path to create the file at
     * @throws IOException if an I/O error occurs
     */
    public static void createTodoIcs(String path) throws IOException {
        Calendar calendar = createTodoCalendar();
        writeCalendar(calendar, path);
    }

    /**
     * Creates an ICS file with VTODO components and returns the bytes.
     *
     * @return the ICS data as a byte array
     * @throws IOException if an I/O error occurs
     */
    public static byte[] createTodoIcsBytes() throws IOException {
        Calendar calendar = createTodoCalendar();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CalendarOutputter outputter = new CalendarOutputter();
            outputter.output(calendar, out);
            return out.toByteArray();
        }
    }

    /**
     * Creates a Calendar with VTODO components containing various fields.
     */
    private static Calendar createTodoCalendar() {
        Calendar calendar = createBaseCalendar();

        // Todo 1: Complete project proposal - in-process with priority and categories
        VToDo todo1 = new VToDo();
        todo1.add(new Uid("todo-001@test.example.com"));
        todo1.add(new Summary("Complete project proposal"));
        todo1.add(new DtStart("20260401T090000Z"));
        todo1.add(new Due("20260415T170000Z"));
        todo1.add(new Priority(1));
        todo1.add(new PercentComplete(75));
        todo1.add(new Status("IN-PROCESS"));
        todo1.add(new Description("Write and submit the Q2 project proposal"));
        todo1.add(new Organizer(URI.create("mailto:manager@example.com")));
        Categories cats1 = new Categories();
        cats1.addCategory("WORK");
        cats1.addCategory("URGENT");
        todo1.add(cats1);
        todo1.add(new Clazz("PRIVATE"));
        todo1.add(new Url(URI.create("https://example.com/projects/proposal")));
        todo1.add(new Created("20260301T120000Z"));
        todo1.add(new LastModified("20260325T080000Z"));
        todo1.add(new Sequence(2));

        // Add an alarm to todo1
        VAlarm alarm1 = new VAlarm();
        alarm1.add(new Action("DISPLAY"));
        alarm1.add(new Trigger(new ParameterList(), "-P1D"));
        alarm1.add(new Description("Proposal due tomorrow"));
        todo1.add(alarm1);

        calendar.add(todo1);

        // Todo 2: Buy groceries - simple needs-action
        VToDo todo2 = new VToDo();
        todo2.add(new Uid("todo-002@test.example.com"));
        todo2.add(new Summary("Buy groceries"));
        todo2.add(new Due("20260330T180000Z"));
        todo2.add(new Priority(5));
        todo2.add(new Status("NEEDS-ACTION"));
        calendar.add(todo2);

        // Todo 3: Review code - completed
        VToDo todo3 = new VToDo();
        todo3.add(new Uid("todo-003@test.example.com"));
        todo3.add(new Summary("Review code"));
        todo3.add(new DtStart("20260325T090000Z"));
        todo3.add(new Completed("20260328T160000Z"));
        todo3.add(new PercentComplete(100));
        todo3.add(new Status("COMPLETED"));
        todo3.add(new Description("Review pull request #42"));
        calendar.add(todo3);

        return calendar;
    }

    /**
     * Creates a base Calendar with standard VCALENDAR properties.
     */
    private static Calendar createBaseCalendar() {
        Calendar calendar = new Calendar();
        calendar.add(new ProdId("-//Qore Technologies//IcsTestHelper//EN"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        return calendar;
    }

    /**
     * Writes a Calendar to a file.
     */
    private static void writeCalendar(Calendar calendar, String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(path))) {
            CalendarOutputter outputter = new CalendarOutputter();
            outputter.output(calendar, fos);
        }
    }
}
