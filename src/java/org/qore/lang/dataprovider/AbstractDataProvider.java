/** Java wrapper for the %Qore AbstractDataProvider class
 *
 */
package org.qore.lang.dataprovider;

// java imports
import java.util.HashMap;
import java.util.Map;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;
import org.qore.jni.QoreJavaApi;

//! Java wrapper for the @ref DataProvider::AbstractDataProvider class in Qore
/** @note Loads and initializes the Qore library and the jni module in static initialization if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qoremod.DataProvider.AbstractDataProvider;</tt>
*/
@Deprecated
public class AbstractDataProvider extends QoreObjectWrapper {
    // static initialization
    static {
        // loads and initializes the Qore library and the jni module (if necessary) and loads the \c DataProvider
        // module
        try {
            QoreJavaApi.initQore();
            QoreJavaApi.callFunction("load_module", "DataProvider");
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    //! creates the object as a wrapper for the Qore object
    public AbstractDataProvider(QoreObject obj) throws Throwable {
        super(obj);
    }

    //! Returns static provider information as data; no objects are returned
    /** @note the \c name and \c children attributes are not returned as they are dynamic attributes
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getInfoAsData() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getInfoAsData");
    }

    //! Returns data provider info
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getInfo() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getInfo");
    }

    //! Creates the given record in the data provider
    /** @param rec a hash representing a single input record
        @param create_options the create options; will be processed by validateCreateOptions()

        @return the data written to the data provider

        @throw INVALID-OPERATION the data provider does not support record creation
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> createRecord(Map<String, Object> rec, Map<String, Object> create_options)
        throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("createRecord", rec, create_options);
    }

    //! Creates the given record in the data provider
    /** @param rec a hash representing a single input record

        @return the data written to the data provider

        @throw INVALID-OPERATION the data provider does not support record creation
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> createRecord(Map<String, Object> rec) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("createRecord", rec);
    }

    //! Upserts the given record in the data provider
    /** @param rec a hash representing a single input record
        @param upsert_options the upsert options; will be processed by validateUpsertOptions()

        @return see @ref db_provider_upsert_results for possible values

        @throw INVALID-OPERATION the data provider does not support upsert operations
    */
    public String upsertRecord(Map<String, Object> rec, Map<String, Object> upsert_options) throws Throwable {
        return (String)obj.callMethod("upsertRecord", rec, upsert_options);
    }

    //! Upserts the given record in the data provider
    /** @param rec a hash representing a single input record

        @return see @ref db_provider_upsert_results for possible values

        @throw INVALID-OPERATION the data provider does not support upsert operations
    */
    public String upsertRecord(Map<String, Object> rec) throws Throwable {
        return (String)obj.callMethod("upsertRecord", rec);
    }

    //! Returns the first record matching the search options
    /** @param where_cond the search criteria; will be processed by processFieldValues()
        @param search_options the search options; will be processed by validateSearchOptions()

        @throw INVALID-OPERATION the data provider does not support reading
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> searchFirstRecord(Map<String, Object> where_cond,
        HashMap<String, Object> search_options) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("searchFirstRecord", where_cond, search_options);
    }

    //! Returns the first record matching the search options
    /** @param where_cond the search criteria; will be processed by processFieldValues()

        @throw INVALID-OPERATION the data provider does not support reading
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> searchFirstRecord(Map<String, Object> where_cond) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("searchFirstRecord", where_cond);
    }

    //! Returns a single record matching the search options
    /** @param where_cond the search criteria; will be processed by processFieldValues()
        @param search_options the search options; will be processed by validateSearchOptions()

        @throw INVALID-OPERATION the data provider does not support reading
        @throw MULTIPLE-RECORDS-ERROR multiple records found
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> searchSingleRecord(Map<String, Object> where_cond,
        HashMap<String, Object> search_options) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("searchSingleRecord", where_cond, search_options);
    }

    //! Returns a single record matching the search options
    /** @param where_cond the search criteria; will be processed by processFieldValues()

        @throw INVALID-OPERATION the data provider does not support reading
        @throw MULTIPLE-RECORDS-ERROR multiple records found
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> searchSingleRecord(Map<String, Object> where_cond) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("searchSingleRecord", where_cond);
    }

    //! Returns an iterator iterating all records
    /** @param search_options the search options; will be processed by validateSearchOptions()

        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderRecordIterator getRecordIterator(Map<String, Object> search_options)
        throws Throwable {
        return new AbstractDataProviderRecordIterator((QoreObject)obj.callMethodSave("getRecordIterator",
            search_options));
    }

    //! Returns an iterator iterating all records
    /**
        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderRecordIterator getRecordIterator()
        throws Throwable {
        return new AbstractDataProviderRecordIterator((QoreObject)obj.callMethodSave("getRecordIterator"));
    }

    //! Returns an iterator iterating all records with the bulk read API
    /** @param block_size the number of records in a read block; must be a positive number
        @param search_options the search options; will be processed by validateSearchOptions()

        @return a bulk record interface object that will return the records in bulk format

        @throw INVALID-BLOCK-SIZE the block size must be a positive number
        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderBulkRecordInterface getBulkRecordInterface(int block_size,
        HashMap<String, Object> search_options) throws Throwable {
        return new AbstractDataProviderBulkRecordInterface((QoreObject)obj.callMethodSave("getBulkRecordInterface",
            block_size, search_options));
    }

    //! Returns an iterator iterating all records with the bulk read API
    /** @param search_options the search options; will be processed by validateSearchOptions()

        @return a bulk record interface object that will return the records in bulk format

        @throw INVALID-BLOCK-SIZE the block size must be a positive number
        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderBulkRecordInterface getBulkRecordInterface(Map<String, Object> search_options) throws Throwable {
        return new AbstractDataProviderBulkRecordInterface((QoreObject)obj.callMethodSave("getBulkRecordInterface",
            1000, search_options));
    }

    //! Returns an iterator iterating all records with the bulk read API
    /** @return a bulk record interface object that will return the records in bulk format

        @throw INVALID-BLOCK-SIZE the block size must be a positive number
        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderBulkRecordInterface getBulkRecordInterface() throws Throwable {
        return new AbstractDataProviderBulkRecordInterface((QoreObject)obj.callMethodSave("getBulkRecordInterface",
            1000));
    }

    //! Returns an iterator for zero or more records matching the search options
    /** @param block_size the number of records in a read block; must be a positive number
        @param where_cond the search criteria; will be processed by processFieldValues()
        @param search_options the search options; will be processed by validateSearchOptions()

        @return a bulk record interface object that will return the records in bulk format

        @throw INVALID-BLOCK-SIZE the block size must be a positive number
        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderBulkRecordInterface searchRecordsBulk(int block_size,
        HashMap<String, Object> where_cond, Map<String, Object> search_options) throws Throwable {
        return new AbstractDataProviderBulkRecordInterface((QoreObject)obj.callMethodSave("searchRecordsBulk",
            block_size, where_cond, search_options));
    }

    //! Returns an iterator for zero or more records matching the search options
    /**
        @param where_cond the search criteria; will be processed by processFieldValues()
        @param search_options the search options; will be processed by validateSearchOptions()

        @return a bulk record interface object that will return the records in bulk format

        @throw INVALID-BLOCK-SIZE the block size must be a positive number
        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderBulkRecordInterface searchRecordsBulk(Map<String, Object> where_cond,
        HashMap<String, Object> search_options) throws Throwable {
        return new AbstractDataProviderBulkRecordInterface((QoreObject)obj.callMethodSave("searchRecordsBulk",
            1000, where_cond, search_options));
    }

    //! Returns an iterator for zero or more records matching the search options
    /**
        @param where_cond the search criteria; will be processed by processFieldValues()

        @return a bulk record interface object that will return the records in bulk format

        @throw INVALID-BLOCK-SIZE the block size must be a positive number
        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderBulkRecordInterface searchRecordsBulk(Map<String, Object> where_cond) throws Throwable {
        return new AbstractDataProviderBulkRecordInterface((QoreObject)obj.callMethodSave("searchRecordsBulk",
            1000, where_cond));
    }

    //! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria; will be processed by processFieldValues()
        @param search_options the search options; will be processed by validateSearchOptions()

        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderRecordIterator searchRecords(Map<String, Object> where_cond,
        HashMap<String, Object> search_options) throws Throwable {
        return new AbstractDataProviderRecordIterator((QoreObject)obj.callMethodSave("searchRecords", where_cond, search_options));
    }

    //! Returns an iterator for zero or more records matching the search options
    /** @param where_cond the search criteria; will be processed by processFieldValues()

        @throw INVALID-OPERATION the data provider does not support reading
    */
    public AbstractDataProviderRecordIterator searchRecords(Map<String, Object> where_cond) throws Throwable {
        return new AbstractDataProviderRecordIterator((QoreObject)obj.callMethodSave("searchRecords", where_cond));
    }

    //! Returns an iterator for zero or more records matching the search options according to an API request
    /** @param req the request to serialize and make according to the request type
        @param where_cond the search criteria; will be processed by processFieldValues()
        @param search_options the search options after processing by validateSearchOptions()

        This will execute the request and perform a default search on any record(s) returned

        @throw INVALID-OPERATION the data provider does not support reading records or the request / response API
    */
    public AbstractDataProviderRecordIterator requestSearchRecords(Object req, Map<String, Object> where_cond,
        HashMap<String, Object> search_options) throws Throwable {
        return new AbstractDataProviderRecordIterator((QoreObject)obj.callMethodSave("requestSearchRecords", req,
            where_cond, search_options));
    }

    //! Returns an iterator for zero or more records matching the search options according to an API request
    /** @param req the request to serialize and make according to the request type
        @param where_cond the search criteria; will be processed by processFieldValues()

        This will execute the request and perform a default search on any record(s) returned

        @throw INVALID-OPERATION the data provider does not support reading records or the request / response API
    */
    public AbstractDataProviderRecordIterator requestSearchRecords(Object req, Map<String, Object> where_cond)
        throws Throwable {
        return new AbstractDataProviderRecordIterator((QoreObject)obj.callMethodSave("requestSearchRecords", req,
            where_cond));
    }

    //! Updates a single record matching the search options
    /** @param set the hash of field data to set; will be processed by processFieldValues()
        @param where_cond the search criteria; will be processed by processFieldValues()
        @param search_options the search options; will be processed by validateSearchOptions()

        @returns @ref True if the record was updated, @ref False if not (no record found)

        @throw INVALID-OPERATION the data provider does not support record updating
    */
    public boolean updateSingleRecord(Map<String, Object> set, Map<String, Object> where_cond,
        HashMap<String, Object> search_options) throws Throwable {
        return (boolean)obj.callMethod("updateSingleRecord", set, where_cond, search_options);
    }

    //! Updates a single record matching the search options
    /** @param set the hash of field data to set; will be processed by processFieldValues()
        @param where_cond the search criteria; will be processed by processFieldValues()

        @returns @ref True if the record was updated, @ref False if not (no record found)

        @throw INVALID-OPERATION the data provider does not support record updating
    */
    public boolean updateSingleRecord(Map<String, Object> set, Map<String, Object> where_cond) throws Throwable {
        return (boolean)obj.callMethod("updateSingleRecord", set, where_cond);
    }

    //! Updates zero or more records matching the search options
    /** @param set the hash of field data to set
        @param where_cond a hash for identifying the record(s) to be updated
        @param search_options the search options; will be processed by validateSearchOptions()

        @return the number of records updated

        @throw INVALID-OPERATION the data provider does not support record updating
    */
    public int updateRecords(Map<String, Object> set, Map<String, Object> where_cond,
        HashMap<String, Object> search_options) throws Throwable {
        return ((Long)obj.callMethod("updateRecords", set, where_cond, search_options)).intValue();
    }

    //! Updates zero or more records matching the search options
    /** @param set the hash of field data to set
        @param where_cond a hash for identifying the record(s) to be updated

        @return the number of records updated

        @throw INVALID-OPERATION the data provider does not support record updating
    */
    public int updateRecords(Map<String, Object> set, Map<String, Object> where_cond) throws Throwable {
        return ((Long)obj.callMethod("updateRecords", set, where_cond)).intValue();
    }

    //! Deletes zero or more records
    /**
        @param where_cond a hash for identifying the record(s) to be deleted
        @param search_options the search options; will be processed by validateSearchOptions()

        @return the number of records deleted

        @throw INVALID-OPERATION the data provider does not support record deletion
    */
    public int deleteRecords(Map<String, Object> where_cond, Map<String, Object> search_options)
        throws Throwable {
        return ((Long)obj.callMethod("deleteRecords", where_cond, search_options)).intValue();
    }

    //! Deletes zero or more records
    /**
        @param where_cond a hash for identifying the record(s) to be deleted

        @return the number of records deleted

        @throw INVALID-OPERATION the data provider does not support record deletion
    */
    public int deleteRecords(Map<String, Object> where_cond)
        throws Throwable {
        return ((Long)obj.callMethod("deleteRecords", where_cond)).intValue();
    }

    //! Makes a request and returns the response
    /** @param req the request to serialize and make according to the request type
        @param request_options the request options; will be processed by validateRequestOptions()

        @return the response to the request

        @throw INVALID-OPERATION the data provider does not support the request API
    */
    public Object doRequest(Object req, Map<String, Object> request_options) throws Throwable {
        return obj.callMethod("doRequest", req, request_options);
    }

    //! Makes a request and returns the response
    /** @param req the request to serialize and make according to the request type

        @return the response to the request

        @throw INVALID-OPERATION the data provider does not support the request API
    */
    public Object doRequest(Object req) throws Throwable {
        return obj.callMethod("doRequest", req);
    }

    //! Returns the description of a successful request message, if any
    /** @return the request type for this provider

        @throw INVALID-OPERATION the data provider does not support the request API
    */
    public AbstractDataProviderType getRequestType() throws Throwable {
        return new AbstractDataProviderType((QoreObject)obj.callMethodSave("getRequestType"));
    }

    //! Returns the description of a response message, if this object represents a response message
    /** @return the response type for this response message

        @throw INVALID-OPERATION the data provider does not support the request API
    */
    public AbstractDataProviderType getResponseType() throws Throwable {
        return new AbstractDataProviderType((QoreObject)obj.callMethodSave("getResponseType"));
    }

    //! Returns a hash of error responses, if any
    /** @return a hash of error responses, if any, keyed by response code or String

        @throw INVALID-OPERATION the data provider does not support the request API
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, AbstractDataProviderType> getErrorResponseTypes() throws Throwable {
        return (HashMap<String, AbstractDataProviderType>)obj.callMethod("getErrorResponseTypes");
    }

    //! Returns the type for the given error code
    /** @param error_code the error code for the response; must be a known error code, or an \c UNKNOWN-ERROR-RESPONSE
        exception is thrown
        @return the type for the given error code

        @throw INVALID-OPERATION the data provider does not support the request API
        @throw UNKNOWN-ERROR-RESPONSE the error response given is not known
    */
    public AbstractDataProviderType getErrorResponseType(String error_code) throws Throwable {
        return new AbstractDataProviderType((QoreObject)obj.callMethodSave("getErrorResponseType", error_code));
    }

    //! Returns a list of child data provider names, if any
    /** @return a list of child data provider names, if any
    */
    public String[] getChildProviderNames() throws Throwable {
        return (String[])obj.callMethod("getChildProviderNames");
    }

    //! Returns the given child provider or @ref nothing if the given child is unknown
    /** @return the given child provider or @ref nothing if the given child is unknown

        @throw INVALID-CHILD-PROVIDER unknown child provider

        @see getChildProviderEx()
    */
    public AbstractDataProvider getChildProvider(String name) throws Throwable {
        return (AbstractDataProvider)obj.callMethod("getChildProvider", name);
    }

    //! Returns the given child provider or throws an exception if the given child is unknown
    /** @return the given child provider or throws an exception if the given child is unknown

        @throw INVALID-CHILD-PROVIDER unknown child provider

        @see getChildProvider()
    */
    public AbstractDataProvider getChildProviderEx(String name) throws Throwable {
        return (AbstractDataProvider)obj.callMethod("getChildProviderEx", name);
    }

    //! Returns the given child provider from a \c "/" separated path String; throws an exception if any element in the path is unknown
    /** @param path a String giving a path to the target provider where child elements are separated by \c "/" characters

        @return the target child provider; throws an exception if any element in the path is unknown

        @throw INVALID-CHILD-PROVIDER unknown child provider
    */
    public AbstractDataProvider getChildProviderPath(String path) throws Throwable {
        return (AbstractDataProvider)obj.callMethod("getChildProviderPath", path);
    }

    //! Returns @ref True if the data provider supports transaction management
    /** @return @ref True if the data provider supports transaction management, in which case commit() or rollback()
        must be called to flush or discard data written to the data provider
    */
    public boolean requiresTransactionManagement() throws Throwable {
        return (boolean)obj.callMethod("requiresTransactionManagement");
    }

    //! Commits data written to the data provider
    /** Has no effect if the data provider does not support transaction management
    */
    public void commit() throws Throwable {
        obj.callMethod("commit");
    }

    //! Rolls back data written to the data provider
    /** Has no effect if the data provider does not support transaction management
    */
    public void rollback() throws Throwable {
        obj.callMethod("rollback");
    }

    //! Returns a bulk insert operation object for the data provider
    /** @return a bulk insert operation object for the data provider

        @throw INVALID-OPERATION the data provider does not support create operations
    */
    public AbstractDataProviderBulkOperation getBulkInserter() throws Throwable {
        return new AbstractDataProviderBulkOperation((QoreObject)obj.callMethodSave("getBulkInserter"));
    }

    //! Returns a bulk upsert operation object for the data provider
    /** @return a bulk upsert operation object for the data provider

        @throw INVALID-OPERATION the data provider does not support upsert operations
    */
    public AbstractDataProviderBulkOperation getBulkUpserter() throws Throwable {
        return new AbstractDataProviderBulkOperation((QoreObject)obj.callMethodSave("getBulkUpserter"));
    }

    //! Returns custom data mapper runtime keys
    /** @return custom data mapper runtime keys

        This base method returns @ref nothing; reimplment in child classes to return a value
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, HashMap<String, Object>> getMapperRuntimeKeys() throws Throwable {
        return (HashMap<String, HashMap<String, Object>>)obj.callMethod("getMapperRuntimeKeys");
    }

    //! Returns the description of the record type, if any
    /**
        @param search_options to be included and processed by validateSearchOptions() if
        \a recordRequiresSearchOptions() is true for this provider, otherwise any value provided in this argument
        is ignored

        @return the description of the record type, if any

        @throws Throwable any exception thrown by the data provider
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, AbstractDataField> getRecordType(Map<String, Object> search_options) throws Throwable {
        return (HashMap<String, AbstractDataField>)obj.callMethod("getRecordType", search_options);
    }

    //! Returns the description of the record type, if any
    @SuppressWarnings("unchecked")
    public HashMap<String, AbstractDataField> getRecordType() throws Throwable {
        return (HashMap<String, AbstractDataField>)obj.callMethod("getRecordType");
    }

    //! Returns the description of the record type with soft types, if any
    /**
        @param search_options to be included and processed by validateSearchOptions() if
        \a recordRequiresSearchOptions() is true for this provider, otherwise any value provided in this argument
        is ignored

        @return the description of the record type with soft types, if any

        @throws Throwable any exception thrown by the data provider
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, AbstractDataField> getSoftRecordType(Map<String, Object> search_options) throws Throwable {
        return (HashMap<String, AbstractDataField>)obj.callMethod("getSoftRecordType", search_options);
    }

    //! Returns the description of the record type with soft types, if any
    @SuppressWarnings("unchecked")
    public HashMap<String, AbstractDataField> getSoftRecordType() throws Throwable {
        return (HashMap<String, AbstractDataField>)obj.callMethod("getSoftRecordType");
    }

    //! Returns options that can be used for searching
    /** @return a hash of options that can be used for searching; keys are search option names, values describe the
        search option; if @ref nothing is returned, then the provider does not support any search options
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, HashMap<String, Object>> getSearchOptions() throws Throwable {
        return (HashMap<String, HashMap<String, Object>>)obj.callMethod("getSearchOptions");
    }

    //! Returns options that can be used for creating records
    /** @return a hash of options that can be used for creating records; keys are option names, values describe the
        option; if @ref nothing is returned, then the provider does not support any creation options
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, HashMap<String, Object>> getCreateOptions() throws Throwable {
        return (HashMap<String, HashMap<String, Object>>)obj.callMethod("getCreateOptions");
    }

    //! Returns options that can be used for upserting records
    /** @return a hash of options that can be used for upserting records; keys are option names, values describe the
        option; if @ref nothing is returned, then the provider does not support any upsert options
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, HashMap<String, Object>> getUpsertOptions() throws Throwable {
        return (HashMap<String, HashMap<String, Object>>)obj.callMethod("getUpsertOptions");
    }

    //! Returns options that can be used for requests
    /** @return a hash of options that can be used for requests; keys are request option names, values describe the
        request option; if @ref nothing is returned, then the provider does not support any request options
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, HashMap<String, Object>> getRequestOptions() throws Throwable {
        return (HashMap<String, HashMap<String, Object>>)obj.callMethod("getRequestOptions");
    }

    //! Returns @ref True if the data provider supports reading
    /**
    */
    public boolean supportsRead() throws Throwable {
        return (boolean)obj.callMethod("supportsRead");
    }

    //! Returns @ref True if the data provider supports native bulk reading
    /**
    */
    public boolean supportsBulkRead() throws Throwable {
        return (boolean)obj.callMethod("supportsBulkRead");
    }

    //! Returns @ref True if the data provider supports the record creation API
    /**
    */
    public boolean supportsCreate() throws Throwable {
        return (boolean)obj.callMethod("supportsCreate");
    }

    //! Returns @ref True if the data provider supports the record update API
    /**
    */
    public boolean supportsUpdate() throws Throwable {
        return (boolean)obj.callMethod("supportsUpdate");
    }

    //! Returns @ref True if the data provider supports the record upsert API
    /**
    */
    public boolean supportsUpsert() throws Throwable {
        return (boolean)obj.callMethod("supportsUpsert");
    }

    //! Returns @ref True if the data provider supports the record deletion API
    /**
    */
    public boolean supportsDelete() throws Throwable {
        return (boolean)obj.callMethod("supportsDelete");
    }

    //! Returns @ref True if the data provider supports the record search API natively
    /**
    */
    public boolean supportsNativeSearch() throws Throwable {
        return (boolean)obj.callMethod("supportsNativeSearch");
    }

    //! Returns True if the data provider supports bulk creation output
    /**
    */
    public boolean supportsBulkCreate() throws Throwable {
        return (boolean)obj.callMethod("supportsBulkCreate");
    }

    //! Returns True if the data provider supports bulk upserts
    /**
    */
    public boolean supportsBulkUpsert() throws Throwable {
        return (boolean)obj.callMethod("supportsBulkUpsert");
    }

    //! Returns True if the data provider supports requests
    /**
    */
    public boolean supportsRequest() throws Throwable {
        return (boolean)obj.callMethod("supportsRequest");
    }

    //! Returns true if the data provider requires search options to retrieve the record type
    /**
    */
    public boolean recordRequiresSearchOptions() throws Throwable {
        return (boolean)obj.callMethod("recordRequiresSearchOptions");
    }

    //! Returns true if the data provider has a record type
    /** @return true if the data provider has a record type
    */
    public boolean hasRecord() throws Throwable {
        return (boolean)obj.callMethod("hasRecord");
    }
}
