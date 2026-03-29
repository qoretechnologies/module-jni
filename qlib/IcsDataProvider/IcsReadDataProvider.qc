# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore IcsReadDataProvider class definition

/*  IcsReadDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the IcsDataProvider namespace
public namespace IcsDataProvider {
#! Provides a data provider for reading ICS (iCalendar) files
public class IcsReadDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "IcsReadDataProvider",
            "supports_read": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "data": <DataProviderOptionInfo>{
                "display_name": "Input Data",
                "short_desc": "ICS data to process",
                "type": AbstractDataProviderTypeMap."data",
                "desc": "ICS data to process; conflicts with `stream` and `path`",
            },
            "path": <DataProviderOptionInfo>{
                "display_name": "ICS Input Path",
                "short_desc": "The location of the ICS file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The location of the ICS file; mutually exclusive with `data` and `stream`.\n\n"
                    "Resolved with a call to "
                    "[FileLocationHandler::getBinaryStreamFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a29c2338efc901a6b8f24542505e2a094)",
                "example_value": "file:///path/to/calendar.ics",
            },
            "stream": <DataProviderOptionInfo>{
                "display_name": "ICS Input Stream",
                "short_desc": "An input stream for ICS data",
                "type": AbstractDataProviderType::get(new Type("InputStream")),
                "desc": "An input stream for ICS data; mutually exclusive with `data` and `path`",
            },
            "content_type": <DataProviderOptionInfo>{
                "display_name": "Content Type",
                "short_desc": "Type of calendar component to read",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The type of iCalendar component to extract: \"events\" (default) or \"todos\"",
                "default_value": "events",
                "allowed_values": (
                    <AllowedValueInfo>{
                        "value": "events",
                        "display_name": "Events",
                        "short_desc": "Read VEVENT components",
                        "desc": "Read VEVENT (calendar event) components with fields for dates, "
                            "location, attendees, recurrence, categories, and alarms",
                    },
                    <AllowedValueInfo>{
                        "value": "todos",
                        "display_name": "To-dos",
                        "short_desc": "Read VTODO components",
                        "desc": "Read VTODO (task/to-do) components with fields for due date, "
                            "completion status, priority, percent complete, and categories",
                    },
                ),
            },
        };
    }

    private {
        #! The input stream for the ICS data
        InputStream i;

        #! The iterator object
        IcsIterator it;

        #! The name of the object
        string name;

        #! Dummy iterator flag
        bool dummy;

        #! The content type to extract: "events" or "todos"
        string content_type = "events";
    }

    #! Creates the IcsReadDataProvider with the input data path and optionally an option hash
    constructor(string path, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("ICS-READ-OPTION-ERROR", ConstructorOptions, opts);
        i = FileLocationHandler::getBinaryStreamFromLocation(path);
        name = path;
        content_type = copts.content_type ?? "events";
    }

    #! Creates the IcsReadDataProvider with the input data stream
    constructor(InputStream stream, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("ICS-READ-OPTION-ERROR", ConstructorOptions, opts);
        i = stream;
        name = sprintf("ics-stream=%s", stream.uniqueHash());
        content_type = copts.content_type ?? "events";
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("ICS-READ-OPTION-ERROR", ConstructorOptions, options);
        content_type = copts.content_type ?? "events";
        if (copts.path) {
            if (copts.stream) {
                error("ICS-READ-OPTION-ERROR", "cannot provide both \"stream\" and \"path\" options; use either "
                    "one or the other");
            }
            if (copts.data) {
                error("ICS-READ-OPTION-ERROR", "cannot provide both \"data\" and \"path\" options; use either "
                    "one or the other");
            }
            i = FileLocationHandler::getBinaryStreamFromLocation(copts.path);
            name = copts.path;
        } else if (copts.stream) {
            if (copts.data) {
                error("ICS-READ-OPTION-ERROR", "cannot provide both \"data\" and \"stream\" options; use either "
                    "one or the other");
            }
            i = copts.stream;
            name = sprintf("ics-stream=%s", i.uniqueHash());
        } else if (copts.data) {
            i = new BinaryInputStream(copts.data);
            name = sprintf("ics-data=%s", i.uniqueHash());
        }
        if (!i) {
            # create a dummy iterator with no input
            i = new StringInputStream("");
            name = "ics";
            dummy = True;
        }
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Common setup code
    private checkIterator() {
        if (it) {
            return;
        }
        it = new IcsIterator(i, content_type);
    }

    #! Returns an iterator for zero or more records matching the search options
    private AbstractDataProviderRecordIterator searchRecordsImpl(*hash<auto> where_cond,
            *hash<auto> search_options) {
        checkIterator();
        return new DefaultRecordIterator(it, where_cond, search_options, getRecordType());
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        if (dummy) {
            return {};
        }
        if (content_type == "todos") {
            return IcsDataProvider::getIcsTodoRecordType();
        }
        return IcsDataProvider::getIcsRecordType();
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}

#! Returns the shared ICS event record type
/** @return a hash of field name to AbstractDataField entries describing the ICS event record schema
*/
public hash<string, AbstractDataField> sub getIcsRecordType() {
    return {
        "uid": new QoreDataField("uid", "The unique identifier of the event",
            AbstractDataProviderType::get(StringType)),
        "summary": new QoreDataField("summary", "The event summary / title",
            AbstractDataProviderType::get(StringType)),
        "dtstart": new QoreDataField("dtstart", "The start date/time of the event",
            AbstractDataProviderType::get(DateType)),
        "dtend": new QoreDataField("dtend", "The end date/time of the event (optional)",
            AbstractDataProviderType::get(new Type("*date"))),
        "duration": new QoreDataField("duration", "The duration of the event in ISO 8601 format (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "location": new QoreDataField("location", "The location of the event (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "description": new QoreDataField("description", "A detailed description of the event (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "organizer": new QoreDataField("organizer", "The organizer's email address (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "attendees": new QoreDataField("attendees", "List of attendee email addresses (optional)",
            AbstractDataProviderType::get(new Type("*list<string>"))),
        "status": new QoreDataField("status",
            "The event status: TENTATIVE, CONFIRMED, or CANCELLED (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "rrule": new QoreDataField("rrule", "The recurrence rule in RFC 5545 RRULE format (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "categories": new QoreDataField("categories", "List of event categories (optional)",
            AbstractDataProviderType::get(new Type("*list<string>"))),
        "classification": new QoreDataField("classification",
            "Event classification: PUBLIC, PRIVATE, or CONFIDENTIAL (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "url": new QoreDataField("url", "A URL associated with the event (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "created": new QoreDataField("created", "The date/time the event was created (optional)",
            AbstractDataProviderType::get(new Type("*date"))),
        "last_modified": new QoreDataField("last_modified",
            "The date/time the event was last modified (optional)",
            AbstractDataProviderType::get(new Type("*date"))),
        "transp": new QoreDataField("transp",
            "Time transparency: OPAQUE or TRANSPARENT (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "sequence": new QoreDataField("sequence", "The revision sequence number (optional)",
            AbstractDataProviderType::get(SoftIntOrNothingType)),
        "alarms": new QoreDataField("alarms",
            "List of alarm hashes with 'action', 'trigger', 'description', 'duration', 'repeat' keys (optional)",
            AbstractDataProviderType::get(new Type("*list<hash<auto>>"))),
    };
}
#! Returns the shared ICS todo record type
/** @return a hash of field name to AbstractDataField entries describing the ICS todo record schema
*/
public hash<string, AbstractDataField> sub getIcsTodoRecordType() {
    return {
        "uid": new QoreDataField("uid", "The unique identifier of the to-do",
            AbstractDataProviderType::get(StringType)),
        "summary": new QoreDataField("summary", "The to-do summary / title",
            AbstractDataProviderType::get(StringType)),
        "dtstart": new QoreDataField("dtstart", "The start date/time of the to-do (optional)",
            AbstractDataProviderType::get(new Type("*date"))),
        "due": new QoreDataField("due", "The due date/time of the to-do (optional)",
            AbstractDataProviderType::get(new Type("*date"))),
        "completed": new QoreDataField("completed", "The completion date/time (optional)",
            AbstractDataProviderType::get(new Type("*date"))),
        "percent_complete": new QoreDataField("percent_complete",
            "Completion percentage 0-100 (optional)",
            AbstractDataProviderType::get(SoftIntOrNothingType)),
        "priority": new QoreDataField("priority",
            "Priority 0-9 where 1 is highest (optional)",
            AbstractDataProviderType::get(SoftIntOrNothingType)),
        "status": new QoreDataField("status",
            "The to-do status: NEEDS-ACTION, IN-PROCESS, COMPLETED, or CANCELLED (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "description": new QoreDataField("description",
            "A detailed description of the to-do (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "organizer": new QoreDataField("organizer", "The organizer's email address (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "attendees": new QoreDataField("attendees", "List of attendee email addresses (optional)",
            AbstractDataProviderType::get(new Type("*list<string>"))),
        "rrule": new QoreDataField("rrule",
            "The recurrence rule in RFC 5545 RRULE format (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "categories": new QoreDataField("categories", "List of to-do categories (optional)",
            AbstractDataProviderType::get(new Type("*list<string>"))),
        "classification": new QoreDataField("classification",
            "To-do classification: PUBLIC, PRIVATE, or CONFIDENTIAL (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "url": new QoreDataField("url", "A URL associated with the to-do (optional)",
            AbstractDataProviderType::get(SoftStringOrNothingType)),
        "created": new QoreDataField("created", "The date/time the to-do was created (optional)",
            AbstractDataProviderType::get(new Type("*date"))),
        "last_modified": new QoreDataField("last_modified",
            "The date/time the to-do was last modified (optional)",
            AbstractDataProviderType::get(new Type("*date"))),
        "sequence": new QoreDataField("sequence", "The revision sequence number (optional)",
            AbstractDataProviderType::get(SoftIntOrNothingType)),
        "alarms": new QoreDataField("alarms",
            "List of alarm hashes with 'action', 'trigger', 'description', 'duration', 'repeat' "
            "keys (optional)",
            AbstractDataProviderType::get(new Type("*list<hash<auto>>"))),
    };
}
} # IcsDataProvider namespace
