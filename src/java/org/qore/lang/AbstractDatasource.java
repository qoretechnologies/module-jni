/** Java wrapper for the %Qore AbstractDatasource class
 *
 */
package org.qore.lang;

// java imports
import java.util.HashMap;
import java.util.Map;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;
import org.qore.lang.AbstractSQLStatement;

//! Java wrapper for the @ref Qore::SQL::AbstractDatasource class in %Qore
/** @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
 *  <tt>import qore.Qore.SQL.AbstractDatasource;</tt>
 */
@Deprecated
public class AbstractDatasource extends QoreObjectWrapper {
    //! creates the object
    public AbstractDatasource(QoreObject ds) {
        super(ds);
    }

    //! Commits the current transaction and releases any thread resources associated with the transaction
    /**
        @par Example:
        @code{.java}
    datasource.commit();
        @endcode
    */
    public void commit() throws Throwable {
        obj.callMethod("commit");
    }

    //! Rolls the current transaction back and releases any thread resources associated with the transaction
    /**
        @par Example:
        @code{.java}
    datasource.rollback();
        @endcode
    */
    public void rollback() throws Throwable {
        obj.callMethod("rollback");
    }

    //! Executes an %SQL command on the server and returns either the integer row count (for example, for updates, inserts, and deletes) or the data retrieved (for example, if a stored procedure is executed that returns values)
    /** @param sql The %SQL command to execute on the server

        @return The return value depends on the DBI driver; normally, for commands with placeholders, a hash is returned holding the values acquired from executing the %SQL statement. For all other commands, normally an integer row count is returned. However, some DBI drivers also allow select statements to be executed through this interface, which would also return a hash (column names) of lists (values for each column).

        @par Example:
        @code{.java}
    int rows = datasource.exec("insert into table (varchar_col, timestamp_col, blob_col, numeric_col) values (%v, %v, %v, %d)", string, ZonedDateTime.now(), binary, 100);
        @endcode
    */
    public Object exec(String sql) throws Throwable {
        return obj.callMethod("exec", sql);
    }

    //! Executes an %SQL command on the server and returns either the integer row count (for example, for updates, inserts, and deletes) or the data retrieved (for example, if a stored procedure is executed that returns values)
    /** @param sql The %SQL command to execute on the server
        @param args Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return The return value depends on the DBI driver; normally, for commands with placeholders, a hash is returned holding the values acquired from executing the %SQL statement. For all other commands, normally an integer row count is returned. However, some DBI drivers also allow select statements to be executed through this interface, which would also return a hash (column names) of lists (values for each column).

        @par Example:
        @code{.java}
    int rows = datasource.exec("insert into table (varchar_col, timestamp_col, blob_col, numeric_col) values (%v, %v, %v, %d)", string, ZonedDateTime.now(), binary, 100);
        @endcode
    */
    public Object exec(String sql, Object... args) throws Throwable {
        return obj.callMethod("vexec", sql, args);
    }

    //! Executes an %SQL command on the server and returns either the integer row count (for example, for updates, inserts, and deletes) or the data retrieved (for example, if a stored procedure is executed that returns values), taking a list for all bind arguments
    /** Same as exec() except takes an explicit list for bind arguments

        @param sql The %SQL command to execute on the server
        @param vargs Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return The return value depends on the DBI driver; normally, for commands with placeholders, a hash is returned holding the values acquired from executing the %SQL statement. For all other commands, normally an integer row count is returned. However, some DBI drivers also allow select statements to be executed through this interface, which would also return a hash (column names) of lists (values for each column).

        @par Example:
        @code{.java}
    int rows = datasource.vexec("insert into example_table value (%v, %v, %v)", arg_list);
        @endcode
    */
    public Object vexec(String sql, Object[] vargs) throws Throwable {
        return obj.callMethod("vexec", sql, vargs);
    }

