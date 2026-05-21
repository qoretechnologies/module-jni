/** Java wrapper for the %Qore AbstractSQLStatement class
 *
 */
package org.qore.lang;

// java imports
import java.util.Map;
import java.util.HashMap;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;

//! Java wrapper for the @ref Qore::SQL::AbstractSQLStatement class in %Qore
/** @par Example:
        @code{.java}
    {
        AbstractSQLStatement stmt = ds.getSQLStatement();
        try {
            stmt.prepare("select * from table");
            stmt.exec();
            stmt.define();
            // note that the next() would implicitly execute exec() and define()
            while (stmt.next()) {
                Map<String, Object> row = stmt.fetchRow();
                process(row);
            }
            // release transaction lock on exit
            stmt.commit();
        } catch (Throwable e) {
            stmt.rollback();
            throw e;
        } finally {
            stmt.release();
        }
    }
        @endcode

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qore.Qore.SQL.AbstractSQLStatement;</tt>
*/
@Deprecated
public class AbstractSQLStatement extends QoreObjectWrapper {
    //! creates the object
    public AbstractSQLStatement(QoreObject ds) {
        super(ds);
    }

    //! Saves an SQL statement that will be prepared and executed later, along with optional arguments
    /** The statement is actually only prepared when used for the first time, this is so that SQLStatement objects created with DatasourcePool objects use the DatasourcePool more efficiently, as many drivers require the actual DB API prepare call to be made on the same connection as the connection the statement will be executed on as well.

        @note This method parses the SQL string for placeholders and bind by value tokens (<tt>%%v</tt>); for a version of this method that does not parse the SQL string for placeholders and bind by value tokens, see prepareRaw().

        @param sql The SQL string to prepare for execution on the DB server
        @param args The arguments for any bind placeholders

        @par Example:
        @code{.java}
    stmt.prepare("select * from table where id = %v");
        @endcode
    */
    public void prepare(String sql, Object... args) throws Throwable {
        Object[] new_args = new Object[args.length + 1];
        new_args[0] = sql;
        System.arraycopy(args, 0, new_args, 1, args.length);
        obj.callMethodArgs("prepare", new_args);
    }

    //! Saves an SQL statement that will be prepared and executed later
    /** The statement is actually only prepared when used for the first time, this is so that SQLStatement objects created with DatasourcePool objects use the DatasourcePool more efficiently, as many drivers require the actual DB API prepare call to be made on the same connection as the connection the statement will be executed on as well.

        @note This method does not parse the SQL string for placeholders and bind by value tokens (<tt>%%v</tt>); for a version of this method that does parse the SQL string for placeholders and bind by value tokens, see prepare().

        @param sql The SQL string to prepare for execution on the DB server

        @par Example:
        @code{.java}
    stmt.prepareRaw("select * from table");
        @endcode
    */
    public void prepareRaw(String sql) throws Throwable {
        obj.callMethod("prepareRaw", sql);
    }

    //! Binds placeholder buffer specifications and values to buffers defined in prepare()
    /** If the statement has not previously been prepared with the DB API, it will be implicitly prepared by this method call. This means that this call will cause a connection to be dedicated from a DatasourcePool object or the transaction lock to be grabbed with a Datasource object, depending on the argument to constructor.

        Arguments to buffer specifications must be given in the same order as declared in the string given to the prepare() method.

        Any arguments previously bound will be released when this call is made.

        @note You can also bind directly when calling exec() or execArgs() as a shortcut as well, in which case it's not necessary to make an extra call to this method.

        @param args Arguments to placeholder specifications (if required by the underlying DBI driver) and bind by value arguments

        @par Example:
        @code{.java}
    stmt.prepare("insert into table (id, name) values (%v, %v)");
    stmt.bind(h.get("id"), h.get("name"));
    stmt.exec();
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications; see the relevant DBI driver docs for more information

        @see bindArgs(), bindPlaceholders(), bindPlaceholdersArgs(), bindValues(), and bindValuesArgs()
    */
    public void bind(Object... args) throws Throwable {
        obj.callMethodArgs("bind", args);
    }

