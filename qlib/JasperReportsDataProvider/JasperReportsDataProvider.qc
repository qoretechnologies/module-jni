# -*- mode: qore; indent-tabs-mode: nil -*-
# Qore JasperReportsDataProvider class definition

/*  JasperReportsDataProvider.qc Copyright 2026 Qore Technologies, s.r.o.

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

#! the JasperReportsDataProvider namespace. All classes used in the JasperReportsDataProvider module should be inside this namespace
public namespace JasperReportsDataProvider {
#! Provides a data provider for generating reports from JasperReports templates
public class JasperReportsDataProvider inherits DataProvider::AbstractDataProvider {
    public {
        #! Provider info
        const ProviderInfo = <DataProviderInfo>{
            "type": "JasperReportsDataProvider",
            "supports_create": True,
            "has_record": True,
            "constructor_options": ConstructorOptions,
        };

        #! Constructor options
        const ConstructorOptions = {
            "template": <DataProviderOptionInfo>{
                "display_name": "Template Path",
                "short_desc": "The path to the JasperReports template file",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The path to the JasperReports template file (.jrxml or .jasper); mutually "
                    "exclusive with `template_data`; the input location is handled by a call to "
                    "[FileLocationHandler::getBinaryFileFromLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a1c17d58d4caa453b7dbe18b850ea1e6)",
            },
            "template_data": <DataProviderOptionInfo>{
                "display_name": "Template Data",
                "short_desc": "Inline binary template data",
                "type": AbstractDataProviderType::get(BinaryType),
                "desc": "Inline binary JasperReports template data (.jrxml or .jasper); mutually "
                    "exclusive with `template`",
            },
            "output_format": <DataProviderOptionInfo>{
                "display_name": "Output Format",
                "short_desc": "The report output format",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The output format for the generated report; one of `pdf`, `xlsx`, `html`, "
                    "or `csv`",
                "required": True,
                "allowed_values": (
                    <AllowedValueInfo>{"value": "pdf", "display_name": "PDF",
                        "short_desc": "PDF format",
                        "desc": "Portable Document Format (PDF) using OpenPDF"},
                    <AllowedValueInfo>{"value": "xlsx", "display_name": "XLSX",
                        "short_desc": "Excel 2007+ format (.xlsx)",
                        "desc": "Excel 2007+ format (`.xlsx`) using Apache POI"},
                    <AllowedValueInfo>{"value": "html", "display_name": "HTML",
                        "short_desc": "HTML format",
                        "desc": "HTML (HyperText Markup Language) format"},
                    <AllowedValueInfo>{"value": "csv", "display_name": "CSV",
                        "short_desc": "CSV format",
                        "desc": "CSV (comma-separated values) format"},
                ),
            },
            "output_path": <DataProviderOptionInfo>{
                "display_name": "Output Path",
                "short_desc": "The path to write the generated report",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "The path to write the generated report output; if not provided, use "
                    "`getData()` to retrieve the binary report data; the output location is handled "
                    "by a call to "
                    "[FileLocationHandler::getOutputStreamForLocation()](https://qoretechnologies.com/"
                    "manual/qorus/current/qore/modules/FileLocationHandler/html/"
                    "class_file_location_handler_1_1_file_location_handler.html"
                    "#a46f7e9cc0e76e0d7e30cae79513cd35)",
            },
            "schema": <DataProviderOptionInfo>{
                "display_name": "Avro Schema",
                "short_desc": "Optional Avro JSON schema for the data source",
                "type": AbstractDataProviderType::get(StringType),
                "desc": "Optional Avro JSON schema string for the JasperReports data source; if "
                    "provided, the data will be serialized using the Avro schema before being "
                    "passed to the report generator",
            },
        };
    }

    private {
        #! The JasperReports generator object
        JasperReportGenerator generator;

        #! The output format
        string outputFormat;

        #! The output path
        *string outputPath;

        #! The name of the object
        string name;

        #! Records created count
        int created = 0;
    }

    #! Creates the JasperReportsDataProvider with a template path and output format
    /** @param templatePath the path to the JasperReports template file (.jrxml or .jasper)
        @param outputFormat the output format (pdf, xlsx, html, csv)
        @param opts a hash of optional options

        @throw JASPERREPORTS-OPTION-ERROR invalid or unknown option; conflicting options
    */
    constructor(string templatePath, string outputFormat, *hash<auto> opts) {
        *hash<auto> copts = checkOptions("JASPERREPORTS-OPTION-ERROR", ConstructorOptions, opts);
        if (copts.template) {
            error("JASPERREPORTS-OPTION-ERROR", "cannot provide \"template\" option when template path "
                "is given as first argument");
        }
        if (copts.template_data) {
            error("JASPERREPORTS-OPTION-ERROR", "cannot provide \"template_data\" option when template "
                "path is given as first argument");
        }
        if (copts.output_format) {
            error("JASPERREPORTS-OPTION-ERROR", "cannot provide \"output_format\" option when output "
                "format is given as second argument");
        }
        self.outputFormat = outputFormat;
        outputPath = copts.output_path;
        name = templatePath;
        binary templateData = FileLocationHandler::getBinaryFileFromLocation(templatePath);
        setupGenerator(templateData);
    }

    #! Creates the object from constructor options
    /**
        @throw JASPERREPORTS-OPTION-ERROR invalid or unknown option; missing required options

        @note If \c output_path is not provided, use getData() to retrieve the report data as binary
    */
    constructor(*hash<auto> options) {
        *hash<auto> copts = checkOptions("JASPERREPORTS-OPTION-ERROR", ConstructorOptions, options);
        if (!copts.output_format) {
            error("JASPERREPORTS-OPTION-ERROR", "the \"output_format\" option is required");
        }
        outputFormat = copts.output_format;
        outputPath = copts.output_path;

        binary templateData;
        if (copts.template) {
            if (copts.template_data) {
                error("JASPERREPORTS-OPTION-ERROR", "cannot provide both \"template\" and "
                    "\"template_data\" options; use either one or the other");
            }
            name = copts.template;
            templateData = FileLocationHandler::getBinaryFileFromLocation(copts.template);
        } else if (copts.template_data) {
            name = "jasperreports-data";
            templateData = copts.template_data;
        } else {
            error("JASPERREPORTS-OPTION-ERROR", "either \"template\" or \"template_data\" option is "
                "required");
        }
        setupGenerator(templateData);
    }

    #! Sets up the JasperReports generator from template binary data
    private setupGenerator(binary templateData) {
        generator = new JasperReportGenerator(templateData);
    }

    #! Returns the object name
    string getName() {
        return name;
    }

    #! Destructor - ensures report is generated
    destructor() {
        commit();
    }

    #! Creates a single record (data row) for the report
    /** @param rec the data record to add to the report
        @param create_options no options are currently supported

        @return the record created (echoes back the input)
    */
    private *hash<auto> createRecordImpl(hash<auto> rec, *hash<auto> create_options) {
        generator.addRecord(rec);
        ++created;
        return rec;
    }

    #! Commits (generates) the report and writes to output_path if provided
    /** Call this method when you are done adding records to generate the report.
        This is also called automatically in the destructor.
    */
    commit() {
        if (!generator) {
            return;
        }
        on_exit {
            delete generator;
        }
        if (outputPath) {
            auto data = generator.generate(outputFormat);
            OutputStreamWrapper os = FileLocationHandler::getOutputStreamForLocation(outputPath);
            on_exit {
                os.close();
                os.waitForIo();
            }
            os.write(data);
        }
    }

    #! Returns the generated report as binary data
    /** @return the report contents as binary data in the configured output format

        @note This is an alternative to using output_path - get the data directly

        @throw JASPERREPORTS-GENERATE-ERROR generator has already been committed
    */
    auto getData() {
        if (!generator) {
            throw "JASPERREPORTS-GENERATE-ERROR", "generator has already been committed";
        }
        return generator.generate(outputFormat);
    }

    #! Returns the number of records created
    int getCreatedCount() {
        return created;
    }

    #! Returns the description of the record type, if any
    private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) {
        return {};
    }

    #! Returns data provider static info
    private hash<DataProviderInfo> getStaticInfoImpl() {
        return ProviderInfo;
    }
}
} # JasperReportsDataProvider namespace
