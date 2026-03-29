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
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
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