    //! Binds placeholder buffer specifications and values given as a list in the single argument to the method to buffers defined in prepare()
    /** If the statement has not previously been prepared with the DB API, it will be implicitly prepared by this method call. This means that this call will cause a connection to be dedicated from a DatasourcePool object or the transaction lock to be grabbed with a Datasource object, depending on the argument to constructor.

        Arguments to buffer specifications must be given in the same order as declared in the string given to the prepare() method.

        Any arguments previously bound will be released when this call is made.

        @note You can also bind directly when calling exec() or execArgs() as a shortcut as well, in which case it's not necessary to make an extra call to this method.

        @param vargs Arguments to placeholder specifications (if required by the underlying DBI driver) and bind by value arguments

        @par Example:
        @code{.java}
    stmt.prepare("insert into table (id, name) values (%v, %v)");
    stmt.bindArgs(args);
    stmt.exec();
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications; see the relevant DBI driver docs for more information

        @see bind(), bindPlaceholders(), bindPlaceholdersArgs(), bindValues(), and bindValuesArgs()
    */
    public void bindArgs(Object[] vargs) throws Throwable {
        obj.callMethod("bindArgs", vargs);
    }

    //! Binds placeholder buffer specifications to buffers defined in prepare()
    /** If the statement has not previously been prepared with the DB API, it will be implicitly prepared by this method call. This means that this call will cause a connection to be dedicated from a DatasourcePool object or the transaction lock to be grabbed with a Datasource object, depending on the argument to constructor.

        Arguments to buffer specifications must be given in the same order as declared in the string given to the prepare() method. Only placeholder buffer specifications will be processed; value buffer specifications will be skipped by this method.

        Any buffer specifications previously defined will be released when this call is made.

        @note You can also bind buffer specifications directly when calling exec() or execArgs() as a shortcut as well, in which case it's not necessary to make an extra call to this method.\n\n
        Not all DBI drivers require binding placeholders specification.

        @param args Arguments to placeholder specifications (if required by the underlying DBI driver)

        @par Example:
        @code{.java}
    stmt.prepare("begin select sysdate into :sd from dual", "date"); end;
    stmt.bindPlaceholders(Type::Date);
    ZonedDateTime d = stmt.getOutput().get("sd");
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications; see the relevant DBI driver docs for more information

        @see bind(), bindArgs(), bindPlaceholdersArgs(), bindValues(), and bindValuesArgs()
    */
    public void bindPlaceholders(Object... args) throws Throwable {
        obj.callMethodArgs("bindPlaceholders", args);
    }

    //! Binds placeholder buffer specifications given as a list in the single argument to the method to buffers defined in prepare()
    /** If the statement has not previously been prepared with the DB API, it will be implicitly prepared by this method call. This means that this call will cause a connection to be dedicated from a DatasourcePool object or the transaction lock to be grabbed with a Datasource object, depending on the argument to constructor.

        Arguments to buffer specifications must be given in the same order as declared in the string given to the prepare() method. Only placeholder buffer specifications will be processed; value buffer specifications will be skipped by this method.

        Any buffer specifications previously defined will be released when this call is made.

        @note You can also bind buffer specifications directly when calling exec() or execArgs() as a shortcut as well, in which case it's not necessary to make an extra call to this method.\n\n
        Not all DBI drivers require binding placeholders specification.

        @param vargs Arguments to placeholder specifications (if required by the underlying DBI driver)

        @par Example:
        @code{.java}
    stmt.prepare("begin select sysdate into :sd from dual", "date"); end;
    stmt.bindPlaceholdersArgs(args);
    ZonedDateTime d = stmt.getOutput().get("sd");
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications; see the relevant DBI driver docs for more information

        @see bind(), bindArgs(), bindPlaceholders(), bindValues(), and bindValuesArgs()
    */
    public void bindPlaceholdersArgs(Object[] vargs) throws Throwable {
        obj.callMethod("bindPlaceholdersArgs", vargs);
    }