    //! Executes an %SQL command on the server and returns either the row count (for example, for updates and inserts) or the data retrieved (for example, if a stored procedure is executed that returns values)
    /** This method does not do any variable binding, so it's useful for example for DDL statements etc

        @par Warning:
        Using this method to execute pure dynamic %SQL many times with different %SQL strings (as opposed to using the same string and binding by value instead of dynamic %SQL) can affect application performance by prohibiting the efficient usage of the DB server's statement cache. See DB server documentation for variable binding and the %SQL statement cache for more information.

        @param sql The %SQL command to execute on the server; this string will not be subjected to any transformations for variable binding

        @return The return value depends on the DBI driver; normally, for commands with placeholders, a hash is returned holding the values acquired from executing the %SQL statement. For all other commands, normally an integer row count is returned. However, some DBI drivers also allow select statements to be executed through this interface, which would also return a hash (column names) of lists (values for each column).

        @par Example:
        @code{.java}
    datasource.execRaw("create table my_tab (id number, some_text varchar2(30))");
        @endcode
    */
    public Object execRaw(String sql) throws Throwable {
        return obj.callMethod("execRaw", sql);
    }

    //! Executes an %SQL select statement on the server and (normally) returns the result as a hash (column names) of lists (column values per row)
    /** The usual return format of this method is suitable for use with @ref context "context statements", for easy iteration and processing of query results.
        Alternatively, the HashListIterator class can be used to iterate the return value of this method.

        Additionally, this format is a more efficient format than that returned by the selectRows() method, because the column names are not repeated for each row returned. Therefore, for retrieving anything greater than small amounts of data, it is recommended to use this method instead of selectRows().

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute on the server
        @param ... Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return This method returns a hash (the keys are the column names) of lists (the column data per row) when executed with an %SQL select statement, however some DBI drivers allow any %SQL to be executed through this method, in which case other data types can be returned (such as an integer for a row count or a hash for output parameters when executing a stored procedure).  If no rows are found, a hash of column names assigned to empty lists is returned.

        @par Example:
        @code{.java}
    Map<String, Object> query = (Map<String, Object>)datasource.select("select * from table where varchar_column = 'col'");
        @endcode

        @note This method returns all the data available immediately; to process query data piecewise, use the SQLStatement class
    */
    public Object select(String sql) throws Throwable {
        return obj.callMethod("select", sql);
    }

    //! Executes an %SQL select statement on the server and (normally) returns the result as a hash (column names) of lists (column values per row)
    /** The usual return format of this method is suitable for use with @ref context "context statements", for easy iteration and processing of query results.
        Alternatively, the HashListIterator class can be used to iterate the return value of this method.

        Additionally, this format is a more efficient format than that returned by the selectRows() method, because the column names are not repeated for each row returned. Therefore, for retrieving anything greater than small amounts of data, it is recommended to use this method instead of selectRows().

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute on the server
        @param args Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return This method returns a hash (the keys are the column names) of lists (the column data per row) when executed with an %SQL select statement, however some DBI drivers allow any %SQL to be executed through this method, in which case other data types can be returned (such as an integer for a row count or a hash for output parameters when executing a stored procedure).  If no rows are found, a hash of column names assigned to empty lists is returned.

        @par Example:
        @code{.java}
    // bind a string and a date/time value by value in a query
    Map<String, Object> query = (Map<String, Object>)datasource.select("select * from table where varchar_column = %v and timestamp_column > %v", string, ZonedDateTime::parse("2007-10-11T15:31:26.289+02:00");
        @endcode

        @note This method returns all the data available immediately; to process query data piecewise, use the SQLStatement class
    */
    public Object select(String sql, Object... args) throws Throwable {
        return obj.callMethod("vselect", sql, args);
    }

