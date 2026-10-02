/** Java wrapper for the %Qore AbstractTable class
 *
 */
package org.qore.lang.sqlutil;

// java imports
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreJavaApi;
import org.qore.lang.AbstractDatasource;

// Qore imports
import org.qore.lang.sqlutil.AbstractSqlUtilBase;
import org.qore.lang.AbstractSQLStatement;

//! Java wrapper for the @ref AbstractTable class in %Qore
/**
    @section CallbackOptions Callback Options
    The following keys can be set for this option:
    - \c info_callback: see @ref info_callback
    - \c sql_callback: see @ref sql_callback
    - \c sql_callback_executed: see @ref sql_callback_executed

    @section CreationOptions Creation Options
    This option is comprised of @ref CallbackOptions plus the following keys:
    - \c replace: (coolean) if true and supported by the underlying db driver, \c "create or replace" text is used
      when creating objects
    - \c table_cache: (@ref SqlUtil::Tables "Tables") an optional table cache for maintaining cached tables and
      foreign key relationships between tables
    - \c data_tablespace: (String) a string giving the data tablespace to use for tables
    - \c index_tablespace: (String) a string giving the index tablespace to use for indexes

    @section IndexOptions Index Options
    - \c index_tablespace: (String) a string giving the index tablespace to use for indexes
    - \c replace: (boolean) if true and supported by the underlying db driver
      "create or replace" text is used when creating objects

    @section TableCreationOptions Table Creation Options
    currently this option is a combination of @ref IndexOptions and @ref CreationOptions plus the following:
    - \c omit: a list pf attributes to omit; possible values are: \c indexes, \c foreign_constraints, \c triggers

    @section AlignTableOptions Align Table Options
    Currently this option is a combination of @ref TableCreationOptions and the following options:
    - \c column_map: (Map) a hash for automatically renaming columns; if the source name (key) exists and
      the target name (value) does not exist, then the source column is automatically renamed
    - \c index_map: (Map) a hash for automatically renaming indexes; if the source name (key) exists and
      the target name (value) does not exist, then the source index is automatically renamed
    - \c constraint_map: (Map) a hash for automatically renaming constraints; if the source name (key)
      exists and the target name (value) does not exist, then the source constraint is automatically renamed
    - \c trigger_map: (Map) a hash for automatically renaming triggers; if the source name (key) exists and
      the target name (value) does not exist, then the source trigger is automatically renamed
    - \c db_table_cache: (@ref SqlUtil::Tables "Tables") an optional table cache for maintaining tables in the
      database and foreign key relationships between tables
    - \c force: (boolean) if true and supported by the driver and object, any objects dropped will be
      dropped with \c FORCE or \c CASCADE options

    @section InsertOptions Insert Options
    In addition to any @ref SqlUtil::AbstractTable::SqlDataCallbackOptions, the following keys can be set for this option:
    - \c returning: a list having elements of one of the two following types:
        - String: column names to return the value inserted
        - Map: a hash having the following keys:
        - \c "key": (required) the column name to return
        - \c "type": (optional) the data type for the output placeholder buffer (ex: @ref Qore::Type::Number "number")

    @note using \c "returning" with a database that does not support this clause will cause an exception to be thrown;
    see @ref AbstractTable.hasReturning()

    @section UpsertOptions Upsert Options
    The following keys can be set for upsert options:
    - \c commit_block: the number of changes made before an automatic commit is made for upsert methods that perform
      commits
    - \c delete_others: if this option is true, then a hash of primary key values in the input data
      is built as the input data is iterated.  After iterating, if the row count of the table and the input data
      matches, then nothing more is done, otherwise, every row of the table is iterated and compared to the primary
      key hash; if a row does not match a primary key value, then it is deleted.  This operation allows tables to be
      completely synchronized by removing rows in the target table not present in the source table.  This operation is
      expensive for large data sets.
    - \c info_callback: see @ref upsert_info_callback
    - \c omit_update: allows for an asymmetrical upsert where a set of column values is inserted, but a smaller set is
      updated in case the unique key values are present in the target table; the value of this key should be set to
      the columns to omit in the update clause

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qoremod.SqlUtil.AbstractTable;</tt>
*/
@Deprecated
public class AbstractTable extends AbstractSqlUtilBase {
    /** @defgroup upsert_options Upsert Strategy Codes
        These options are used with:
        - @ref upsert()
        - @ref upsertFromSelect()

        to specify the upsert strategy when synchronizing table data
        */
    ///@{
    //! Upsert option: insert first, if the insert fails, then update
    /** with this option an insert is attempted, and if it fails due to a duplicate row, then an update is made unconditionally; with
        this upsert strategy, the following row result codes are possible:
        - @ref UR_Inserted
        - @ref UR_Verified (the only value returned by certain drivers with optimized upsert implementations)
    */
    public static int UpsertInsertFirst = 1;

    //! Upsert option: update first, if the update fails, then insert
    /** with this option an update is attempted, and if it fails due to a missing row, then an insert is performed; with
        this upsert strategy, the following row result codes are possible:
        - @ref UR_Inserted
        - @ref UR_Verified (the only value returned by certain drivers with optimized upsert implementations)
    */
    public static int UpsertUpdateFirst = 2;

    //! Upsert option: select first, if the row is unchanged, do nothing, if it doesn't exist, insert, otherwise update
    /** with this option the row is selected, if it doesn't exist, an insert is made, and an update is made only if the
        values are different; with this upsert strategy, the following row result codes are possible:
        - @ref UR_Inserted
        - @ref UR_Updated
        - @ref UR_Unchanged
    */
    public static int UpsertSelectFirst = 3;

    //! Upsert option: if the target table is empty, use @ref UpsertInsertFirst, otherwise use @ref UpsertUpdateFirst
    /** With this upsert strategy, the following row result codes are possible:
        - @ref UR_Inserted
        - @ref UR_Verified (the only value returned by certain drivers with optimized upsert implementations)
    */
    public static int UpsertAuto = 4;

    //! Upsert option: insert if the row does not exist, otherwise ignore
    /** With this upsert strategy, the following row result codes are possible:
        - @ref UR_Inserted
        - @ref UR_Unchanged
    */
    public static int UpsertInsertOnly = 5;

    //! Upsert option: update if the row exists, otherwise ignore
    /** With this upsert strategy, the following row result codes are possible:
        - @ref UR_Verified
        - @ref UR_Unchanged
    */
    public static int UpsertUpdateOnly = 6;

    //! hash mapping upsert strategy codes to a text description
    /** @see @ref UpsertStrategyDescriptionMap for a reverse mapping
    */
    public static final Map<Integer, String> UpsertStrategyMap = Collections.unmodifiableMap(new HashMap<Integer, String>() {
        {
            put(UpsertInsertFirst, "UpsertInsertFirst");
            put(UpsertUpdateFirst, "UpsertUpdateFirst");
            put(UpsertSelectFirst, "UpsertSelectFirst");
            put(UpsertAuto, "UpsertAuto");
            put(UpsertInsertOnly, "UpsertInsertOnly");
            put(UpsertUpdateOnly, "UpsertUpdateOnly");
        }
    });

    //! hash mapping upsert strategy descriptions to upsert strategy codes
    /** @see @ref UpsertStrategyMap for a reverse mapping
    */
    public static final Map<String, Integer> UpsertStrategyDescriptionMap = Collections.unmodifiableMap(new HashMap<String, Integer>() {
        {
            put("UpsertInsertFirst", UpsertInsertFirst);
            put("UpsertUpdateFirst", UpsertUpdateFirst);
            put("UpsertSelectFirst", UpsertSelectFirst);
            put("UpsertAuto", UpsertAuto);
            put("UpsertInsertOnly", UpsertInsertOnly);
            put("UpsertUpdateOnly", UpsertUpdateOnly);
        }
    });
    ///@}

    /** @defgroup java_sql_cop_trunc_date_enum cop_trunc_date() formats
    These are formatting constant which can be used as @ref cop_trunc_date() formatting arguments.

    Input date used in the table below: 2017-04-20 14:27:34

    |!Constant|!Meaning|!Example
    |@ref DT_YEAR|Truncate date up to year|2017-01-01 00:00:00
    |@ref DT_MONTH|Truncate date up to month|2017-04-01 00:00:00
    |@ref DT_DAY|Truncate date up to day|2017-01-20 00:00:00
    |@ref DT_HOUR|Truncate date up to hour|2017-01-20 14:00:00
    |@ref DT_MINUTE|Truncate date up to minute|2017-01-20 14:27:00
    |@ref DT_SECOND|Truncate date up to second|2017-01-20 14:27:34

    @note Oracle: using \c DT_SECOND for \c DATE type does not make sense as the \c DATE resolution
          is up to seconds out of the box. On the other side \c TINESTAMP is truncated up to seconds
          with this operator.
    */
    ///@{
    //! Format unit: year
    public static final String DT_YEAR = "Y";

    //! Format unit: month
    public static final String DT_MONTH = "M";

    //! Format unit: day
    public static final String DT_DAY = "D";

    //! Format unit: hour
    public static final String DT_HOUR = "H";

    //! Format unit: minute
    public static final String DT_MINUTE = "m";

    //! Format unit: hour
    public static final String DT_SECOND = "S";
    ///@}