    //! Binds values to value buffer specifications to buffers defined in prepare()
    /** If the statement has not previously been prepared with the DB API, it will be implicitly prepared by this method call. This means that this call will cause a connection to be dedicated from a DatasourcePool object or the transaction lock to be grabbed with a Datasource object, depending on the argument to constructor.

        Arguments to buffer specifications must be given in the same order as declared in the string given to the prepare() method.

        Any values previously bound will be released when this call is made.

        @note You can also bind directly when calling exec() or execArgs() as a shortcut as well, in which case it's not necessary to make an extra call to this method.

        @param args Arguments to bind by value arguments

        @par Example:
        @code{.java}
    stmt.prepare("insert into table (id, name) values (%v, %v)");
    stmt.exec(h.get("id"), h.get("name"));
    stmt.exec();
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications; see the relevant DBI driver docs for more information

        @see bind(), bindArgs(), bindPlaceholders(), bindPlaceholdersArgs(), and bindValuesArgs().
    */
    public void bindValues(Object... args) throws Throwable {
        obj.callMethodArgs("bindValues", args);
    }

    //! Binds values to value buffer specifications given as a list in the single argument to the method to value buffers defined in prepare()
    /** If the statement has not previously been prepared with the DB API, it will be implicitly prepared by this method call. This means that this call will cause a connection to be dedicated from a DatasourcePool object or the transaction lock to be grabbed with a Datasource object, depending on the argument to constructor.

        Arguments to buffer specifications must be given in the same order as declared in the string given to the prepare() method.

        Any values previously bound will be released when this call is made.

        @note You can also bind directly when calling exec() or execArgs() as a shortcut as well, in which case it's not necessary to make an extra call to this method.

        @param vargs Arguments to bind by value arguments

        @par Example:
        @code{.java}
    stmt.prepare("insert into table (id, name) values (%v, %v)");
    stmt.bindValuesArgs(args);
    stmt.exec();
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications; see the relevant DBI driver docs for more information
    */
    public void bindValuesArgs(Object[] vargs) throws Throwable {
        obj.callMethod("bindValuesArgs", vargs);
    }

    //! Executes the bound statement with any bound buffers, also optionally allows binding placeholder buffer specifications and values to buffers defined in prepare() before executing the statement
    /** If the statement has not previously been prepared with the DB API, it will be implicitly prepared by this method call. This means that this call will cause a connection to be dedicated from a DatasourcePool object or the transaction lock to be grabbed with a Datasource object, depending on the argument to constructor.

        Optional arguments to buffer specifications must be given in the same order as declared in the string given to the prepare() method.

        If bind arguments are provided, any arguments previously bound will be released when this call is made.

        After calling this method to execute the statement, to retrieve information about the call or output values bound in the call, call affectedRows(), getOutput(), or getOutputRows() as needed.

        To retrieve rows from a select statement call either next() and fetchRow(), or fetchRows() or fetchColumns() as needed.

        @param args Optional arguments to placeholder specifications (if required by the underlying DBI driver) and bind by value arguments can be given in the call to the method; if present, arguments are bound before the statement is executed

        @par Example:
        @code{.java}
    stmt.prepare("insert into table (id, name) values (%v, %v)");
    stmt.exec(h.get("id"), h.get("name"));
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare(); the SQLStatement uses a DatasourcePool an the statement was prepared on another connection

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed; see the relevant DBI driver docs for more information

        @see execArgs()
    */
    public void exec(Object... args) throws Throwable {
        obj.callMethodArgs("exec", args);
    }