    //! Executes an %SQL select statement and returns a Qore ColumnarResult object.
    /** The returned object can be passed back to Qore code or accessed with QoreObject.callMethod().

        @param sql The %SQL command to execute on the server

        @return a Qore::SQL::ColumnarResult object
    */
    public Object selectColumnar(String sql) throws Throwable {
        return obj.callMethod("selectColumnar", sql);
    }

    //! Executes an %SQL select statement and returns a Qore ColumnarResult object.
    /** The returned object can be passed back to Qore code or accessed with QoreObject.callMethod().

        @param sql The %SQL command to execute on the server
        @param args bind arguments

        @return a Qore::SQL::ColumnarResult object
    */
    public Object selectColumnar(String sql, Object... args) throws Throwable {
        return obj.callMethod("vselectColumnar", sql, args);
    }

    //! Executes an %SQL select statement and returns a Qore ColumnarResult object, taking all bind arguments as an array.
    /** @param sql The %SQL command to execute on the server
        @param vargs bind arguments

        @return a Qore::SQL::ColumnarResult object
    */
    public Object vselectColumnar(String sql, Object[] vargs) throws Throwable {
        return obj.callMethod("vselectColumnar", sql, vargs);
    }

    //! Executes an %SQL select statement on the server and returns the first row as a hash (the column values)
    /** If more than one row is returned, then it is treated as an error and a \c DBI-SELECT-ROW-ERROR is returned (however the DBI driver should raise its own exception here to avoid retrieving more than one row from the server). For a similar method taking a list for all bind arguments, see vselectRow().

        This method also accepts all bind parameters (<tt>%%d</tt>, <tt>%%v</tt>, etc) as documented in @ref sql_binding "Binding by Value and Placeholder"

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute on the server

        @return This method normally returns a hash (the keys are the column names) of row data or null if no row is found for the query when executed with an %SQL select statement, however some DBI drivers allow any %SQL statement to be executed through this method (not only select statements), in this case other data types can be returned

        @par Example:
        @code{.java}
    Map<String, Object> h = (Map<String, Object>)datasource.selectRow("select * from example_table where id = 1");
        @endcode

        @throw DBI-SELECT-ROW-ERROR more than 1 row retrieved from the server
    */
    public Object selectRow(String sql) throws Throwable {
        return obj.callMethod("selectRow", sql);
    }

    //! Executes an %SQL select statement on the server and returns the first row as a hash (the column values)
    /** If more than one row is returned, then it is treated as an error and a \c DBI-SELECT-ROW-ERROR is returned (however the DBI driver should raise its own exception here to avoid retrieving more than one row from the server). For a similar method taking a list for all bind arguments, see vselectRow().

        This method also accepts all bind parameters (<tt>%%d</tt>, <tt>%%v</tt>, etc) as documented in @ref sql_binding "Binding by Value and Placeholder"

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute on the server
        @param args Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return This method normally returns a hash (the keys are the column names) of row data or null if no row is found for the query when executed with an %SQL select statement, however some DBI drivers allow any %SQL statement to be executed through this method (not only select statements), in this case other data types can be returned

        @par Example:
        @code{.java}
    Map<String, Object> h = (Map<String, Object>)datasource.selectRow("select * from example_table where id = 1");
        @endcode

        @throw DBI-SELECT-ROW-ERROR more than 1 row retrieved from the server
    */
    public Object selectRow(String sql, Object... args) throws Throwable {
        return obj.callMethod("vselectRow", sql, args);
    }

    //! Executes an %SQL select statement on the server and returns the result as a list (rows) of hashes (the column values)
    /** The return format of this method is not as memory efficient as that returned by the select() method, therefore for larger amounts of data, it is recommended to use select().
        The usual return value of this method can be iterated with the ListHashIterator class.

        This method also accepts all bind parameters (<tt>%%d</tt>, <tt>%%v</tt>, etc) as documented in @ref sql_binding "Binding by Value and Placeholder"

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute on the server

        @return Normally returns a list (rows) of hash (where the keys are the column names of each row) or null if no rows are found for the query, however some DBI drivers allow any %SQL statement to be executed through this method (not only select statements), in this case other data types can be returned

        @par Example:
        @code{.java}
    Objeect[] list = (Object[])datasource.selectRows("select * from example_table");
        @endcode

        @see select()

        @note This method returns all the data available immediately; to process query data piecewise, use the SQLStatement class
    */
    public Object selectRows(String sql) throws Throwable {
        return obj.callMethod("selectRows", sql);
    }