    /** @defgroup sql_cop_funcs SQL Column Operator Functions
        These are static methods that can be used in the \c "columns" argument for select statements:
        - @ref cop_append(): append a string to the output of a column
        - @ref cop_as(): rename columns
        - @ref cop_avg(): return the averge value for the given column when grouping
        - @ref cop_cast(): convert column value into another datatype
        - @ref cop_coalesce(): returns the first non-NULL value in the given columns
        - @ref cop_distinct(): add \c DISTINCT to the column name
        - @ref cop_length(): return the length of the given text column
        - @ref cop_lower(): return a column in lower case
        - @ref cop_max(): return the maximum value for the given column when grouping
        - @ref cop_min(): return the minimum value for the given column when grouping
        - @ref cop_over(): return a hash for the \c "over" operator for windowing methods
        - @ref cop_prepend(): prepend a string to the output of a column
        - @ref cop_seq(): increments the sequence and returns the next value
        - @ref cop_seq_currval(): returns the current value of the given sequence without changing the sequence
        - @ref cop_substr(): returns a substring of a text column
        - @ref cop_sum(): return the sum of all values for the given column when grouping
        - @ref cop_upper(): return a column in upper case
        - @ref cop_value(): return a constant value (SQL literal) in a column
        - @ref cop_year(): return the year of the given date column
        - @ref cop_year_day(): return the year to day value of the given date column
        - @ref cop_year_hour(): return the year to hour value of the given date column
        - @ref cop_year_month(): return the year and month value of the given date column
        - @ref cop_trunc_date(): return truncated date regarding the mask
        - @ref cop_cume_dist(): analytic/window method
        - @ref cop_dense_rank(): analytic/window method
        - @ref cop_first_value(): analytic/window method
        - @ref cop_last_value(): analytic/window method
        - @ref cop_ntile(): analytic/window method
        - @ref cop_percent_rank(): analytic/window method
        - @ref cop_rank(): analytic/window method
        - @ref cop_row_number(): analytic/window method

        Column operator methods can be nested as in the following example:
        @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("permission_type", "USER");
    put("limit", 100);
    put("offset", 200);
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(AbstractTable.cop_lower("permission_type"), "perm"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode
     */
    ///@{
    //! returns a @ref SqlUtil::ColumnOperatorInfo hash
    /** @param cop the column operator (one of @ref sql_cops)
        @param column the column name
        @param arg the argument to the operator

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @note Normally this method is not called directly, but rather by the other column operator methods
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> make_cop(String cop, Object column, Object arg) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("make_cop", cop, column, arg);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash
    /** @param cop the column operator (one of @ref sql_cops)
        @param column the column name

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @note Normally this method is not called directly, but rather by the other column operator methods
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> make_cop(String cop, Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("make_cop", cop, column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "as" operator with the given argument
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("permission_type", "USER");
    put("limit", 100);
    put("offset", 200);
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(AbstractTable.cop_lower("permission_type"), "perm"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins) or any other column "cop_..." method
        @param arg the new name of the output column

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @see cop_value for SQL literals handling
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_as(Object column, String arg) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_as", column, arg);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "cast" operator with the given argument(s)
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> ch = new HashMap<String, Object>() {
    put("id", AbstractTable.cop_cast("id", "string"));
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", ch);
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins) or any other column "cop_..." method
        @param arg the new datatype to cast the column value(s) to
        @param arg1 optional, type dependent, specification (e.g. size or precision)
        @param arg2 optional, type dependent, specification (e.g. scale)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @see cop_value for SQL literals handling
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_cast(Object column, String arg, Object arg1, Object arg2) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_cast", column, arg, arg1, arg2);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "cast" operator with the given argument(s)
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> ch = new HashMap<String, Object>() {
    put("id", AbstractTable.cop_cast("id", "string"));
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", ch);
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins) or any other column "cop_..." method
        @param arg the new datatype to cast the column value(s) to
        @param arg1 optional, type dependent, specification (e.g. size or precision)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @see cop_value for SQL literals handling
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_cast(Object column, String arg, Object arg1) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_cast", column, arg, arg1);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "cast" operator with the given argument(s)
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> ch = new HashMap<String, Object>() {
    put("id", AbstractTable.cop_cast("id", "string"));
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", ch);
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins) or any other column "cop_..." method
        @param arg the new datatype to cast the column value(s) to

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @see cop_value for SQL literals handling
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_cast(Object column, String arg) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_cast", column, arg);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "prepend" operator with the given argument
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_prepend("name", "migrated-"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)
        @param arg the text to prepend to the row values in the output column

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_prepend(Object column, String arg) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_prepend", column, arg);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "append" operator with the given argument
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_append("name", "-migrated"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)
        @param arg the text to append (ie concatenate) to the row values in the output column

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_append(Object column, String arg) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_append", column, arg);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "value" (literal) operator with the given argument
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_value(100));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param arg the value to be returned in the column

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        SQL literals can be useful in some cases - as dummy values for select
        statements where there is exact columns required, unions, expected values
        for \c arc.insertFromIterator(src.getStatement(sh))
        "insert as select", etc.

        The term literal refers to a fixed data value. For example, 123, 'foobar' etc.

        Mapping of Qore values to literals:

        |!Java Type|!SQL Type|!Qore Example|!SQL interpretation
        |int|NUMBER as it is|\c 123|\c 123
        |float|NUMBER as it is|\c 12.3|\c 12.3
        |\c java.math.BigDecimal|NUMBER as it is|\c 1.2n|\c 1.2
        |\c java.time.ZonedDateTime|String representation of the date using DB native implementation like TO_TIMESTAMP for Oracle.|\c now()|\c to_timestamp('20150421104825000000', 'YYYYMMDDHH24MISSFF6')
        |boolean|Internal representation of the bool value using DB native implementation|\c True|\c 1
        |String|Standard and escaped string literal. No additional literal methods like Oracle's <tt>nq{foobar}</tt> are supported now|\c "foo bar"|\c 'foo bar'
        |\c null|Direct null literal|\c NULL|\c null

        @note Passing an existing SQL method name as a value to the cop_value() method
                does not result in method call. The string value is escaped as it is.
                Example: sysdate becomes 'sysdate'. See example below.

        The most useful cop_value() usage is with cooperation of cop_as() which allows
        human readable column name aliases.

        @warning Using cop_value() without cop_as() can result in errors
                    depending on the DB backend. For example Oracle's use of <tt>cop_value(1), cop_value(True)</tt>
                    ends with <tt>ORA-00918: column ambiguously defined</tt>
                    because both values are interpreted as 1 in the resulting SQL.
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_value(Object arg) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_value", arg);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "upper" operator with the given argument; returns a column value in upper case
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_upper("name"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_upper(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_upper", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "lower" operator with the given argument; returns a column value in lower case
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_lower("name"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_lower(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_lower", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "distinct" operator with the given argument; returns distinct column values
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_distinct("name"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_distinct(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_distinct", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "min" operator; returns minimum column values
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_min("id"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_min(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_min", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "max" operator; returns maximum column values
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_max("id"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_max(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_max", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "avg" operator; returns average column values
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_avg("quantity"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_avg(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_avg", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "sum" operator; returns the total sum of a numeric column.
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_sum("quantity"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_sum(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_sum", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "count" operator; returns row counts
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_count());
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_count(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_count", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "count" operator; returns row counts
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_count());
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_count() throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_count");
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "over" clause
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(AbstractTable.cop_over(AbstractTable.cop_max("qty"), "account_id"), "max_qty_per_account")));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_over(Object column, String partitionby, String orderby) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_over", column, partitionby, orderby);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "over" clause
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(AbstractTable.cop_over(AbstractTable.cop_max("qty"), "account_id"), "max_qty_per_account")));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_over(Object column, String partitionby) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_over", column, partitionby);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "over" clause
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(AbstractTable.cop_over(AbstractTable.cop_max("qty"), "account_id"), "max_qty_per_account")));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_over(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_over", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "-" operator with the given arguments
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_minus("complete_count", "error_count"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column1 the column specification for the first argument (String name or dot notation for use in joins)
        @param column2 the column specification for the second argument (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_minus(Object column1, Object column2) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_minus", column1, column2);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "+" operator with the given arguments
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_plus("complete_count", "error_count"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column1 the column specification for the first argument (String name or dot notation for use in joins)
        @param column2 the column specification for the second argument (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_plus(Object column1, Object column2) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_plus", column1, column2);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "/" operator with the given arguments
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_divide("complete_count", "error_count"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column1 the column specification for the first argument (String name or dot notation for use in joins)
        @param column2 the column specification for the second argument (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_divide(Object column1, Object column2) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_divide", column1, column2);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "*" operator with the given arguments
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_multiply("complete_count", "error_count"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column1 the column specification for the first argument (String name or dot notation for use in joins)
        @param column2 the column specification for the second argument (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_multiply(Object column1, Object column2) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_multiply", column1, column2);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "year" operator with the given argument
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_year("error_time"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_year(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_year", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "year_month" operator with the given argument
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_year_month("error_time"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_year_month(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_year_month", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "year_day" operator with the given argument
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_year_day("error_time"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_year_day(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_year_day", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "year_hour" operator with the given argument
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_year_hour("error_time"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_year_hour(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_year_hour", column);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "seq" operator with the given argument giving the sequence name whose value should be returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_seq("xid", "xis"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param seq the name of the sequence whose value should be returned
        @param as an optional column name that should be returned for the sequence value (so that @ref cop_as() need not be used)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_seq(String seq, String as) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_seq", seq, as);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "seq" operator with the given argument giving the sequence name whose value should be returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_seq("xid", "xis"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param seq the name of the sequence whose value should be returned

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_seq(String seq) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_seq", seq);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "seq_currval" operator with the given argument giving the sequence name whose current value should be returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_seq_currval("xid", "xis"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param seq the name of the sequence whose current value should be returned
        @param as an optional column name that should be returned for the sequence value (so that @ref cop_as() need not be used)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_seq_currval(String seq, String as) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_seq_currval", seq, as);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "seq_currval" operator with the given argument giving the sequence name whose current value should be returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_seq_currval("xid", "xis"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param seq the name of the sequence whose current value should be returned

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_seq_currval(String seq) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_seq_currval", seq);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "coalesce" operator with the given column arguments; the first non-NULL column value will be returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_colesce("first_name", "last_name"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param col1 the name or column operator hash for the first value
        @param col2 the name or column operator hash for the second value, additional values should follow this argument

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @throw COALESCE-ERROR the arguments must be either string column designators or column operator hashes
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_coalesce(Object col1, Object col2) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_coalesce", col1, col2);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "coalesce" operator with the given column arguments; the first non-NULL column value will be returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_colesce("first_name", "last_name"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param col1 the name or column operator hash for the first value
        @param col2 the name or column operator hash for the second value, additional values should follow this argument
        @param args other column names or column operator hashes for subsequent values

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @throw COALESCE-ERROR the arguments must be either string column designators or column operator hashes
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_coalesce(Object col1, Object col2, Object... args) throws Throwable {
        Object[] new_args = new Object[args.length + 2];
        new_args[0] = col1;
        new_args[1] = col2;
        System.arraycopy(args, 0, new_args, 2, args.length);
        return (HashMap<String, Object>)QoreJavaApi.callFunctionArgs("cop_coalesce", new_args);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "substr" operator with the given arguments; returns a substring of a column value
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_substr("name", 1, 1));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)
        @param start position where the substring starts
        @param count length of the substring in characters

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_substr(Object column, int start, int count) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_substr", column, start, count);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "substr" operator with the given arguments; returns a substring of a column value
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_substr("name", 1, 1));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)
        @param start position where the substring starts
        @param count length of the substring in characters

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_substr(Object column, long start, long count) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_substr", column, start, count);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "substr" operator with the given arguments; returns a substring of a column value
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_substr("name", 1));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)
        @param start position where the substring starts

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_substr(Object column, int start) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_substr", column, start);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "substr" operator with the given arguments; returns a substring of a column value
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_substr("name", 1));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)
        @param start position where the substring starts

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_substr(Object column, long start) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_substr", column, start);
    }

    //! returns a @ref SqlUtil::ColumnOperatorInfo hash for the \c "len" operator with the given argument; returns the length of the given text field
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_length("product_code"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)

        @return a @ref SqlUtil::ColumnOperatorInfo hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @since %SqlUtil 1.3.1
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_length(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_length", column);
    }

    //! Truncates a date column or value regarding the given mask. The resulting value remains Qore::date (no conversion to eg. string)
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_trunc_date("mydate", AbstractTable.DT_MINUTE));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// input: 2017-02-01 14:22:37
// output 2017-02-01 14:22:00
        @endcode

        @param column the column specification for the column (String name or dot notation for use in joins)
        @param mask the string with one of specified values rederenced in @ref sql_cop_trunc_date_enum

        @return a column operator description hash corresponding to the arguments for use in the @ref select_option_columns "columns" argument of a @ref select_option_hash "select option hash"

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_trunc_date(Object column, String mask) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_trunc_date", column, mask);
    }

    //! Analytic/window method: relative rank of the current row
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_cume_dist(), "row_type", "id"), "cume_dist"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
// select cume_dist() over (partition by row_type order by id) as "cume_dist" from test_analytic_methods where type = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @return relative rank of the current row: (number of rows preceding or peer with current row) / (total rows)

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_cume_dist() throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_cume_dist");
    }

    //! Analytic/window method: rank of the current row without gaps
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_dense_rank(), "row_type", "id"), "dense_rank"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
select dense_rank() over (partition by row_type order by id) as "dense_rank" from test_analytic_methods where type = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @return rank of the current row without gaps; this method counts peer groups

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_dense_rank() throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_dense_rank");
    }

    //! Analytic/window method: value evaluated at the row that is the first row of the window frame
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_first_value("row_value"), "row_type", "id"), "first_value"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
select first_value(row_value) over (partition by row_type order by id) as "first_value" from test_analytic_methods where test = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @return returns value evaluated at the row that is the first row of the window frame

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_first_value(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_first_value", column);
    }

    //! Analytic/window method: value evaluated at the row that is the last row of the window frame
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_last_value("row_value"), "row_type", "id"), "last_value"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
select last_value(row_value) over (partition by row_type order by id) as "last_value" from test_analytic_methods where type = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @return returns value evaluated at the row that is the last row of the window frame

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_last_value(Object column) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_last_value", column);
    }

    //! Analytic/window method: integer ranging from 1 to the argument value, dividing the partition as equally as possible
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_ntile(10), "row_type", "id"), "ntile"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
select ntile(10) over (partition by row_type order by id) as "ntile" from test_analytic_methods where type = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @param value an integer value used as count of sp;it buckets

        @return integer ranging from 1 to the argument value, dividing the partition as equally as possible

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_ntile(int value) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_ntile", value);
    }

    //! Analytic/window method: integer ranging from 1 to the argument value, dividing the partition as equally as possible
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_ntile(10), "row_type", "id"), "ntile"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
select ntile(10) over (partition by row_type order by id) as "ntile" from test_analytic_methods where type = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @param value an integer value used as count of sp;it buckets

        @return integer ranging from 1 to the argument value, dividing the partition as equally as possible

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_ntile(long value) throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_ntile", value);
    }

    //! Analytic/window method: relative rank of the current row
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_percent_rank(), "row_type", "id"), "percent_rank"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
select percent_rank() over (partition by row_type order by id) as "percent_rank" from test_analytic_methods where type = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @return relative rank of the current row: (rank - 1) / (total rows - 1)

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_percent_rank() throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_percent_rank");
    }

    //! Analytic/window method: rank of the current row with gaps
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_rank(), "row_type", "id"), "rank"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
select rank() over (partition by row_type order by id) as "rank" from test_analytic_methods where type = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @return rank of the current row with gaps; same as row_number of its first peer

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_rank() throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_rank");
    }

    //! Analytic/window method: number of the current row within its partition, counting from 1
    /** @par Example:
        @code{.java}
HashMap<String, Object> wh = new HashMap<String, Object>() {
    put("type", "user");
};

HashMap<String, Object> sh = new HashMap<String, Object>() {
    put("columns", AbstractTable.cop_as(cop_over(cop_row_number(), "row_type", "id"), "row_number"));
    put("where", wh);
};

HashMap<String, Object> rows = t.selectRows(sh);
// rendered SQL statement
select row_number() over (partition by row_type order by id) as "row_number" from test_analytic_methods where type = 'user';
        @endcode

        Analytic/window method. Must be used with @ref cop_over() with \c partitionby and \c orderby arguments

        @note MySQL DB family: This analytic method is available only in MariaDB 10.2 and later only.

        @return number of the current row within its partition, counting from 1

        @since %SqlUtil 1.4.0
    */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> cop_row_number() throws Throwable {
        return (HashMap<String, Object>)QoreJavaApi.callFunction("cop_row_number");
    }
    ///@}

    /** @defgroup upsert_results Upsert Result Codes
        @see @ref UpsertResultMap and @ref UpsertResultDescriptionMap
        */
    ///@{
    //! row was inserted
    public static int UR_Inserted = 1;