    //! Executes the bound statement with any bound buffers, also optionally allows binding placeholder buffer specifications and values given as a list in the single argument to the method to buffers defined in prepare()
    /** If the statement has not previously been prepared with the DB API, it will be implicitly prepared by this method call. This means that this call will cause a connection to be dedicated from a DatasourcePool object or the transaction lock to be grabbed with a Datasource object, depending on the argument to constructor.

        Optional arguments to buffer specifications must be given in the same order as declared in the string given to the prepare() method.

        If bind arguments are provided, any arguments previously bound will be released when this call is made.

        After calling this method to execute the statement, to retrieve information about the call or output values bound in the call, call affectedRows(), getOutput(), or getOutputRows() as needed.

        To retrieve rows from a select statement call either next() and fetchRow(), or fetchRows() or fetchColumns() as needed.

        @param vargs Optional arguments to placeholder specifications (if required by the underlying DBI driver) and bind by value arguments can be given in the call to the method; if present, arguments are bound before the statement is executed

        @par Example:
        @code{.java}
    stmt.prepare("insert into table (id, name) values (%v, %v)");
    stmt.execArgs(args);
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw(); the SQLStatement uses a DatasourcePool an the statement was prepared on another connection

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed; see the relevant DBI driver docs for more information

        @see exec()
    */
    public void execArgs(Object[] vargs) throws Throwable {
        obj.callMethod("execArgs", vargs);
    }

    //! Returns the number of rows affected by the last call to exec()
    /** @return the number of rows affected by the last call to exec()

        @par Example:
        @code{.java}
    int rc = stmt.affectedRows();
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed; see the relevant DBI driver docs for more information
    */
    public int affectedRows() throws Throwable {
        return ((Long)obj.callMethod("affectedRows")).intValue();
    }

    //! Retrieves output buffers as a hash; result sets will be returned as hashes of lists
    /** @return Returns a Map<String, Object> of output buffers; result sets will be returned as hashes of lists. Each key in the Map<String, Object> is the same as the name given to the placeholder specification in the call to prepare() or prepareRaw()

        @par Example:
        @code{.java}
    Map<String, Object> h = stmt.getOutput();
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed or when output values are retrieved; see the relevant DBI driver docs for more information
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getOutput() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getOutput");
    }

    //! Retrieves output buffers as a hash; result sets will be returned as lists of hashes
    /** @return Retrieves output buffers as a hash; result sets will be returned as lists of hashes. Each key in the Map<String, Object> is the same as the name given to the placeholder specification in the call to prepare() or prepareRaw()

        @par Example:
        @code{.java}
    Map<String, Object> h = stmt.getOutputRows();
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed or when output values are retrieved; see the relevant DBI driver docs for more information
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getOutputRows() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getOutputRows");
    }

    //! Performs an explicit define operation on the SQLStatement
    /** It is not encessary to call this method manually; define operations are implicitly executed when needed when retrieving values from a select statement

        @par Example:
        @code{.java}
    {
        AbstractSQLStatement stmt = ds.getSQLStatement();
        try {
            stmt.prepare("select * from table");
            stmt.exec();
            stmt.define();
            // note that the next() would implicitly execute exec() and define()
            while (stmt.next()) {
                Map<String, Object> row = stmt.fetchRow();
                process(row);
            }
            // release transaction lock on exit
            stmt.commit();
        } catch (Throwable e) {
            stmt.rollback();
            throw e;
        } finally {
            stmt.release();
        }
    }
        @endcode
    */
    public void define() throws Throwable {
        obj.callMethod("define");
    }

    //! Closes the statement if it is open, however this method does not release the connection or transaction lock
    /**
        @par Example:
        @code{.java}
    stmt.close();
        @endcode
    */
    public void close() throws Throwable {
        obj.callMethod("close");
    }

    //! Commits the transaction, releases the connection or the transaction lock according to the object used in the constructor and closes the statement
    /**
        @par Example:
        @code{.java}
    stmt.commit();
        @endcode

        @note For possible exceptions; see DBI driver docs for the commit() method
    */
    public void commit() throws Throwable {
        obj.callMethod("commit");
    }

    //! Closes the SQLStatement, performs a transaction rollback, and releases the connection or the transaction lock according to the object used in the constructor, and closes the statement
    /**
        @par Example:
        @code{.java}
    stmt.rollback();
        @endcode

        @note For possible exceptions; see DBI driver docs for the rollback() method
    */
    public void rollback() throws Throwable {
        obj.callMethod("rollback");
    }

    //! Manually starts a transaction and allocates a connection or grabs the transaction lock according to the object used in the constructor
    /**
        @par Example:
        @code{.java}
    stmt.beginTransaction();
        @endcode
    */
    public void beginTransaction() throws Throwable {
        obj.callMethod("beginTransaction");
    }