    //! Executes an %SQL select statement on the server and returns the result as a list (rows) of hashes (the column values)
    /** The return format of this method is not as memory efficient as that returned by the select() method, therefore for larger amounts of data, it is recommended to use select().
        The usual return value of this method can be iterated with the ListHashIterator class.

        This method also accepts all bind parameters (<tt>%%d</tt>, <tt>%%v</tt>, etc) as documented in @ref sql_binding "Binding by Value and Placeholder"

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute on the server
        @param args Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return Normally returns a list (rows) of hash (where the keys are the column names of each row) or null if no rows are found for the query, however some DBI drivers allow any %SQL statement to be executed through this method (not only select statements), in this case other data types can be returned

        @par Example:
        @code{.java}
    Objeect[] list = (Object[])datasource.selectRows("select * from example_table");
        @endcode

        @see select()

        @note This method returns all the data available immediately; to process query data piecewise, use the SQLStatement class
    */
    public Object selectRows(String sql, Object... args) throws Throwable {
        return obj.callMethod("vselectRows", sql, args);
    }

    //! Executes a select statement on the server and returns the results in a hash (column names) of lists (column values per row), taking a list for all bind arguments
    /** The usual return format of this method is suitable for use with @ref context "context statements", for easy iteration and processing of query results.
        Alternatively, the HashListIterator class can be used to iterate the return value of this method.

        This method also accepts all bind parameters (<tt>%%d</tt>, <tt>%%v</tt>, etc) as documented in @ref sql_binding "Binding by Value and Placeholder"

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute on the server
        @param vargs Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return Normally returns a hash (the keys are the column names) of list (each hash key's value is a list giving the row data), however some DBI drivers allow any %SQL statement to be executed through this method (not only select statements), in this case other data types can be returned.  If no rows are found, a hash of column names assigned to empty lists is returned.

        @par Example:
        @code{.java}
    Map<String, Object> query = (Map<String, Object>)datasource.vselect("select * from example_table where id = %v and name = %v", arg_list);
        @endcode

        @see select()

        @note This method returns all the data available immediately; to process query data piecewise, use the SQLStatement class
    */
    public Object vselect(String sql, Object[] vargs) throws Throwable {
        return obj.callMethod("vselect", sql, vargs);
    }

    //! Executes a select statement on the server and returns the first row as a hash (column names and values), taking a list for all bind arguments
    /** This method is the same as the selectRow() method, except this method takes a single argument after the %SQL command giving the list of bind value parameters

        This method also accepts all bind parameters (<tt>%%d</tt>, <tt>%%v</tt>, etc) as documented in @ref sql_binding "Binding by Value and Placeholder"

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute on the server
        @param vargs Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return This method normally returns a hash (the keys are the column names) of row data or null if no row is found for the query when executed with an %SQL select statement, however some DBI drivers allow any %SQL statement to be executed through this method (not only select statements), in this case other data types can be returned

        @par Example:
        @code{.java}
    Map<String, Object> h = (Map<String, Object>)datasource.vselectRow("select * from example_table where id = %v and type = %v", arg_list);
        @endcode

        @see selectRow()
    */
    public Object vselectRow(String sql, Object[] vargs) throws Throwable {
        return obj.callMethod("vselect", sql, vargs);
    }

