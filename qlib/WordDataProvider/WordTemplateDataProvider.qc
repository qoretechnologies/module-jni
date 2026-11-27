# -*- mode: qore; indent-tabs-mode: nil -*-
# Copyright 2026 Qore Technologies, s.r.o. MIT license.

public namespace WordDataProvider {
#! Fields shared by the Word template actions
public class WordTemplateRequestType inherits HashDataType {
    public {
        const SourceFields = {
            "template_data": {
                "display_name": "Template Data", "short_desc": "Original DOCX bytes",
                "type": AbstractDataProviderTypeMap."*base64binary",
                "desc": "A Word .docx template supplied as bytes. Provide exactly one of template_data and template_path.",
            },
            "template_path": {
                "display_name": "Template Location", "short_desc": "Location of the original DOCX",
                "type": StringOrNothingType,
                "desc": "A location readable through FileLocationHandler, as an alternative to template_data.",
            },
        };
        const FillFields = SourceFields + {
            "mappings": {
                "display_name": "Field Mappings", "short_desc": "Values keyed by Word control tag",
                "type": AutoHashType,
                "desc": "Map each scalar tag to single-line text, a number or a boolean. Map each repeating " +
                    "section tag to a list of records keyed by its scalar column tags.",
            },
            "path": {
                "display_name": "Output Location", "short_desc": "Optional destination for the rendered DOCX",
                "type": StringOrNothingType,
                "desc": "Write the completed DOCX to this FileLocationHandler location.",
            },
        };
    }
    constructor(bool fill) : HashDataType(AutoHashType, fill ? "WordTemplateFillRequest" : "WordTemplateFieldsRequest") {
        addQoreFields(fill ? FillFields : SourceFields);
    }
}

#! Discovers the required mappings of a DOCX template
public class WordTemplateFieldsDataProvider inherits AbstractDataProvider {
    public {
        const ProviderInfo = <DataProviderInfo>{
            "name": "inspect-template", "desc": "Discover required fields and row mappings in a Word template",
            "type": "WordTemplateFieldsDataProvider", "supports_request": True,
        };
        const RequestType = new WordTemplateRequestType(False);
        const ResponseType = AbstractDataProviderType::get(AutoHashType);
    }

    constructor(*hash<auto> options) {
        # Java document processing uses uncontrolled APIs.
        DataProviderActionCatalog::checkSandboxAccess(WordDataProvider::AppName);
    }

    string getName() { return ProviderInfo.name; }
    private hash<DataProviderInfo> getStaticInfoImpl() { return ProviderInfo; }
    private *AbstractDataProviderType getRequestTypeImpl() { return RequestType; }
    private *AbstractDataProviderType getResponseTypeImpl() { return ResponseType; }

    private WordTemplate readTemplate(hash<auto> req) {
        DataProviderActionCatalog::checkSandboxAccess(WordDataProvider::AppName);
        if (exists req.template_data == exists req.template_path) {
            throw "WORD-TEMPLATE-ERROR", "provide exactly one of template_data and template_path";
        }
        if (exists req.template_path) {
            # a location of the code that the provider may have been lent to (qore#7811)
            Priv::check_request_location(getName(), "template_path", req.template_path);
        }
        binary data = exists req.template_data ? req.template_data : FileLocationHandler::getBinaryFileFromLocation(req.template_path);
        try {
            return new WordTemplate(data);
        } catch (hash<ExceptionInfo> ex) {
            throw "WORD-TEMPLATE-ERROR", ex.desc;
        }
    }

    private hash<auto> doRequestImpl(auto req, *hash<auto> request_options) {
        return {"fields": readTemplate(req).getFields()};
    }
}

#! Fills the template and returns the generated document
/** Accepts transitional .docx documents with named inline plain-text controls containing one styled run and
    one text element. A row-level Word 2013 repeating section holds one repeating-section item containing one
    table row with named text controls. All tags are required. An empty list removes the prototype row.
    Use inspect-template for the tag names and row fields. Filled controls become ordinary text and rows.

    The action preserves static package parts, including headers, footers, styles and embedded images.
    Text expansion and additional rows can change line and page breaks. The action does not paginate the document.
    Macros, embedded active content, external relationships, Word fields, other control types, and controls outside
    the main document are unsupported. The Word provider's sandbox policy applies to both template actions.
*/
public class WordFillTemplateDataProvider inherits WordTemplateFieldsDataProvider {
    public {
        const FillProviderInfo = <DataProviderInfo>{
            "name": "fill-template", "desc": "Fill named text controls and repeating table rows in a Word template",
            "type": "WordFillTemplateDataProvider", "supports_request": True,
        };
        const FillRequestType = new WordTemplateRequestType(True);
    }

    constructor(*hash<auto> options) : WordTemplateFieldsDataProvider(options) {
        # Apply the Word app policy when constructing the fill action.
        DataProviderActionCatalog::checkSandboxAccess(WordDataProvider::AppName);
    }

    string getName() { return FillProviderInfo.name; }
    private hash<DataProviderInfo> getStaticInfoImpl() { return FillProviderInfo; }
    private *AbstractDataProviderType getRequestTypeImpl() { return FillRequestType; }
    private *AbstractDataProviderType getResponseTypeImpl() { return GeneratedDataOutputDataType; }

    private hash<auto> doRequestImpl(auto req, *hash<auto> request_options) {
        WordTemplate template = readTemplate(req);
        binary rendered;
        try {
            rendered = template.fill(req.mappings);
        } catch (hash<ExceptionInfo> ex) {
            throw "WORD-TEMPLATE-ERROR", ex.desc;
        }
        if (exists req.path) {
            # a location of the code that the provider may have been lent to (qore#7811)
            Priv::check_request_location("fill-template", "path", req.path);
            OutputStreamWrapper os = FileLocationHandler::getOutputStreamForLocation(req.path);
            on_exit os.waitForIo();
            on_exit os.close();
            os.write(rendered);
        }
        return {"data": rendered, "record_count": 1};
    }
}
}