    //! Increments the row pointer when retrieving rows from a select statement; returns true if there is a row to retrieve, false if not
    /** If this method returns true, then call fetchRow() afterwards to retrieve the row

        @return true if there is a row to retrieve, false if not (no more rows to be retrieved)

        @par Example:
        @code{.java}
    while (stmt.next()) {
        Map<String, Object> h = stmt.fetchRow();
        process(h);
    }
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed; see the relevant DBI driver docs for more information
    */
    public boolean next() throws Throwable {
        return (boolean)obj.callMethod("next");
    }

    //! returns true if the object is currently pointing at a valid element, false if not (use when iterating with next())
    /** @return true if the object is currently pointing at a valid element, false if not

        @par Example:
        @code{.java}
    if (i.valid()) {
        process(i.getValue());
    }
        @endcode
    */
    public boolean valid() throws Throwable {
        return (boolean)obj.callMethod("valid");
    }

    //! Retrieves the current row as a Map<String, Object> where the keys are the column names and the values are the column values
    /** Use with next() to iterate through the results of a select statement one row at a time

        @return the current row as a Map<String, Object> where the keys are the column names and the values are the column values

        @par Example:
        @code{.java}
    while (stmt.next()) {
        Map<String, Object> h = stmt.fetchRow();
        process(h);
    }
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed or when row values are retrieved; see the relevant DBI driver docs for more information
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> fetchRow() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("fetchRow");
    }

    //! Retrieves the current row as a Map<String, Object> where the keys are the column names and the values are the column values
    /** Use with next() to iterate through the results of a select statement one row at a time

        @return the current row as a Map<String, Object> where the keys are the column names and the values are the column values

        @par Example:
        @code{.java}
    while (stmt.next()) {
        Map<String, Object> h = stmt.getValue();
        process(h);
    }
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note
        - Equivalent to fetchRow()
        - Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed or when row values are retrieved; see the relevant DBI driver docs for more information
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getValue() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getValue");
    }

    //! Retrieves a block of rows as a list of hashes with the maximum number of rows determined by the argument passed; automatically advances the row pointer; with this call it is not necessary to call next()
    /** If the argument passed is omitted or less than or equal to zero, then all available rows from the current row position are retrieved, also if fewer rows are available than requested then only the rows available are retrieved.

        If no more rows are available then an empty list is returned.

        @param rows The maximum number of rows to retrieve, if this argument is omitted, negative, or equal to zero, then all available rows from the current row position are retrieved

        @par Example:
        @code{.java}
    Map<String, Object>[] l = stmt.fetchRows(-1);
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note
        - There is no need to call next() when calling this method; the method automatically iterates through the given number of rows
        - Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed or when row values are retrieved; see the relevant DBI driver docs for more information
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object>[] fetchRows(int rows) throws Throwable {
        return (HashMap<String, Object>[])obj.callMethod("fetchRows", rows);
    }

    //! Retrieves all available rows as a list of hashes; automatically advances the row pointer; with this call it is not necessary to call next()
    /** @par Example:
        @code{.java}
    Map<String, Object>[] l = stmt.fetchRows();
        @endcode

        If no more rows are available then an empty list is returned.

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note
        - There is no need to call next() when calling this method; the method automatically iterates through the given number of rows
        - Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed or when row values are retrieved; see the relevant DBI driver docs for more information
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object>[] fetchRows() throws Throwable {
        return (HashMap<String, Object>[])obj.callMethod("fetchRows");
    }

    //! Retrieves a block of rows as a Map<String, Object> of lists with the maximum number of rows determined by the argument passed; automatically advances the row pointer; with this call it is not necessary to call next().
    /** If the argument passed is omitted or less than or equal to zero, then all available rows from the current row position are retrieved, also if fewer rows are available than requested then only the rows available are retrieved.

        @param rows The maximum number of rows to retrieve, if this argument is omitted, negative, or equal to zero, then all available rows from the current row position are retrieved

        @return a Map<String, Object> (giving column names) of lists (giving row values for each column) of data returned; each list will have at most \a rows elements (unless \a rows is negative, in which case all available rows are returned).  If the total number of rows available is less than \a rows (if \a rows is positive), then the last data returned by this method may return short lists.  If no more rows are available, then an empty Map<String, Object> is returned

        @par Example:
        @code{.java}
    Map<String, Object> h = stmt.fetchColumns(-1);
        @endcode

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note
        - There is no need to call next() when calling this method; the method automatically iterates through the given number of rows
        - Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed or when row values are retrieved; see the relevant DBI driver docs for more information
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> fetchColumns(int rows) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("fetchColumns", rows);
    }

    //! Retrieves all available rows as a Map<String, Object> of lists; automatically advances the row pointer; with this call it is not necessary to call next().
    /** @par Example:
        @code{.java}
    Map<String, Object> h = stmt.fetchColumns();
        @endcode

        @return a Map<String, Object> (giving column names) of lists (giving row values for each column) of data returned; each list will have at most \a rows elements (unless \a rows is negative, in which case all available rows are returned).  If the total number of rows available is less than \a rows (if \a rows is positive), then the last data returned by this method may return short lists.  If no more rows are available, then an empty Map<String, Object> is returned

        @throw SQLSTATEMENT-ERROR No %SQL has been set with prepare() or prepareRaw()

        @note
        - There is no need to call next() when calling this method; the method automatically iterates through the given number of rows
        - Exceptions could be thrown by the DBI driver when the statement is prepared or when attempting to bind the given arguments to buffer specifications or when the statement is executed or when row values are retrieved; see the relevant DBI driver docs for more information
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> fetchColumns() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("fetchColumns");
    }

    //! Retrieves a block of rows as a Qore ColumnarResult object.
    /** @param rows The maximum number of rows to retrieve; if omitted, negative, or zero, all available rows are
        retrieved

        @return a Qore::SQL::ColumnarResult object
    */
    public Object fetchColumnar(int rows) throws Throwable {
        return obj.callMethod("fetchColumnar", rows);
    }