    //! Executes a select statement on the server and returns the results in a list (rows) of hashes (column names and values), taking a list for all bind arguments
    /** Same as the selectRows() method, except this method takes a single argument after the %SQL command giving the list of bind value parameters.

        The usual return value of this method can be iterated with the ListHashIterator class.

        The return format of this method is not as memory efficient as that returned by the select() method, therefore for larger amounts of data, it is recommended to use select().

        This method also accepts all bind parameters (<tt>%%d</tt>, <tt>%%v</tt>, etc) as documented in @ref sql_binding "Binding by Value and Placeholder"

        To execute select statements that begin a transaction (such as \c "select for update"), execute beginTransaction() first to signal that a transaction is starting;
        this is particularly important when the object is shared among more than one thread.

        @param sql The %SQL command to execute
        @param vargs Include any values to be bound (using <tt>%v</tt> in the command string) or placeholder specifications (using <tt>:</tt><em>key_name</em> in the command string) in order after the command string

        @return Normally returns a list (rows) of hash (where the keys are the column names of each row) or null if no rows are found for the query, however some DBI drivers allow any %SQL statement to be executed through this method (not only select statements), in this case other data types can be returned

        @par Example:
        @code{.java}
    Object[] list = (Object[])datasource.vselectRows("select * from example_table where id = %v and type = %v", arg_list);
        @endcode

        @see selectRows()

        @note This method returns all the data available immediately; to process query data piecewise, use the SQLStatement class
    */
    public Object vselectRows(String sql, Object[] vargs) throws Throwable {
        return obj.callMethod("vselectRows", sql, vargs);
    }

    //! Manually signals the start of transaction management on the AbstractDatasource
    /** This method should be called when the AbstractDatasource object will be shared between more than 1 thread, and a transaction will be started with a select() method or the like.

        This method does not make any communication with the server to start a transaction; it only allocates the transaction lock to the current thread in %Qore.

        @par Example:
        @code{.java}
    datasource.beginTransaction();
        @endcode
    */
    public void beginTransaction() throws Throwable {
        obj.callMethod("beginTransaction");
    }

    //! Returns the username parameter as a string or null if none is set
    /** @return the username parameter as a string or null if none is set

        @par Example:
        @code{.java}
    String user = datasource.getUserName();
        @endcode
    */
    public String getUserName() throws Throwable {
        return (String)obj.callMethod("getUserName");
    }

    //! Returns the password parameter as a string or null if none is set
    /** @return the password parameter as a string or null if none is set

        @par Example:
        @code{.java}
    String pass = datasource.getPassword();
        @endcode
    */
    public String getPassword() throws Throwable {
        return (String)obj.callMethod("getPassword");
    }

    //! Returns the database name parameter as a string or null if none is set
    /** @return the database name parameter as a string or null if none is set

        @par Example:
        @code{.java}
    String db = datasource.getDBName();
        @endcode
    */
    public String getDBName() throws Throwable {
        return (String)obj.callMethod("getDBName");
    }

    //! Retrieves the database-specific charset set encoding for the object
    /** @return the database-specific charset set encoding for the object

        @par Example:
        @code{.java}
    String enc = datasource.getDBEncoding();
        @endcode

        @see getOSEncoding();
    */
    public String getDBEncoding() throws Throwable {
        return (String)obj.callMethod("getDBEncoding");
    }

    //! Returns the %Qore character encoding name for the object as a string or null if none is set
    /** @return the %Qore character encoding name for the object as a string or null if none is set

        @par Example:
        @code{.java}
    String enc = datasource.getOSEncoding();
        @endcode
    */
    public String getOSEncoding() throws Throwable {
        return (String)obj.callMethod("getOSEncoding");
    }

    //! Returns the hostname parameter as a string or null if none is set
    /** @return the hostname parameter as a string or null if none is set

        @par Example:
        @code{.java}
    String host = datasource.getHostName();
        @endcode
    */
    public String getHostName() throws Throwable {
        return (String)obj.callMethod("getHostName");
    }