    //! row was updated unconditionally (not returned with @ref UpsertSelectFirst)
    public static int UR_Verified = 2;

    //! row was updated because it was different (only possible with @ref UpsertSelectFirst)
    public static int UR_Updated = 3;

    //! row was unchanged (only possible with @ref UpsertSelectFirst, @ref UpsertInsertOnly, and @ref UpsertUpdateOnly)
    public static int UR_Unchanged = 4;

    //! row was deleted (only possible with batch upsert methods such as @ref SqlUtil::AbstractTable::upsertFromIterator() where @ref UpsertOptions "upsert option" \c delete_others is true)
    public static int UR_Deleted = 5;
    ///@}

    //! hash mapping upsert results to a description
    /** @see @ref UpsertResultDescriptionMap for a reverse mapping
    */
    public static final Map<Integer, String> UpsertResultMap = Collections.unmodifiableMap(new HashMap<Integer, String>() {
        {
            put(UR_Inserted, "inserted");
            put(UR_Verified, "verified");
            put(UR_Updated, "updated");
            put(UR_Unchanged, "unchanged");
            put(UR_Deleted, "deleted");
        }
    });

    //! hash mapping upsert descriptions to codes
    /** @see @ref UpsertResultMap for a reverse mapping
    */
    public static final Map<String, Integer> UpsertResultDescriptionMap = Collections.unmodifiableMap(new HashMap<String, Integer>() {
        {
            put("inserted", UR_Inserted);
            put("verified", UR_Verified);
            put("updated", UR_Updated);
            put("unchanged", UR_Unchanged);
            put("deleted", UR_Deleted);
        }
    });

    //! maps upsert result codes to single letter symbols
    public static final Map<Integer, String> UpsertResultLetterMap = Collections.unmodifiableMap(new HashMap<Integer, String>() {
        {
            put(UR_Inserted, "I");
            put(UR_Verified, "V");
            put(UR_Updated, "U");
            put(UR_Unchanged, ".");
            put(UR_Deleted, "X");
        }
    });

    //! creates the object as a wrapper for the Qore object
    public AbstractTable(QoreObject obj) {
        super(obj);
    }

    //! returns the name of the table
    public String getName() throws Throwable {
        return (String)obj.callMethod("getName");
    }

    //! finds a row in the table with the given primary key value; if no row matches the primary key value passed then null is returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> row = table.find(id);
        @endcode

