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
    }

    #! Creates the IcsReadDataProvider with the input data path and optionally an option hash
    constructor(string path, *hash<auto> opts) {
        i = FileLocationHandler::getBinaryStreamFromLocation(path);
        name = path;
        checkOptions("ICS-READ-OPTION-ERROR", ConstructorOptions, opts);
    }

    #! Creates the IcsReadDataProvider with the input data stream
    constructor(InputStream stream, *hash<auto> opts) {
        i = stream;
        checkOptions("ICS-READ-OPTION-ERROR", ConstructorOptions, opts);
        name = sprintf("ics-stream=%s", stream.uniqueHash());
    }

    #! Creates the object from constructor options
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("ICS-READ-OPTION-ERROR", ConstructorOptions, options);
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
        it = new IcsIterator(i);
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
    };
}
} # IcsDataProvider namespace