    //! Gets the port number that will be used for the next connection to the server
    /** Invalid port numbers will cause an exception to be thrown when the connection is opened

        @par Example:
        @code{.java}
    Integer port = datasource.getPort();
        @endcode
    */
    public Integer getPort() throws Throwable {
        return (Integer)obj.callMethod("getPort");
    }

    //! Returns the name of the driver used for the object
    /** @return the name of the driver used for the object

        @par Example:
        @code{.java}
    String driver = datasource.getDriverName();
        @endcode
    */
    public String getDriverName() throws Throwable {
        return (String)obj.callMethod("getDriverName");
    }

    //! Returns the driver-specific server version data for the current connection
    /** @return the driver-specific server version data for the current connection

        @par Example:
        @code{.java}
    Object ver = datasource.getServerVersion();
        @endcode

        @note see the documentation for the DBI driver being used for additional possible exceptions
    */
    public Object getServerVersion() throws Throwable {
        return obj.callMethod("getServerVersion");
    }

    //! Retrieves the driver-specific client library version information
    /** @return the driver-specific client library version information

        @par Example:
        @code{.java}
    Object ver = datasource.getClientVersion();
        @endcode

        @note see the documentation for the DBI driver being used for possible exceptions
    */
    public Object getClientVersion() throws Throwable {
        return obj.callMethod("getClientVersion");
    }

    //! Returns @ref True if a transaction is currently in progress
    /** @return @ref True if a transaction is currently in progress

        @par Example:
        @code{.java}
    boolean b = datasource.inTransaction();
        @endcode
    */
    public boolean inTransaction() throws Throwable {
        return (boolean)obj.callMethod("inTransaction");
    }

    //! Returns a @ref datasource_hash "datasource hash" describing the configuration of the current object
    /** @par Example:
        @code{.java}
    Map<String, Object> h = obj.getConfigHash();
        @endcode

        @return a @ref datasource_hash "datasource hash" describing the configuration of the current object

        @since %Qore 0.8.8
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getConfigHash() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("getConfigHash");
    }

    //! Returns a string giving the configuration of the current object in a format that can be parsed by parse_datasource()
    /** @par Example:
        @code{.java}
    String str = obj.getConfigString();
        @endcode

        @return a string giving the configuration of the current object in a format that can be parsed by parse_datasource()

        @since %Qore 0.8.8
    */
    public String getConfigString() throws Throwable {
        return (String)obj.callMethod("getConfigString");
    }

    //! Should return @ref true if the current thread is in a transaction with this object, must be re-implemented in subclasses to provide the desired functionality
    /** @note
        - this is reimplemented as @ref Qore::SQL::Datasource::currentThreadInTransaction() "Datasource::currentThreadInTransaction()" and @ref Qore::SQL::DatasourcePool::currentThreadInTransaction() "DatasourcePool::currentThreadInTransaction()"
        - this method was added as a non-abstract method in Qore 0.8.10 to avoid breaking existing subclasses of AbstractDatasource

        @since Qore 0.8.10
    */
    public boolean currentThreadInTransaction() throws Throwable {
        return (boolean)obj.callMethod("currentThreadInTransaction");
    }

    //! Returns an @ref Qore::SQL::AbstractSQLStatement "AbstractSQLStatement" object based on the current database connection object
    /** @note
        - this is reimplemented as @ref Qore::SQL::Datasource::getSQLStatement() "Datasource::getSQLStatement()" and @ref Qore::SQL::DatasourcePool::getSQLStatement() "DatasourcePool::getSQLStatement()"
        - this method was added as a non-abstract method in Qore 0.9.0 to avoid breaking existing subclasses of AbstractDatasource

        @since Qore 0.9.0
    */
    public AbstractSQLStatement getSQLStatement() throws Throwable {
        return new AbstractSQLStatement((QoreObject)obj.callMethodSave("getSQLStatement"));
    }
}