        @throw PRIMARY-KEY-ERROR the table has no primary key or the primary key has more than one column
    */
    @SuppressWarnings("unchecked")
    HashMap<String, Object> find(Object id) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("find", id);
    }

    //! finds rows in the table with the given primary key values; if no row matches any primary key value passed then null is returned
    /** @par Example:
        @code{.java}
HashMap<String, Object>[] rows = table.find(list);
        @endcode

        @param ids the list of primary key IDs to find; if the list is empty then null is returned

        @return a list of hashes of rows matching the primary key IDs passed or null if no row matches any primary key value passed

        @throw PRIMARY-KEY-ERROR the table has no primary key or the primary key has more than one column
    */
    @SuppressWarnings("unchecked")
    HashMap<String, Object>[] find(Object[] ids) throws Throwable {
        return (HashMap<String, Object>[])obj.callMethod("find", (Object)ids);
    }

    //! finds a row in the table with the given primary key value given as a hash; if no row matches the primary key value passed then null is returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> row = table.find(row);
        @endcode

        @param row a hash giving the primary key value to find; other columns may also appear in the hash, however at least all columns of the primary key must be present

        @return a hash of the row value matching the primary key value passed or null if no row matches the primary key value passed

        @throw PRIMARY-KEY-ERROR the table has no primary key or the the hash passed does not contain all columns of the primary key

        @note a table with a primary key with a single column can also be used with this method; just pass a hash with one key
    */
    @SuppressWarnings("unchecked")
    HashMap<String, Object> find(Map<String, Object> row) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("find", row);
    }

    //! finds a single row in the table that match the row condition passed; multiple rows may match, but only one row will be returned from the database; if no row matches the condition hash passed then null is returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> row = table.findSingle(h);
        @endcode

        @param cond a hash giving the column values to find; see @ref where_clauses for the format of this argument

        @return a hash representing a single row in the table with the given column values; multiple rows may match, but only one row will be returned from the database; if no row matches the condition hash passed then null is returned

        @throw WHERE-ERROR unknown operator or invalid arguments given in the cond hash for the where clause

        @note this is equivalent to calling selectRow() with \c where = \c cond and \c limit = 1
     */
    @SuppressWarnings("unchecked")
    HashMap<String, Object> findSingle(Map<String, Object> cond) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("findSingle", cond);
    }

    //! reeturns the first row in the table immediately; one and only one row will be returned from the database; if the table is empty, then null is returned
    /** @par Example:
        @code{.java}
HashMap<String, Object> row = table.findSingle();
        @endcode

        @return the first row in the table; if the table is empty, then null is returned

        @note this is equivalent to calling selectRow() with \c limit = 1
     */
    @SuppressWarnings("unchecked")
    HashMap<String, Object> findSingle() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("findSingle");
    }

    //! finds all rows in the table with the given column values; a list of hashes is returned representing the rows returned
    /** @par Example:
        @code{.java}
HashMap<String, Object>[] rows = table.findAll(h);
        @endcode

        @param cond a hash giving the column values to find; see @ref where_clauses for the format of this argument

        @return a list of hashes is returned representing the rows returned

        @throw WHERE-ERROR unknown operator or invalid arguments given in the cond hash for the where clause

        @note this is equivalent to calling selectRows() with \c where = \c cond
    */
    @SuppressWarnings("unchecked")
    HashMap<String, Object>[] findAll(Map<String, Object> cond) throws Throwable {
        return (HashMap<String, Object>[])obj.callMethod("findAll", cond);
    }

    //! finds all rows in the table with the given column values; a list of hashes is returned representing the rows returned
    /** @par Example:
        @code{.java}
HashMap<String, Object>[] rows = table.findAll();
        @endcode

        @param cond a hash giving the column values to find; see @ref where_clauses for the format of this argument

        @return a list of hashes is returned representing the rows returned

        @throw WHERE-ERROR unknown operator or invalid arguments given in the cond hash for the where clause

        @note this is equivalent to calling selectRows() with \c where = \c cond
    */
    @SuppressWarnings("unchecked")
    HashMap<String, Object>[] findAll() throws Throwable {
        return (HashMap<String, Object>[])obj.callMethod("findAll");
    }

    //! returns a descriptive string of the datasource (without the password) and the table name (with a possible qualifier for schema, etc)
    /** Used in exception descriptions

        @since SqlUtil 1.3
    */
    public String getDesc() throws Throwable {
        return (String)obj.callMethod("getDesc");
    }

    //! returns the base type of the underlying object (normally \"table\", some DB-specific implementations may support others like \c "view")
    public String getBaseType() throws Throwable {
        return (String)obj.callMethod("getBaseType");
    }

    //! returns the name of the table to be used in SQL (with a possible qualifier for schema, etc)
    public String getSqlName() throws Throwable {
        return (String)obj.callMethod("getSqlName");
    }

    //! returns the column name for use in SQL strings; subclasses can return a special string in case the column name is a reserved word
    public String getColumnSqlName(String col) throws Throwable {
        return (String)obj.callMethod("getColumnSqlName", col);
    }

    //! returns a list of column names for use in SQL strings; subclasses can process the argument list in case a column name is a reserved word
    String[] getColumnSqlNames(String[] cols) throws Throwable {
        return (String[])obj.callMethod("getColumnSqlNames", (Object)cols);
    }

    //! commits the current transaction on the underlying @ref org.qore.lang.AbstractDatasource
    public void commit() throws Throwable {
        obj.callMethod("commit");
    }

    //! rolls back the current transaction on the underlying @ref org.qore.lang.AbstractDatasource
    public void rollback() throws Throwable {
        obj.callMethod("rollback");
    }

    //! returns true if the table has been read from or created in the database, false if not
    /** @par Example:
        @code{.java}
boolean b = table.inDb();
        @endcode

        @return true if the table has been read from or created in the database, false if not

        @note this method only returns a flag if the object contains configuration already retrieved from the database, @see checkExistence() for a method that will check the database if the table exists in case the table is not already known to exist in the database
    */
    public boolean inDb() throws Throwable {
        return (boolean)obj.callMethod("inDb");
    }

    //! drops the table from the database without any transaction management
    /** @par Example:
        @code{.java}
table.drop();
        @endcode

        @param opt optional callback options; see @ref CallbackOptions for more info

        @throw OPTION-ERROR invalid or unknown callback option

        @note Transaction management is normally not performed when dropping tables, however this method uses the org.qore.lang.AbstractDatasource.exec() method, which normally participates in acquiring a transaction lock for the underlying datasource object; therefore after this method executes normally the transaction lock will be dedicated to the calling thread.
     */
    public void drop(HashMap<String, Object> opt) throws Throwable {
        obj.callMethod("drop", opt);
    }

        //! drops the table from the database without any transaction management
    /** @par Example:
        @code{.java}
table.drop();
        @endcode

        @throw OPTION-ERROR invalid or unknown callback option

        @note Transaction management is normally not performed when dropping tables, however this method uses the org.qore.lang.AbstractDatasource.exec() method, which normally participates in acquiring a transaction lock for the underlying datasource object; therefore after this method executes normally the transaction lock will be dedicated to the calling thread.
     */
    public void drop() throws Throwable {
        obj.callMethod("drop");
    }

    //! executes some SQL with optional arguments so that if an error occurs the current transaction state is not lost
    /** @par Example:
        @code{.java}
t.tryExec("drop table tmp_table");
        @endcode

        Include any arguments in the parameter list after the \a sql argument

        @param sql the SQL to execute

        @return any return value from the SQL command executed
     */
    public Object tryExec(String sql) throws Throwable {
        return obj.callMethod("tryExec", sql);
    }

    //! executes some SQL with optional arguments so that if an error occurs the current transaction state is not lost
    /** @par Example:
        @code{.java}
t.tryExec("delete from tmp_table where id = %v and name = %v", arglist);
        @endcode

        @param sql the SQL to execute
        @param args the bind / placeholder or other arguments corresponding to the SQL string

        @return any return value from the SQL command executed
     */
    public Object tryExecArgs(String sql, Object[] args) throws Throwable {
        return obj.callMethod("tryExecArgs", sql, (Object)args);
    }

    //! executes some SQL so that if an error occurs the current transaction state is not lost
    /** @par Example:
        @code{.java}
t.tryExecRaw("drop table tmp_table");
        @endcode

        Include any arguments in the parameter list after the \a sql argument

        @param sql the SQL to execute

        @return any return value from the SQL command executed
     */
    public Object tryExecRaw(String sql) throws Throwable {
        return obj.callMethod("tryExecRaw", sql);
    }

    //! returns the sql required to drop the table; reimplement in subclasses if necessary
    /** @par Example:
        @code{.java}
list l = table.getDropSql();
        @endcode

        @param opt optional callback options; see @ref CallbackOptions for more info

        @return a list of strings that can be used to drop the table and any other objects assocatied with the table (for example: PostgreSQL table trigger method(s))

        @throw OPTION-ERROR invalid or unknown callback option
    */
    public String[] getDropSql(Map<String, Object> opt) throws Throwable {
        return (String[])obj.callMethod("getDropSql", opt);
    }

    //! returns the sql required to drop the table; reimplement in subclasses if necessary
    /** @par Example:
        @code{.java}
String[] l = table.getDropSql();
        @endcode

        @return a list of strings that can be used to drop the table and any other objects assocatied with the table (for example: PostgreSQL table trigger method(s))

        @throw OPTION-ERROR invalid or unknown callback option
    */
    public String[] getDropSql() throws Throwable {
        return (String[])obj.callMethod("getDropSql");
    }

    //! truncates all the table data without any transaction management
    /** @par Example:
        @code{.java}
table.truncate();
        @endcode

        @note Transaction management may not be applied when truncating tables depending on the database driver (for example truncating tables in Oracle does not participate in transaction management), however this method uses the org.qore.lang.AbstractDatasource.exec() method, which normally participates in acquiring a transaction lock for the underlying datasource object; therefore after this method executes normally the transaction lock will be dedicated to the calling thread.
     */
    public void truncate() throws Throwable {
        obj.callMethod("truncate");
    }

    //! gets the SQL that can be used to truncate the table
    /** @par Example:
        @code{.java}
String sql = table.getTruncateSql();
        @endcode

        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return the SQL that can be used to truncate the table

        @throw OPTION-ERROR invalid or unsupported option passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getTruncateSql(Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getTruncateSql", opt);
    }

    //! gets the SQL that can be used to truncate the table
    /** @par Example:
        @code{.java}
String sql = table.getTruncateSql();
        @endcode

        @return the SQL that can be used to truncate the table

        @throw OPTION-ERROR invalid or unsupported option passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getTruncateSql() throws Throwable {
        return (String)obj.callMethod("getTruncateSql");
    }

    //! creates the table with all associated properties (indexes, constraints, etc) without any transaction management
    /** @par Example:
        @code{.java}
table.create();
        @endcode

        @param opt a hash of options for the SQL creation strings

        @note Transaction management is normally not performed when creating tables, however this method uses the org.qore.lang.AbstractDatasource.exec() method, which normally participates in acquiring a transaction lock for the underlying datasource object; therefore after this method executes normally the transaction lock will be dedicated to the calling thread.

        @throw CREATE-TABLE-ERROR table has already been read from or created in the database
     */
    public void create(Map<String, Object> opt) throws Throwable {
        obj.callMethod("create", opt);
    }

    //! creates the table with all associated properties (indexes, constraints, etc) without any transaction management
    /** @par Example:
        @code{.java}
table.create();
        @endcode

        @note Transaction management is normally not performed when creating tables, however this method uses the org.qore.lang.AbstractDatasource.exec() method, which normally participates in acquiring a transaction lock for the underlying datasource object; therefore after this method executes normally the transaction lock will be dedicated to the calling thread.

        @throw CREATE-TABLE-ERROR table has already been read from or created in the database
     */
    public void create() throws Throwable {
        obj.callMethod("create");
    }

    //! returns true if the table has no data rows, false if not
    /** @par Example:
        @code{.java}
boolean b = table.emptyData();
        @endcode

        @return true if the table has no data rows, false if not

        @see
        - empty()
        - rowCount()
    */
    public boolean emptyData() throws Throwable {
        return (boolean)obj.callMethod("emptyData");
    }

    //! returns true if the table has no definitions, false if not
    /** @par Example:
        @code{.java}
boolean b = table.empty();
        @endcode

        @return true if the table has no definitions, false if not

        @see emptyData()
    */
    public boolean empty() throws Throwable {
        return (boolean)obj.callMethod("empty");
    }

    //! creates the object from a table description hash
    /** @param desc a @ref table_desc_hash "table description hash" describing the table
        @param opt an optional hash of options for the table creation string; see @ref SqlUtil::AbstractTable::TableOptions for common options; each driver can support additional driver-specific options

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw DESCRIPTION-ERROR invalid or unsupported description hash value passed
    */
    public void setupTable(Map<String, Object> desc, Map<String, Object> opt) throws Throwable {
        obj.callMethod("setupTable", desc, opt);
    }

    //! creates the object from a table description hash
    /** @param desc a @ref table_desc_hash "table description hash" describing the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw DESCRIPTION-ERROR invalid or unsupported description hash value passed
    */
    public void setupTable(Map<String, Object> desc) throws Throwable {
        obj.callMethod("setupTable", desc);
    }

    //! adds a column to the table; if the table is already known to be in the database, then it is added in the database also immediately; otherwise it is only added internally and can be created when create() is called for example
    /** @par Example:
        @code{.java}
table.addColumn("name", column_hash, false);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the column
        @param opt a hash describing the column; the following keys are permitted (other column options may be supported depending on the underlying AbstractTable implementation):
        - \c qore_type: a qore type string that will be converted to a native DB type with some default conversion;
        - \c native_type: the native database column type; if both \c native_type and \c qore_type are given then \c native_type is used
        - \c size: for data types requiring a size component, the size; for numeric columns this represents the precision for example
        - \c scale: for numeric data types, this value gives the scale
        - \c default_value: the default value for the column
        - \c default_value_native: a boolean flag to say if a \c default_value should be validated against table column type (False) or used as it is (True) to allow to use DBMS native methods or features. Defaults to False. It is strongly recommended to use \c default_value_native for \c default_value in \c driver specific sub-hash to avoid non-portable schema hashes
        @param nullable if true then the column can hold NULL values; note that primary key columns cannot be nullable

        @throw COLUMN-ERROR no \c native_type or \c qore_type keys in column option hash, column already exists, invalid column data

        @note make sure and add a \c default_value value when adding a column with a \c "not null" constraint with existing data

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public void addColumn(String cname, Map<String, Object> opt, boolean nullable) throws Throwable {
        obj.callMethod("addColumn", cname, opt, nullable);
    }

    //! adds a nullable column to the table; if the table is already known to be in the database, then it is added in the database also immediately; otherwise it is only added internally and can be created when create() is called for example
    /** @par Example:
        @code{.java}
table.addColumn("name", column_hash, false);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the column
        @param opt a hash describing the column; the following keys are permitted (other column options may be supported depending on the underlying AbstractTable implementation):
        - \c qore_type: a qore type string that will be converted to a native DB type with some default conversion;
        - \c native_type: the native database column type; if both \c native_type and \c qore_type are given then \c native_type is used
        - \c size: for data types requiring a size component, the size; for numeric columns this represents the precision for example
        - \c scale: for numeric data types, this value gives the scale
        - \c default_value: the default value for the column
        - \c default_value_native: a boolean flag to say if a \c default_value should be validated against table column type (False) or used as it is (True) to allow to use DBMS native methods or features. Defaults to False. It is strongly recommended to use \c default_value_native for \c default_value in \c driver specific sub-hash to avoid non-portable schema hashes

        @throw COLUMN-ERROR no \c native_type or \c qore_type keys in column option hash, column already exists, invalid column data

        @note make sure and add a \c default_value value when adding a column with a \c "not null" constraint with existing data

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public void addColumn(String cname, Map<String, Object> opt) throws Throwable {
        obj.callMethod("addColumn", cname, opt);
    }

    //! returns a list of SQL strings that can be use to add a column to the table
    /** @par Example:
        @code{.java}
String[] l = table.getAddColumnSql("name", copt, false);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the column
        @param copt a hash describing the column; the following keys are permitted (other column options may be supported depending on the underlying AbstractTable implementation):
        - \c qore_type: a qore type String that will be converted to a native DB type with some default conversion;
        - \c native_type: the native database column type; if both \c native_type and \c qore_type are given then \c native_type is used
        - \c size: for data types requiring a size component, the size; for numeric columns this represents the precision for example
        - \c scale: for numeric data types, this value gives the scale
        - \c default_value: the default value for the column
        - \c default_value_native: a boolean flag to say if a \c default_value should be validated against table column type (false) or used as it is (true) to allow to use DBMS native methods or features. Defaults to false. It is strongly recommended to use \c default_value_native for \c default_value in \c driver specific sub-hash to avoid non-portable schema hashes
        @param nullable if true then the column can hold NULL values; note that primary key columns cannot be nullable
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return a list of SQL strings that can be use to add a column to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR no \c native_type or \c qore_type keys in column option hash, column already exists, invalid column data

        @note
        - make sure and add a \c default_value value when adding a column with a \c "not null" constraint with existing data
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getAddColumnSql(String cname, Map<String, Object> copt, boolean nullable, Map<String, Object> opt) throws Throwable {
        return (String[])obj.callMethod("getAddColumnSql", cname, copt, nullable, opt);
    }

    //! returns a list of SQL strings that can be use to add a column to the table
    /** @par Example:
        @code{.java}
String[] l = table.getAddColumnSql("name", copt, false);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the column
        @param copt a hash describing the column; the following keys are permitted (other column options may be supported depending on the underlying AbstractTable implementation):
        - \c qore_type: a qore type String that will be converted to a native DB type with some default conversion;
        - \c native_type: the native database column type; if both \c native_type and \c qore_type are given then \c native_type is used
        - \c size: for data types requiring a size component, the size; for numeric columns this represents the precision for example
        - \c scale: for numeric data types, this value gives the scale
        - \c default_value: the default value for the column
        - \c default_value_native: a boolean flag to say if a \c default_value should be validated against table column type (false) or used as it is (true) to allow to use DBMS native methods or features. Defaults to false. It is strongly recommended to use \c default_value_native for \c default_value in \c driver specific sub-hash to avoid non-portable schema hashes
        @param nullable if true then the column can hold NULL values; note that primary key columns cannot be nullable

        @return a list of SQL strings that can be use to add a column to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR no \c native_type or \c qore_type keys in column option hash, column already exists, invalid column data

        @note
        - make sure and add a \c default_value value when adding a column with a \c "not null" constraint with existing data
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getAddColumnSql(String cname, Map<String, Object> copt, boolean nullable) throws Throwable {
        return (String[])obj.callMethod("getAddColumnSql", cname, copt, nullable);
    }

    //! returns a list of SQL strings that can be use to add a nullable column to the table
    /** @par Example:
        @code{.java}
String[] l = table.getAddColumnSql("name", copt);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the column
        @param copt a hash describing the column; the following keys are permitted (other column options may be supported depending on the underlying AbstractTable implementation):
        - \c qore_type: a qore type String that will be converted to a native DB type with some default conversion;
        - \c native_type: the native database column type; if both \c native_type and \c qore_type are given then \c native_type is used
        - \c size: for data types requiring a size component, the size; for numeric columns this represents the precision for example
        - \c scale: for numeric data types, this value gives the scale
        - \c default_value: the default value for the column
        - \c default_value_native: a boolean flag to say if a \c default_value should be validated against table column type (false) or used as it is (true) to allow to use DBMS native methods or features. Defaults to false. It is strongly recommended to use \c default_value_native for \c default_value in \c driver specific sub-hash to avoid non-portable schema hashes

        @return a list of SQL strings that can be use to add a column to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR no \c native_type or \c qore_type keys in column option hash, column already exists, invalid column data

        @note
        - make sure and add a \c default_value value when adding a column with a \c "not null" constraint with existing data
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getAddColumnSql(String cname, Map<String, Object> copt) throws Throwable {
        return (String[])obj.callMethod("getAddColumnSql", cname, copt);
    }

    //! gets a list of SQL strings that can be used to modify an existing column in the table
    /** @par Example:
        @code{.java}
String[] l = table.getModifyColumnSql("name", copt, false);
        @endcode

        @param cname the name of the column
        @param copt a hash describing the column; the following keys are permitted (other column options may be supported depending on the underlying AbstractTable implementation):
        - \c qore_type: a qore type String that will be converted to a native DB type with some default conversion;
        - \c native_type: the native database column type; if both \c native_type and \c qore_type are given then \c native_type is used
        - \c size: for data types requiring a size component, the size; for numeric columns this represents the precision for example
        - \c scale: for numeric data types, this value gives the scale
        - \c default_value: the default value for the column
        - \c default_value_native: a boolean flag to say if a \c default_value should be validated against table column type (false) or used as it is (true) to allow to use DBMS native methods or features. Defaults to false. It is strongly recommended to use \c default_value_native for \c default_value in \c driver specific sub-hash to avoid non-portable schema hashes
        @param nullable if true then the column can hold NULL values; note that primary key columns cannot be nullable
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return a list of SQL strings that can be used to modify an existing column in the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR no \c native_type or \c qore_type keys in column option hash, column does not exist, invalid column data

        @note
        - make sure and add a \c default_value value when modifying a column to have a \c "not null" constraint with existing data
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getModifyColumnSql(String cname, Map<String, Object> copt, boolean nullable, Map<String, Object> opt) throws Throwable {
        return (String[])obj.callMethod("getModifyColumnSql", cname, copt, nullable, opt);
    }

    //! gets a list of SQL strings that can be used to modify an existing column in the table
    /** @par Example:
        @code{.java}
String[] l = table.getModifyColumnSql("name", copt, false);
        @endcode

        @param cname the name of the column
        @param copt a hash describing the column; the following keys are permitted (other column options may be supported depending on the underlying AbstractTable implementation):
        - \c qore_type: a qore type String that will be converted to a native DB type with some default conversion;
        - \c native_type: the native database column type; if both \c native_type and \c qore_type are given then \c native_type is used
        - \c size: for data types requiring a size component, the size; for numeric columns this represents the precision for example
        - \c scale: for numeric data types, this value gives the scale
        - \c default_value: the default value for the column
        - \c default_value_native: a boolean flag to say if a \c default_value should be validated against table column type (false) or used as it is (true) to allow to use DBMS native methods or features. Defaults to false. It is strongly recommended to use \c default_value_native for \c default_value in \c driver specific sub-hash to avoid non-portable schema hashes
        @param nullable if true then the column can hold NULL values; note that primary key columns cannot be nullable

        @return a list of SQL strings that can be used to modify an existing column in the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR no \c native_type or \c qore_type keys in column option hash, column does not exist, invalid column data

        @note
        - make sure and add a \c default_value value when modifying a column to have a \c "not null" constraint with existing data
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getModifyColumnSql(String cname, Map<String, Object> copt, boolean nullable) throws Throwable {
        return (String[])obj.callMethod("getModifyColumnSql", cname, copt, nullable);
    }

    //! gets a list of SQL strings that can be used to modify an existing column in the table
    /** @par Example:
        @code{.java}
String[] l = table.getModifyColumnSql("name", copt, false);
        @endcode

        @param cname the name of the column
        @param copt a hash describing the column; the following keys are permitted (other column options may be supported depending on the underlying AbstractTable implementation):
        - \c qore_type: a qore type String that will be converted to a native DB type with some default conversion;
        - \c native_type: the native database column type; if both \c native_type and \c qore_type are given then \c native_type is used
        - \c size: for data types requiring a size component, the size; for numeric columns this represents the precision for example
        - \c scale: for numeric data types, this value gives the scale
        - \c default_value: the default value for the column
        - \c default_value_native: a boolean flag to say if a \c default_value should be validated against table column type (false) or used as it is (true) to allow to use DBMS native methods or features. Defaults to false. It is strongly recommended to use \c default_value_native for \c default_value in \c driver specific sub-hash to avoid non-portable schema hashes

        @return a list of SQL strings that can be used to modify an existing column in the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR no \c native_type or \c qore_type keys in column option hash, column does not exist, invalid column data

        @note
        - make sure and add a \c default_value value when modifying a column to have a \c "not null" constraint with existing data
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getModifyColumnSql(String cname, Map<String, Object> copt) throws Throwable {
        return (String[])obj.callMethod("getModifyColumnSql", cname, copt);
    }

    //! gets an SQL String that can be used to rename an existing column in the table
    /** @par Example:
        @code{.java}
String sql = table.getRenameColumnSql("name", "family_name");
        @endcode

        @param old_name the current name of the column
        @param new_name the new name of the column
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return an SQL String that can be used to rename an existing column in the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR if the old column does not exist in the table or the new column already does

        @see getModifyColumnSql() for a method that allows the column definition to be updated

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
    */
    public String getRenameColumnSql(String old_name, String new_name, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getRenameColumnSql", old_name, new_name, opt);
    }

    //! gets an SQL String that can be used to rename an existing column in the table
    /** @par Example:
        @code{.java}
String sql = table.getRenameColumnSql("name", "family_name");
        @endcode

        @param old_name the current name of the column
        @param new_name the new name of the column

        @return an SQL String that can be used to rename an existing column in the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR if the old column does not exist in the table or the new column already does

        @see getModifyColumnSql() for a method that allows the column definition to be updated

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
    */
    public String getRenameColumnSql(String old_name, String new_name) throws Throwable {
        return (String)obj.callMethod("getRenameColumnSql", old_name, new_name);
    }

    //! adds a primary key to the table; if the table is already known to be in the database, then it is added in the database also immediately; otherwise it is only added internally and can be created when create() is called for example
    /** @par Example:
        @code{.java}
table.addPrimaryKey("pk_mytable", "id");
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param pkname the name of the new primary key constraint
        @param columns a single column name or a list of columns that make up the primary key
        @param opt a hash of options for the new primary key; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ConstraintOptions

        @throw PRIMARY-KEY-ERROR the table already has a primary key or invalid columns or options passed

        @see inDb() for a method that tells if the table is already in the database or not
    */
    public void addPrimaryKey(String pkname, String[] columns, Map<String, Object> opt) throws Throwable {
        obj.callMethod("addPrimaryKey", pkname, columns, opt);
    }

    //! adds a primary key to the table; if the table is already known to be in the database, then it is added in the database also immediately; otherwise it is only added internally and can be created when create() is called for example
    /** @par Example:
        @code{.java}
table.addPrimaryKey("pk_mytable", "id");
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param pkname the name of the new primary key constraint
        @param columns a single column name or a list of columns that make up the primary key

        @throw PRIMARY-KEY-ERROR the table already has a primary key or invalid columns or options passed

        @see inDb() for a method that tells if the table is already in the database or not
    */
    public void addPrimaryKey(String pkname, String[] columns) throws Throwable {
        obj.callMethod("addPrimaryKey", pkname, columns);
    }

    //! returns the SQL that can be used to add a primary key to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddPrimaryKeySql("pk_mytable", "id", pkopt, opt);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param pkname the name of the new primary key constraint
        @param cols a single column name or a list of columns that make up the primary key
        @param pkopt a hash of options for the new primary key; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ConstraintOptions
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return the SQL that can be used to add a primary key to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw PRIMARY-KEY-ERROR the table already has a primary key or invalid columns or options passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddPrimaryKeySql(String pkname, String[] cols, Map<String, Object> pkopt, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getAddPrimaryKeySql", pkname, cols, pkopt, opt);
    }

    //! returns the SQL that can be used to add a primary key to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddPrimaryKeySql("pk_mytable", "id", pkopt);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param pkname the name of the new primary key constraint
        @param cols a single column name or a list of columns that make up the primary key
        @param pkopt a hash of options for the new primary key; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ConstraintOptions

        @return the SQL that can be used to add a primary key to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw PRIMARY-KEY-ERROR the table already has a primary key or invalid columns or options passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddPrimaryKeySql(String pkname, String[] cols, Map<String, Object> pkopt) throws Throwable {
        return (String)obj.callMethod("getAddPrimaryKeySql", pkname, cols, pkopt);
    }

    //! returns the SQL that can be used to add a primary key to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddPrimaryKeySql("pk_mytable", "id");
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param pkname the name of the new primary key constraint
        @param cols a single column name or a list of columns that make up the primary key

        @return the SQL that can be used to add a primary key to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw PRIMARY-KEY-ERROR the table already has a primary key or invalid columns or options passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddPrimaryKeySql(String pkname, String[] cols) throws Throwable {
        return (String)obj.callMethod("getAddPrimaryKeySql", pkname, cols);
    }

    //! gets a list of SQL strings to drop all constraints and indexes with the given column name; if the column does not exist then an empty list is returned
    /** @par Example:
        @code{.java}
String[] strlist = table.getDropAllConstraintsAndIndexesOnColumnSql("status");
        @endcode

        @param cname the name of the column
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @throw OPTION-ERROR invalid or unsupported option passed

        @note
        - this method retrieves current primary key definition from the database if not already loaded or defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
    */
    public String[] getDropAllConstraintsAndIndexesOnColumnSql(String cname, Map<String, Object> opt) throws Throwable {
        return (String[])obj.callMethod("getDropAllConstraintsAndIndexesOnColumnSql", cname, opt);
    }

    //! gets a list of SQL strings to drop all constraints and indexes with the given column name; if the column does not exist then an empty list is returned
    /** @par Example:
        @code{.java}
String[] strlist = table.getDropAllConstraintsAndIndexesOnColumnSql("status");
        @endcode

        @param cname the name of the column

        @throw OPTION-ERROR invalid or unsupported option passed

        @note
        - this method retrieves current primary key definition from the database if not already loaded or defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
    */
    public String[] getDropAllConstraintsAndIndexesOnColumnSql(String cname) throws Throwable {
        return (String[])obj.callMethod("getDropAllConstraintsAndIndexesOnColumnSql", cname);
    }

    //! gets a list of SQL strings that can be used to drop the primary key from the table
    /** @par Example:
        @code{.java}
String[] l = table.getDropPrimaryKeySql();
        @endcode

        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return a list of SQL strings that can be used to drop the primary key from the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw PRIMARY-KEY-ERROR the table has no primary key

        @note
        - this method retrieves current primary key definition from the database if not already loaded or defined
        - if there are known foreign contraints on the primary key, SQL for dropping those constraints is also returned
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getDropPrimaryKeySql(Map<String, Object> opt) throws Throwable {
        return (String[])obj.callMethod("getDropPrimaryKeySql", opt);
    }

    //! gets a list of SQL strings that can be used to drop the primary key from the table
    /** @par Example:
        @code{.java}
String[] l = table.getDropPrimaryKeySql();
        @endcode

        @return a list of SQL strings that can be used to drop the primary key from the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw PRIMARY-KEY-ERROR the table has no primary key

        @note
        - this method retrieves current primary key definition from the database if not already loaded or defined
        - if there are known foreign contraints on the primary key, SQL for dropping those constraints is also returned
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getDropPrimaryKeySql() throws Throwable {
        return (String[])obj.callMethod("getDropPrimaryKeySql");
    }

    //! adds a unique constraint to the table; if the table is known to be in the database already, then the constraint is added to the database also immediately; otherwise it is only added internally and can be created when create() is called for example
    /** @par Example:
        @code{.java}
table.addUniqueConstraint("uk_mytable", "name", opt);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the new unique constraint
        @param cols a single column name or a list of columns that make up the unique constraint
        @param opt a hash of options for the new unique constraint; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ConstraintOptions


        @throw UNIQUE-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns passed
        @throw OPTION-ERROR invalid or unsupported option passed

        @see inDb() for a method that tells if the table is already in the database or not
    */
    public void addUniqueConstraint(String cname, String[] cols, Map<String, Object> opt) throws Throwable {
        obj.callMethod("addUniqueConstraint", cname, cols, opt);
    }

    //! returns an SQL String that can be used to add a unique constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddUniqueConstraintSql("uk_mytable", "name", ukopt);
        @endcode

        @param cname the name of the new unique constraint
        @param cols a single column name or a list of columns that make up the unique constraint
        @param ukopt a hash of options for the new unique constraint; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ConstraintOptions
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return an SQL String that can be used to add a unique constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw UNIQUE-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddUniqueConstraintSql(String cname, String[] cols, Map<String, Object> ukopt, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getAddUniqueConstraintSql", cname, cols, ukopt, opt);
    }

    //! returns an SQL String that can be used to add a unique constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddUniqueConstraintSql("name", ukopt);
        @endcode

        @param cname the name of the new unique constraint
        @param cols a single column name or a list of columns that make up the unique constraint
        @param ukopt a hash of options for the new unique constraint; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ConstraintOptions

        @return an SQL String that can be used to add a unique constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw UNIQUE-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddUniqueConstraintSql(String cname, String[] cols, Map<String, Object> ukopt) throws Throwable {
        return (String)obj.callMethod("getAddUniqueConstraintSql", cname, cols, ukopt);
    }

    //! returns an SQL String that can be used to add a unique constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddUniqueConstraintSql("name", cols);
        @endcode

        @param cname the name of the new unique constraint
        @param cols a single column name or a list of columns that make up the unique constraint

        @return an SQL String that can be used to add a unique constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw UNIQUE-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddUniqueConstraintSql(String cname, String[] cols) throws Throwable {
        return (String)obj.callMethod("getAddUniqueConstraintSql", cname, cols);
    }

    //! adds an index to the table; if the table is already known to be in the database, then it is added in the database also immediately; otherwise it is only added internally and can be created when create() is called for example
    /** @par Example:
        @code{.java}
table.addIndex("uk_mytable_name", true, new String[]{"name"}, opt);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param iname the name of the new index
        @param unique a flag to tell if the new index should be unique or not
        @param cols a single column name or a list of columns that make up the index
        @param opt a hash of options for the new index; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::IndexOptions

        @throw INDEX-ERROR the table already has an index with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public void addIndex(String iname, boolean unique, String[] cols, Map<String, Object> opt) throws Throwable {
        obj.callMethod("addIndex", iname, unique, cols, opt);
    }

    //! adds an index to the table; if the table is already known to be in the database, then it is added in the database also immediately; otherwise it is only added internally and can be created when create() is called for example
    /** @par Example:
        @code{.java}
table.addIndex("uk_mytable_name", true, new String[]{"name"});
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param iname the name of the new index
        @param unique a flag to tell if the new index should be unique or not
        @param cols a single column name or a list of columns that make up the index

        @throw INDEX-ERROR the table already has an index with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public void addIndex(String iname, boolean unique, String[] cols) throws Throwable {
        obj.callMethod("addIndex", iname, unique, cols);
    }

    //! returns an SQL String that can be used to add an index to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddIndexSql("uk_mytable_name", true, "name", ixopt);
        @endcode

        @param iname the name of the new index
        @param unique a flag to tell if the new index should be unique or not
        @param cols a single column name or a list of columns that make up the index
        @param ixopt a hash of options for the new index; each driver may implement its own options; for common options, see @ref IndexOptions
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return an SQL String that can be used to add an index to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw INDEX-ERROR the table already has an index with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddIndexSql(String iname, boolean unique, String[] cols, Map<String, Object> ixopt, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getAddIndexSql", iname, unique, cols, ixopt, opt);
    }

    //! returns an SQL String that can be used to add an index to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddIndexSql("uk_mytable_name", true, "name", ixopt);
        @endcode

        @param iname the name of the new index
        @param unique a flag to tell if the new index should be unique or not
        @param cols a single column name or a list of columns that make up the index
        @param ixopt a hash of options for the new index; each driver may implement its own options; for common options, see @ref IndexOptions

        @return an SQL String that can be used to add an index to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw INDEX-ERROR the table already has an index with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddIndexSql(String iname, boolean unique, String[] cols, Map<String, Object> ixopt) throws Throwable {
        return (String)obj.callMethod("getAddIndexSql", iname, unique, cols, ixopt);
    }

    //! returns an SQL String that can be used to add an index to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddIndexSql("uk_mytable_name", true, "name", ixopt);
        @endcode

        @param iname the name of the new index
        @param unique a flag to tell if the new index should be unique or not
        @param cols a single column name or a list of columns that make up the index

        @return an SQL String that can be used to add an index to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw INDEX-ERROR the table already has an index with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddIndexSql(String iname, boolean unique, String[] cols) throws Throwable {
        return (String)obj.callMethod("getAddIndexSql", iname, unique, cols);
    }

    //! gets the SQL that can be used to drop an index from the table
    /** @par Example:
        @code{.java}
String sql = table.getDropIndexSql("uk_mytable_name");
        @endcode

        @param iname the name of the index to drop
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return the SQL that can be used to drop an index from the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw INDEX-ERROR the given index does not exist in the table

        @note
        - this method retrieves current index definitions from the database if not already loaded or defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String getDropIndexSql(String iname, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getDropIndexSql", iname, opt);
    }

    //! gets the SQL that can be used to drop an index from the table
    /** @par Example:
        @code{.java}
String sql = table.getDropIndexSql("uk_mytable_name");
        @endcode

        @param iname the name of the index to drop

        @return the SQL that can be used to drop an index from the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw INDEX-ERROR the given index does not exist in the table

        @note
        - this method retrieves current index definitions from the database if not already loaded or defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String getDropIndexSql(String iname) throws Throwable {
        return (String)obj.callMethod("getDropIndexSql", iname);
    }

    //! returns an SQL String that can be used to add a foreign constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddForeignConstraintSql("fk_mytable_other_table", ("name", "version"), "other_table");
        @endcode

        @param cname the name of the new foreign constraint
        @param cols a single column name or a list of columns in the local table that make up the foreign constraint
        @param table the name of the other table that the constraint targets
        @param tcols a single column name or a list of columns in the foreign table or null meaning that the column names are the same as in the local table; if column names are given the same number of columns must be given in the local and foreign tables
        @param fkopt a hash of options for the new foreign constraint; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ForeignConstraintOptions
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return an SQL String that can be used to add a foreign constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw FOREIGN-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddForeignConstraintSql(String cname, String[] cols, String table, String[] tcols, Map<String, Object> fkopt, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getAddForeignConstraintSql", cname, cols, table, tcols, fkopt, opt);
    }

    //! returns an SQL String that can be used to add a foreign constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddForeignConstraintSql("fk_mytable_other_table", cols, "other_table");
        @endcode

        @param cname the name of the new foreign constraint
        @param cols a single column name or a list of columns in the local table that make up the foreign constraint
        @param table the name of the other table that the constraint targets
        @param tcols a single column name or a list of columns in the foreign table or null meaning that the column names are the same as in the local table; if column names are given the same number of columns must be given in the local and foreign tables
        @param fkopt a hash of options for the new foreign constraint; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ForeignConstraintOptions

        @return an SQL String that can be used to add a foreign constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw FOREIGN-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddForeignConstraintSql(String cname, String[] cols, String table, String[] tcols, Map<String, Object> fkopt) throws Throwable {
        return (String)obj.callMethod("getAddForeignConstraintSql", cname, cols, table, tcols, fkopt);
    }

    //! returns an SQL String that can be used to add a foreign constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddForeignConstraintSql("fk_mytable_other_table", cols, "other_table");
        @endcode

        @param cname the name of the new foreign constraint
        @param cols a single column name or a list of columns in the local table that make up the foreign constraint
        @param table the name of the other table that the constraint targets
        @param tcols a single column name or a list of columns in the foreign table or null meaning that the column names are the same as in the local table; if column names are given the same number of columns must be given in the local and foreign tables

        @return an SQL String that can be used to add a foreign constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw FOREIGN-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddForeignConstraintSql(String cname, String[] cols, String table, String[] tcols) throws Throwable {
        return (String)obj.callMethod("getAddForeignConstraintSql", cname, cols, table, tcols);
    }

    //! returns an SQL String that can be used to add a foreign constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddForeignConstraintSql("fk_mytable_other_table", cols, "other_table");
        @endcode

        @param cname the name of the new foreign constraint
        @param cols a single column name or a list of columns in the local table that make up the foreign constraint
        @param table the name of the other table that the constraint targets

        @return an SQL String that can be used to add a foreign constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw FOREIGN-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddForeignConstraintSql(String cname, String[] cols, String table) throws Throwable {
        return (String)obj.callMethod("getAddForeignConstraintSql", cname, cols, table);
    }

    //! returns an SQL String that can be used to add a check constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddCheckConstraintSql("check_mytable_id", "id > 10");
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the new constraint
        @param src the source of the constraint clause
        @param copt a hash of options for the new constraint; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ConstraintOptions
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return an SQL String that can be used to add a check constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw CHECK-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddCheckConstraintSql(String cname, String src, Map<String, Object> copt, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getAddForeignConstraintSql", cname, src, copt, opt);
    }

    //! returns an SQL String that can be used to add a check constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddCheckConstraintSql("check_mytable_id", "id > 10");
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the new constraint
        @param src the source of the constraint clause
        @param copt a hash of options for the new constraint; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::ConstraintOptions

        @return an SQL String that can be used to add a check constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw CHECK-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddCheckConstraintSql(String cname, String src, Map<String, Object> copt) throws Throwable {
        return (String)obj.callMethod("getAddCheckConstraintSql", cname, src, copt);
    }

    //! returns an SQL String that can be used to add a check constraint to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddCheckConstraintSql("check_mytable_id", "id > 10");
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param cname the name of the new constraint
        @param src the source of the constraint clause

        @return an SQL String that can be used to add a check constraint to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw CHECK-CONSTRAINT-ERROR the table already has a constraint with the given name or invalid columns or options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String getAddCheckConstraintSql(String cname, String src) throws Throwable {
        return (String)obj.callMethod("getAddCheckConstraintSql", cname, src);
    }

    //! gets the SQL that can be used to drop a constraint from the table; this can be any constraint on the table, a primary key, a foreign key constraint, or a generic constraint
    /** @par Example:
        @code{.java}
String sql = table.getDropConstraintSql("uk_mytable_name");
        @endcode

        @param cname the name of the constraint to drop
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return the SQL that can be used to drop a constraint from the table; this can be any constraint on the table, a primary key, a foreign key constraint, or a generic constraint

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw CONSTRAINT-ERROR the given constraint does not exist in the table

        @note
        - this method retrieves current constraint definitions from the database if not already loaded or defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String getDropConstraintSql(String cname, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getDropConstraintSql", cname, opt);
    }

    //! gets the SQL that can be used to drop a constraint from the table; this can be any constraint on the table, a primary key, a foreign key constraint, or a generic constraint
    /** @par Example:
        @code{.java}
String sql = table.getDropConstraintSql("uk_mytable_name");
        @endcode

        @param cname the name of the constraint to drop

        @return the SQL that can be used to drop a constraint from the table; this can be any constraint on the table, a primary key, a foreign key constraint, or a generic constraint

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw CONSTRAINT-ERROR the given constraint does not exist in the table

        @note
        - this method retrieves current constraint definitions from the database if not already loaded or defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String getDropConstraintSql(String cname) throws Throwable {
        return (String)obj.callMethod("getDropConstraintSql", cname);
    }

    //! gets the SQL that can be used to drop a constraint from the table if it exists, otherwise returns null; this can be any constraint on the table, a primary key, a foreign key constraint, or a generic constraint
    /** @par Example:
        @code{.java}
String sql = table.getDropConstraintIfExistsSql("uk_mytable_name");
        @endcode

        @param cname the name of the constraint to drop
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return the SQL that can be used to drop a constraint from the table if it exists, otherwise returns null; this can be any constraint on the table, a primary key, a foreign key constraint, or a generic constraint

        @throw OPTION-ERROR invalid or unsupported option passed

        @note
        - this method retrieves current constraint definitions from the database if not already loaded or defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String getDropConstraintIfExistsSql(String cname, Map<String, Object> opt) throws Throwable {
        return (String)obj.callMethod("getDropConstraintIfExistsSql", cname, opt);
    }

    //! gets the SQL that can be used to drop a constraint from the table if it exists, otherwise returns null; this can be any constraint on the table, a primary key, a foreign key constraint, or a generic constraint
    /** @par Example:
        @code{.java}
String sql = table.getDropConstraintIfExistsSql("uk_mytable_name");
        @endcode

        @param cname the name of the constraint to drop

        @return the SQL that can be used to drop a constraint from the table if it exists, otherwise returns null; this can be any constraint on the table, a primary key, a foreign key constraint, or a generic constraint

        @throw OPTION-ERROR invalid or unsupported option passed

        @note
        - this method retrieves current constraint definitions from the database if not already loaded or defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String getDropConstraintIfExistsSql(String cname) throws Throwable {
        return (String)obj.callMethod("getDropConstraintIfExistsSql", cname);
    }

    //! returns a list of SQL strings that can be used to add a trigger to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddTriggerSql("trig_mytable", trigger_src);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param tname the name of the new trigger
        @param src the source of the trigger
        @param topt a hash of options for the new trigger; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::TriggerOptions
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return a list of SQL strings that can be used to add a trigger to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw TRIGGER-ERROR the table already has a trigger with the given name or invalid options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String[] getAddTriggerSql(String tname, String src, Map<String, Object> topt, Map<String, Object> opt) throws Throwable {
        return (String[])obj.callMethod("getAddTriggerSql", tname, src, topt, opt);
    }

    //! returns a list of SQL strings that can be used to add a trigger to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddTriggerSql("trig_mytable", trigger_src);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param tname the name of the new trigger
        @param src the source of the trigger
        @param topt a hash of options for the new trigger; each driver may implement its own options; for common options, see @ref SqlUtil::AbstractTable::TriggerOptions

        @return a list of SQL strings that can be used to add a trigger to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw TRIGGER-ERROR the table already has a trigger with the given name or invalid options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String[] getAddTriggerSql(String tname, String src, Map<String, Object> topt) throws Throwable {
        return (String[])obj.callMethod("getAddTriggerSql", tname, src, topt);
    }

    //! returns a list of SQL strings that can be used to add a trigger to the table
    /** @par Example:
        @code{.java}
String sql = table.getAddTriggerSql("trig_mytable", trigger_src);
        @endcode

        In case the table is already in the database, this method commits the transaction on success and rolls back the transaction if there's an error.

        @param tname the name of the new trigger
        @param src the source of the trigger

        @return a list of SQL strings that can be used to add a trigger to the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw TRIGGER-ERROR the table already has a trigger with the given name or invalid options were passed

        @see inDb() for a method that tells if the table is already in the database or not

        @note if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)
     */
    public String[] getAddTriggerSql(String tname, String src) throws Throwable {
        return (String[])obj.callMethod("getAddTriggerSql", tname, src);
    }

    //! returns SQL that can be used to drop the given trigger from the table
    /** @par Example:
        @code{.java}
String sql = table.getDropTriggerSql("trig_mytable");
        @endcode

        @param tname the name of the trigger to drop
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return the SQL that can be used to drop the given trigger from the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw TRIGGER-ERROR the given trigger does not exist

        @note
        - this method retrieves all current trigger definitions from the database if none have already been defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getDropTriggerSql(String tname, Map<String, Object> opt) throws Throwable {
        return (String[])obj.callMethod("getDropTriggerSql", tname, opt);
    }

    //! returns SQL that can be used to drop the given trigger from the table
    /** @par Example:
        @code{.java}
String sql = table.getDropTriggerSql("trig_mytable");
        @endcode

        @param tname the name of the trigger to drop

        @return the SQL that can be used to drop the given trigger from the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw TRIGGER-ERROR the given trigger does not exist

        @note
        - this method retrieves all current trigger definitions from the database if none have already been defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getDropTriggerSql(String tname) throws Throwable {
        return (String[])obj.callMethod("getDropTriggerSql", tname);
    }

    //! returns the SQL that can be used to drop a column from the table
    /** @par Example:
        @code{.java}
String sql = table.getDropColumnSql("notes_2");
        @endcode

        @param cname the name of the column to drop
        @param opt a hash of options for the SQL string; see @ref AlignTableOptions for common options; each driver can support additional driver-specific options

        @return the SQL that can be used to drop a column from the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR the named column is not present in the table

        @note
        - this method retrieves the column definitions from the database if none have already been defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getDropColumnSql(String cname, Map<String, Object> opt) throws Throwable {
        return (String[])obj.callMethod("getDropColumnSql", cname, opt);
    }

    //! returns the SQL that can be used to drop a column from the table
    /** @par Example:
        @code{.java}
String sql = table.getDropColumnSql("notes_2");
        @endcode

        @param cname the name of the column to drop

        @return the SQL that can be used to drop a column from the table

        @throw OPTION-ERROR invalid or unsupported option passed
        @throw COLUMN-ERROR the named column is not present in the table

        @note
        - this method retrieves the column definitions from the database if none have already been defined
        - if the @ref sql_callback_executed "sql_callback_executed option" is true in \a opt, then the changes are also effected in the current object, if not, then they are not (see @ref sql_callback_executed for more information)

        @see inDb() for a method that tells if the table is already in the database or not
     */
    public String[] getDropColumnSql(String cname) throws Throwable {
        return (String[])obj.callMethod("getDropColumnSql", cname);
    }

    //! inserts a row into the table without any transaction management; a transaction will be in progress after this method is successfully executed
    /** @par Insert operation
        @par Example:
        @code{.java}
table.insert(row);
        @endcode

        @param row a hash representing the row to insert; hash values can also be set with @ref sql_iop_funcs to insert values based on SQL operations to be used directly in the insert statement

        @return in case the \c "returning" @ref InsertOptions "insert option" is used, a hash of return values is returned, otherwise null is returned

        @throw COLUMN-ERROR an unknown column was referenced in the hash to be inserted
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> insert(Map<String, Object> row) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("insert", row);
    }

    //! @ref insert() variant
    /**
        @param row a hash representing the row to insert; hash values can also be set with @ref sql_iop_funcs to insert values based on SQL operations to be used directly in the insert statement
        @param opt optional insert options; see @ref InsertOptions for more info
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> insert(Map<String, Object> row, Map<String, Object> opt) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("insert", row, opt);
    }

    //! returns true if the current database driver supports the \c "returning" clause in insert statements, false if not
    /** @return true if the current database driver supports the \c "returning" clause in insert statements, false if not
        @since %SqlUtil 1.3
    */
    public boolean hasReturning() throws Throwable {
        return (boolean)obj.callMethod("hasReturning");
    }

    //! inserts rows into a table based on a select statement from another table (which must be using the same datasource as the current table); a transaction will be in progress after this method is successfully executed
    /** @par Example:
        @code{.java}
int rows = table.insertFromSelect(new String[]{"id", "name", "created"}, source_table, sh);
        @endcode

        @param cols the list of column names to use to insert in the current table
        @param source the source table for the select statement
        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument
        @param opt optional SQL data operation callback options; see @ref SqlUtil::AbstractTable::SqlDataCallbackOptions for more inf

        @return the number of rows inserted

        @throw OPTION-ERROR invalid or unsupported option
        @throw COLUMN-ERROR unknown or invalid column in insert list
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note this method does not take insert options because it is executed entirely in the database server; use insertFromIterator() or insertFromIteratorCommit() to insert arbitrary data with insert options
    */
    public int insertFromSelect(String[] cols, AbstractTable source, Map<String, Object> sh, Map<String, Object> opt) throws Throwable {
        return ((Long)obj.callMethod("insertFromSelect", cols, source, sh, opt)).intValue();
    }

    //! @ref insertFromSelect() variant
    public int insertFromSelect(String[] cols, AbstractTable source, Map<String, Object> sh) throws Throwable {
        return ((Long)obj.callMethod("insertFromSelect", cols, source, sh)).intValue();
    }

    //! @ref insertFromSelect() variant
    public int insertFromSelect(String[] cols, AbstractTable source) throws Throwable {
        return ((Long)obj.callMethod("insertFromSelect", cols, source)).intValue();
    }

    //! update or insert the data in the table according to the hash argument; the table must have a unique key to do this
    /** @par Example:
        @code{.java}
table.upsert(row);
        @endcode

        @param row a hash representing the row to insert or update
        @param upsert_strategy see @ref upsert_options for possible values for the upsert strategy
        @param opt a hash of options for the upsert operation; see @ref UpsertOptions for common options; each driver can support additional driver-specific options

        @return an integer code giving the result of the update; see @ref upsert_results for more information

        @throw COLUMN-ERROR an unknown column was referenced in the hash to be inserted
        @throw UPSERT-ERROR no primary key, unique constraint, or unique index for upsert; not all columns of the unique constraint/index are used in the upsert statement

        @note if upserting multiple rows; it's better to use getBulkUpsertClosure(), getUpsertClosure(), or getUpsertClosureWithValidation() and execute the closure on each row; when using this method, the overhead for setting up the upsert is made for each row which is very inefficient
     */
    public int upsert(Map<String, Object> row, int upsert_strategy, Map<String, Object> opt) throws Throwable {
        return ((Long)obj.callMethod("upsert", row, upsert_strategy, opt)).intValue();
    }

    //! update or insert the data in the table according to the hash argument; the table must have a unique key to do this
    /** @par Example:
        @code{.java}
table.upsert(row);
        @endcode

        @param row a hash representing the row to insert or update
        @param upsert_strategy see @ref upsert_options for possible values for the upsert strategy

        @return an integer code giving the result of the update; see @ref upsert_results for more information

        @throw COLUMN-ERROR an unknown column was referenced in the hash to be inserted
        @throw UPSERT-ERROR no primary key, unique constraint, or unique index for upsert; not all columns of the unique constraint/index are used in the upsert statement

        @note if upserting multiple rows; it's better to use getBulkUpsertClosure(), getUpsertClosure(), or getUpsertClosureWithValidation() and execute the closure on each row; when using this method, the overhead for setting up the upsert is made for each row which is very inefficient
     */
    public int upsert(Map<String, Object> row, int upsert_strategy) throws Throwable {
        return ((Long)obj.callMethod("upsert", row, upsert_strategy)).intValue();
    }

    //! this method upserts or merges data from the given foreign table and @ref select_option_hash "select option hash" into the current table; no transaction management is performed with this method
    /** @par Example:
        @code{.java}
Map<String, Object> h;
try {
    h = table.upsertFromSelect(table2, sh, AbstractTable.UpsertUpdateFirst);

    ds.commit();
} catch (Throwable e) {
    ds.collback();
    throw e;
}
        @endcode

        The table argument does not need to be in the same database as the current table; it can also
        be in a different database server or a database server of a different type (you can use this method to upsert or
        merge data to or from any database supported by SqlUtil).

        @param t the table for the source data; this does not need to be in the same database as the target (the current table), nor does it need to be the same database type
        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument
        @param upsert_strategy see @ref upsert_options for possible values for the upsert strategy
        @param opt a hash of options for the upsert operation; see @ref UpsertOptions for common options; each driver can support additional driver-specific options; note that this method ignores any \c "commit_block" option

        @return null if no actions were taken or a hash with the following keys assigned to numeric values indicating the number of rows processed (keys correspond to @ref UpsertResultDescriptionMap keys):
        - \c "inserted": the number of rows inserted
        - \c "verified": the number of rows updated unconditionally; note that this key is returned with all upsert strategy codes other than @ref UpsertSelectFirst instead of \c "updated"
        - \c "updated": the number of rows updated; note that this key is only returned if \a upsert_strategy is @ref UpsertSelectFirst, otherwise updated rows are reported as \c "verified" since rows are updated unconditionally with other the upsert strategy codes
        - \c "unchanged": the number of rows unchanged; this key can only be returned if \a upsert_strategy is @ref UpsertSelectFirst, @ref UpsertInsertOnly, or @ref UpsertUpdateOnly
        - \c "deleted": the number of rows deleted; this can only be returned if @ref UpsertOptions "upsert option" \c delete_others is true

        @throw OPTION-ERROR invalid or unsupported option
        @throw COLUMN-ERROR an unknown column was referenced in the hash to be inserted
        @throw UPSERT-ERROR no primary key, unique constraint, or unique index for upsert; not all columns of the unique constraint/index are used in the upsert statement

        @note
        - if @ref UpsertOptions "upsert option" \c delete_others is true, then a hash of primary key values in the input data is built as the input data is iterated.  After iterating, if the row count of the table and the input data matches, then nothing more is done, otherwise, every row of the table is iterated and compared to the primary key hash; if a row does not match a primary key value, then it is deleted.  This operation is only executed if \c delete_others is true and is expensive for large data sets.
       - this method uses an @ref Qore::SQL::AbstractSQLStatement "AbstractSQLStatement" object to pipeline the select data to the upsert code; to release the transaction lock acquired by the @ref Qore::SQL::AbstractSQLStatement "AbstractSQLStatement" object, a commit() or rollback() action must be executed on the underlying datasource object as in the example above
        - unlike insertFromSelect(), this method processes arbitrary input data and accepts @ref UpsertOptions "upsert options"

        @see
        - upsertFromSelect()
        - upsert()
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> upsertFromSelect(AbstractTable t, Map<String, Object> sh, int upsert_strategy, Map<String, Object> opt) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("upsertFromSelect", t, sh, upsert_strategy, opt);
    }

    //! this method upserts or merges data from the given foreign table and @ref select_option_hash "select option hash" into the current table; no transaction management is performed with this method
    /** @par Example:
        @code{.java}
Map<String, Object> h;
try {
    h = table.upsertFromSelect(table2, sh, AbstractTable.UpsertUpdateFirst);

    ds.commit();
} catch (Throwable e) {
    ds.collback();
    throw e;
}
        @endcode

        The table argument does not need to be in the same database as the current table; it can also
        be in a different database server or a database server of a different type (you can use this method to upsert or
        merge data to or from any database supported by SqlUtil).

        @param t the table for the source data; this does not need to be in the same database as the target (the current table), nor does it need to be the same database type
        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument
        @param upsert_strategy see @ref upsert_options for possible values for the upsert strategy

        @return null if no actions were taken or a hash with the following keys assigned to numeric values indicating the number of rows processed (keys correspond to @ref UpsertResultDescriptionMap keys):
        - \c "inserted": the number of rows inserted
        - \c "verified": the number of rows updated unconditionally; note that this key is returned with all upsert strategy codes other than @ref UpsertSelectFirst instead of \c "updated"
        - \c "updated": the number of rows updated; note that this key is only returned if \a upsert_strategy is @ref UpsertSelectFirst, otherwise updated rows are reported as \c "verified" since rows are updated unconditionally with other the upsert strategy codes
        - \c "unchanged": the number of rows unchanged; this key can only be returned if \a upsert_strategy is @ref UpsertSelectFirst, @ref UpsertInsertOnly, or @ref UpsertUpdateOnly
        - \c "deleted": the number of rows deleted; this can only be returned if @ref UpsertOptions "upsert option" \c delete_others is true

        @throw OPTION-ERROR invalid or unsupported option
        @throw COLUMN-ERROR an unknown column was referenced in the hash to be inserted
        @throw UPSERT-ERROR no primary key, unique constraint, or unique index for upsert; not all columns of the unique constraint/index are used in the upsert statement

        @note
        - if @ref UpsertOptions "upsert option" \c delete_others is true, then a hash of primary key values in the input data is built as the input data is iterated.  After iterating, if the row count of the table and the input data matches, then nothing more is done, otherwise, every row of the table is iterated and compared to the primary key hash; if a row does not match a primary key value, then it is deleted.  This operation is only executed if \c delete_others is true and is expensive for large data sets.
       - this method uses an @ref Qore::SQL::AbstractSQLStatement "AbstractSQLStatement" object to pipeline the select data to the upsert code; to release the transaction lock acquired by the @ref Qore::SQL::AbstractSQLStatement "AbstractSQLStatement" object, a commit() or rollback() action must be executed on the underlying datasource object as in the example above
        - unlike insertFromSelect(), this method processes arbitrary input data and accepts @ref UpsertOptions "upsert options"

        @see
        - upsertFromSelect()
        - upsert()
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> upsertFromSelect(AbstractTable t, Map<String, Object> sh, int upsert_strategy) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("upsertFromSelect", t, sh, upsert_strategy);
    }

    //! deletes rows in the table matching the condition and returns the count of rows deleted; no transaction management is performed with this method
    /** @par Example:
        @code{.java}
int dcnt = table.del(cond_hash);
        @endcode

        @param cond a hash of conditions for the where clause; see @ref where_clauses for more information
        @param opt optional SQL data operation callback options; see @ref SqlUtil::AbstractTable::SqlDataCallbackOptions for more info

        @return the count of rows deleted

        @throw WHERE-ERROR unknown operator or invalid arguments given in the cond hash for the where clause
    */
    public int del(HashMap<String, Object> cond, HashMap<String, Object> opt) throws Throwable {
        return ((Long)obj.callMethod("del", cond, opt)).intValue();
    }

    //! @ref del() variant
    public int del(HashMap<String, Object> cond) throws Throwable {
        return ((Long)obj.callMethod("del", cond)).intValue();
    }

    //! @ref del() variant
    public int del() throws Throwable {
        return ((Long)obj.callMethod("del")).intValue();
    }

    //! updates rows in the table matching an optional condition and returns the count of rows updated; no transaction management is performed with this method
    /** @par Example:
        @code{.java}
int ucnt = table.update(set_hash, cond_hash);
        @endcode

        @param set the hash of values to set, key values are column names, hash values are the values to assign to those columns or update operators (see @ref sql_uop_funcs)
        @param cond a hash of conditions for the where clause; see @ref where_clauses for more information
        @param opt optional SQL data operation callback options; see @ref SqlUtil::AbstractTable::SqlDataCallbackOptions for more info

        @return the count of rows updated

        @throw UPDATE-ERROR the set hash is empty
        @throw WHERE-ERROR unknown operator or invalid arguments given in the cond hash for the where clause
    */
    public int update(HashMap<String, Object> set, HashMap<String, Object> cond, HashMap<String, Object> opt) throws Throwable {
        return ((Long)obj.callMethod("update", set, cond, opt)).intValue();
    }

    //! A @ref update() variant
    public int update(HashMap<String, Object> set, HashMap<String, Object> cond) throws Throwable {
        return ((Long)obj.callMethod("update", set, cond)).intValue();
    }

    //! A @ref update() variant
    public int update(HashMap<String, Object> set) throws Throwable {
        return ((Long)obj.callMethod("update", set)).intValue();
    }

    //! this method upserts or merges data from the given foreign table and @ref select_option_hash "select option hash" into the current table; no transaction management is performed with this method
    /** @par Example:
        @code{.java}
Map<String, Object> h;
try {
    h = table.upsertFromSelect(table2, sh, AbstractTable.UpsertUpdateFirst);

    ds.commit();
} catch (Throwable e) {
    ds.collback();
    throw e;
}
        @endcode

        The table argument does not need to be in the same database as the current table; it can also
        be in a different database server or a database server of a different type (you can use this method to upsert or
        merge data to or from any database supported by SqlUtil).

        @param t the table for the source data; this does not need to be in the same database as the target (the current table), nor does it need to be the same database type
        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument

        @return null if no actions were taken or a hash with the following keys assigned to numeric values indicating the number of rows processed (keys correspond to @ref UpsertResultDescriptionMap keys):
        - \c "inserted": the number of rows inserted
        - \c "verified": the number of rows updated unconditionally; note that this key is returned with all upsert strategy codes other than @ref UpsertSelectFirst instead of \c "updated"
        - \c "updated": the number of rows updated; note that this key is only returned if \a upsert_strategy is @ref UpsertSelectFirst, otherwise updated rows are reported as \c "verified" since rows are updated unconditionally with other the upsert strategy codes
        - \c "unchanged": the number of rows unchanged; this key can only be returned if \a upsert_strategy is @ref UpsertSelectFirst, @ref UpsertInsertOnly, or @ref UpsertUpdateOnly
        - \c "deleted": the number of rows deleted; this can only be returned if @ref UpsertOptions "upsert option" \c delete_others is true

        @throw OPTION-ERROR invalid or unsupported option
        @throw COLUMN-ERROR an unknown column was referenced in the hash to be inserted
        @throw UPSERT-ERROR no primary key, unique constraint, or unique index for upsert; not all columns of the unique constraint/index are used in the upsert statement

        @note
        - if @ref UpsertOptions "upsert option" \c delete_others is true, then a hash of primary key values in the input data is built as the input data is iterated.  After iterating, if the row count of the table and the input data matches, then nothing more is done, otherwise, every row of the table is iterated and compared to the primary key hash; if a row does not match a primary key value, then it is deleted.  This operation is only executed if \c delete_others is true and is expensive for large data sets.
       - this method uses an @ref Qore::SQL::AbstractSQLStatement "AbstractSQLStatement" object to pipeline the select data to the upsert code; to release the transaction lock acquired by the @ref Qore::SQL::AbstractSQLStatement "AbstractSQLStatement" object, a commit() or rollback() action must be executed on the underlying datasource object as in the example above
        - unlike insertFromSelect(), this method processes arbitrary input data and accepts @ref UpsertOptions "upsert options"

        @see
        - upsertFromSelect()
        - upsert()
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> upsertFromSelect(AbstractTable t, Map<String, Object> sh) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("upsertFromSelect", t, sh);
    }

    //! returns the number of rows in the table
    /** @par Example:
        @code{.java}
int cnt = table.rowCount();
        @endcode

        @return the number of rows in the table

        @note to see if the table is empty or not, use emptyData() as this is much faster than rowCount()

        @see emptyData()
     */
    public int rowCount() throws Throwable {
        return ((Long)obj.callMethod("rowCount")).intValue();
    }

    //! returns an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate the results of a select statement matching the arguments
    /** @par Example:
        @code{.java}
AbstractSQLStatement i = table.getStatement(sh, opts);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument
        @param opt optional SQL data operation callback options; see @ref SqlUtil::AbstractTable::SqlDataCallbackOptions for more info

        @return an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate the results of a select statement matching the arguments

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - the @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object created by a successful call to this method acquires a thread resource for the underlying @ref org.qore.lang.AbstractDatasource "AbstractDatasource" object that must be released by calling @ref org.qore.lang.AbstractDatasource.commit() "commit()" or @ref org.qore.lang.AbstractDatasource.rollback() "rollback()", even if the statement does not acquire any database locks

        @see getStatementNoExec()

        @since %SqlUtil 1.5
     */
    public AbstractSQLStatement getStatement(Map<String, Object> sh, Map<String, Object> opt) throws Throwable {
        return new AbstractSQLStatement((QoreObject)obj.callMethodSave("getStatement", sh, opt));
    }

    //! returns an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate the results of a select statement matching the arguments
    /** @par Example:
        @code{.java}
AbstractSQLStatement i = table.getStatement(sh);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument

        @return an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate the results of a select statement matching the arguments

        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - the @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object created by a successful call to this method acquires a thread resource for the underlying @ref org.qore.lang.AbstractDatasource "AbstractDatasource" object that must be released by calling @ref org.qore.lang.AbstractDatasource.commit() "commit()" or @ref org.qore.lang.AbstractDatasource.rollback() "rollback()", even if the statement does not acquire any database locks

        @see getStatementNoExec()

        @since %SqlUtil 1.5
     */
    public AbstractSQLStatement getStatement(Map<String, Object> sh) throws Throwable {
        return new AbstractSQLStatement((QoreObject)obj.callMethodSave("getStatement", sh));
    }

    //! returns an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate all the rows in the table
    /** @par Example:
        @code{.java}
AbstractSQLStatement i = table.getStatement();
        @endcode

        @return an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate all the rows in the table

        @note
        - the @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object created by a successful call to this method acquires a thread resource for the underlying @ref org.qore.lang.AbstractDatasource "AbstractDatasource" object that must be released by calling @ref org.qore.lang.AbstractDatasource.commit() "commit()" or @ref org.qore.lang.AbstractDatasource.rollback() "rollback()", even if the statement does not acquire any database locks

        @see getStatementNoExec()

        @since %SqlUtil 1.5
     */
    public AbstractSQLStatement getStatement() throws Throwable {
        return new AbstractSQLStatement((QoreObject)obj.callMethodSave("getStatement"));
    }

    //! returns an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate the results of a select statement matching the arguments; the statement is only prepared and not executed
    /** @par Example:
        @code{.java}
AbstractSQLStatement i = table.getStatementNoExec(sh, opts);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument
        @param opt optional SQL data operation callback options; see @ref SqlUtil::AbstractTable::SqlDataCallbackOptions for more info

        @return an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate the results of a select statement matching the arguments

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - the @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object created by a successful call to this method acquires a thread resource for the underlying @ref org.qore.lang.AbstractDatasource "AbstractDatasource" object that must be released by calling @ref org.qore.lang.AbstractDatasource.commit() "commit()" or @ref org.qore.lang.AbstractDatasource.rollback() "rollback()", even if the statement does not acquire any database locks

        @see getStatement()

        @since %SqlUtil 1.5
     */
    public AbstractSQLStatement getStatementNoExec(Map<String, Object> sh, Map<String, Object> opt) throws Throwable {
        return new AbstractSQLStatement((QoreObject)obj.callMethodSave("getStatementNoExec", sh, opt));
    }

    //! returns an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate the results of a select statement matching the arguments; the statement is only prepared and not executed
    /** @par Example:
        @code{.java}
AbstractSQLStatement i = table.getStatementNoExec(sh);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument

        @return an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate the results of a select statement matching the arguments

        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - the @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object created by a successful call to this method acquires a thread resource for the underlying @ref org.qore.lang.AbstractDatasource "AbstractDatasource" object that must be released by calling @ref org.qore.lang.AbstractDatasource.commit() "commit()" or @ref org.qore.lang.AbstractDatasource.rollback() "rollback()", even if the statement does not acquire any database locks

        @see getStatement()

        @since %SqlUtil 1.5
     */
    public AbstractSQLStatement getStatementNoExec(Map<String, Object> sh) throws Throwable {
        return new AbstractSQLStatement((QoreObject)obj.callMethodSave("getStatementNoExec", sh));
    }

    //! returns an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate all the rows in the table; the statement is only prepared and not executed
    /** @par Example:
        @code{.java}
AbstractSQLStatement i = table.getStatementNoExec();
        @endcode

        @return an @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object that will iterate all the rows in the table

        @note
        - the @ref org.qore.lang.AbstractSQLStatement "AbstractSQLStatement" object created by a successful call to this method acquires a thread resource for the underlying @ref org.qore.lang.AbstractDatasource "AbstractDatasource" object that must be released by calling @ref org.qore.lang.AbstractDatasource.commit() "commit()" or @ref org.qore.lang.AbstractDatasource.rollback() "rollback()", even if the statement does not acquire any database locks

        @see getStatement()

        @since %SqlUtil 1.5
     */
    public AbstractSQLStatement getStatementNoExec() throws Throwable {
        return new AbstractSQLStatement((QoreObject)obj.callMethodSave("getStatementNoExec"));
    }

    //! returns a hash representing the row in the table that matches the argument hash; if more than one row would be returned an exception is raised
    /** @par Example:
        @code{.java}
Map<String, Object> h = table.selectRow(sh);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument
        @param opt optional SQL data operation callback options; see @ref SqlUtil::AbstractTable::SqlDataCallbackOptions for more info

        @return a hash representing the row in the table that matches the argument hash; if more than one row would be returned an exception is raised

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint
        @throw DBI-SELECT-ROW-ERROR more than 1 row retrieved from the server

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - if the \c forupdate @ref select_option_hash "select option" is used, then after a successful select operation, the calling thread will own the thread transaction lock
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> selectRow(Map<String, Object> sh, Map<String, Object> opt) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("selectRow", sh, opt);
    }

    //! returns a hash representing the row in the table that matches the argument hash; if more than one row would be returned an exception is raised
    /** @par Example:
        @code{.java}
Map<String, Object> h = table.selectRow(sh);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument

        @return a hash representing the row in the table that matches the argument hash; if more than one row would be returned an exception is raised

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint
        @throw DBI-SELECT-ROW-ERROR more than 1 row retrieved from the server

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - if the \c forupdate @ref select_option_hash "select option" is used, then after a successful select operation, the calling thread will own the thread transaction lock
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> selectRow(Map<String, Object> sh) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("selectRow", sh);
    }

    //! returns a list of hashes representing the rows in the table that match the argument hash
    /** @par Example:
        @code{.java}
Map<String, Object>[] l = table.selectRows(sh, opt);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument
        @param opt optional SQL data operation callback options; see @ref SqlUtil::AbstractTable::SqlDataCallbackOptions for more info

        @return a list of hashes representing the rows in the table that match the argument hash

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - if the \c forupdate @ref select_option_hash "select option" is used, then after a successful select operation, the calling thread will own the thread transaction lock
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object>[] selectRows(Map<String, Object> sh, Map<String, Object> opt) throws Throwable {
        return (HashMap<String, Object>[])obj.callMethod("selectRows", sh, opt);
    }

    //! returns a list of hashes representing the rows in the table that match the argument hash
    /** @par Example:
        @code{.java}
Map<String, Object>[] l = table.selectRows(sh);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument

        @return a list of hashes representing the rows in the table that match the argument hash

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - if the \c forupdate @ref select_option_hash "select option" is used, then after a successful select operation, the calling thread will own the thread transaction lock
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object>[] selectRows(Map<String, Object> sh) throws Throwable {
        return (HashMap<String, Object>[])obj.callMethod("selectRows", sh);
    }

    //! returns a list of hashes representing the rows in the table that match the argument hash
    /** @par Example:
        @code{.java}
Map<String, Object>[] l = table.selectRows();
        @endcode

        @return a list of hashes representing the rows in the table that match the argument hash

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - if the \c forupdate @ref select_option_hash "select option" is used, then after a successful select operation, the calling thread will own the thread transaction lock
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object>[] selectRows() throws Throwable {
        return (HashMap<String, Object>[])obj.callMethod("selectRows");
    }

    //! returns a hash of lists representing the columns and rows in the table that match the argument hahs
    /** @par Example:
        @code{.java}
Map<String, Object> h = table.select(sh);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument
        @param opt optional SQL data operation callback options; see @ref SqlUtil::AbstractTable::SqlDataCallbackOptions for more info

        @return a hash of lists representing the columns and rows in the table that match the argument hash

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - if the \c forupdate @ref select_option_hash "select option" is used, then after a successful select operation, the calling thread will own the thread transaction lock
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> select(Map<String, Object> sh, Map<String, Object> opt) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("select", sh, opt);
    }

    //! returns a hash of lists representing the columns and rows in the table that match the argument hahs
    /** @par Example:
        @code{.java}
Map<String, Object> h = table.select(sh);
        @endcode

        @param sh a hash of conditions for the select statement; see @ref select_option_hash "select option hash" for information about this argument

        @return a hash of lists representing the columns and rows in the table that match the argument hash

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - if the \c forupdate @ref select_option_hash "select option" is used, then after a successful select operation, the calling thread will own the thread transaction lock
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> select(Map<String, Object> sh) throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("select", sh);
    }

    //! returns a hash of lists representing the columns and rows in the table that match the argument hahs
    /** @par Example:
        @code{.java}
Map<String, Object> h = table.select();
        @endcode

        @return a hash of lists representing the columns and rows in the table that match the argument hash

        @throw OPTION-ERROR invalid or unsupported select option
        @throw SELECT-ERROR \c 'offset' supplied without \c 'orderby' or \c 'limit', \c 'orderby' with \c 'limit' and \c 'offset' does not match any unique constraint

        @note
        - if \c "offset" is supplied and no \c "orderby" is supplied, then if any primary key exists, the primary key columns will be used for the \c "orderby" option automatically
        - if the \c forupdate @ref select_option_hash "select option" is used, then after a successful select operation, the calling thread will own the thread transaction lock
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> select() throws Throwable {
        return (HashMap<String, Object>)obj.callMethod("select");
    }
}