    //! Retrieves all available rows as a Qore ColumnarResult object.
    /** @return a Qore::SQL::ColumnarResult object
    */
    public Object fetchColumnar() throws Throwable {
        return obj.callMethod("fetchColumnar");
    }

    //! Describes columns in the statement result.
    /**
        @return a Map<String, Object> with (<i>column_name</i>: <i>description_hash</i>) format, where each <i>description_hash</i> has the following keys:
        - \c "name": (string) the column name
        - \c "type": (integer) the column type code (%Qore type code)
        - \c "maxsize": (integer) the maximum size of the column
        - \c "native_type": (string) the database-specific name of the type
        - \c "internal_id": (integer) the database-specific type code of the type
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> describe() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("describe");
    }

    //! Returns the current SQL string set with the call to prepare() or prepareRaw() or void if no SQL has been set
    /** @return Returns the current SQL string set with the call to prepare() or prepareRaw() or void if no SQL has been set

        @par Example:
        @code{.java}
    String sql = stmt.getSQL();
        @endcode
    */
    public String getSQL() throws Throwable {
        return (String)obj.callMethod("getSQL");
    }

    //! Returns true if the object is currently active and has a connection or transaction lock allocated to it, or false if not
    /** @return true if the object is currently active and has a connection or transaction lock allocated to it, or false if not

        @par Example:
        @code{.java}
    if (stmt.active()) {
        stmt.commit();
    }
        @endcode
    */
    public boolean active() throws Throwable {
        return (boolean)obj.callMethod("active");
    }

    //! Returns true if the current thread is in a transaction (i.e. holds the transaction lock), false if not
    /** @return true if the current thread is in a transaction (i.e. holds the transaction lock), false if not

        @par Example:
        @code{.java}
    boolean b = stmt.currentThreadInTransaction();
        @endcode
    */
    public boolean currentThreadInTransaction() throws Throwable {
        return (boolean)obj.callMethod("currentThreadInTransaction");
    }
}
