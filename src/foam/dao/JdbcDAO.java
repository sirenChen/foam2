/**
 * @license
 * Copyright 2017 The FOAM Authors. All Rights Reserved.
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package foam.dao;

import foam.core.*;
import foam.dao.pg.IndexedPreparedStatement;
import foam.mlang.order.Comparator;

import foam.mlang.predicate.Predicate;

import javax.sql.DataSource;
import java.lang.Exception;
import java.sql.*;

import java.sql.SQLException;
import java.util.*;

/*
  We assume that the database is created by a script when the system starts the first time.
  When the system restarts at any point in time it should verify that the database is already created otherwise it should create it.
  When creating the database, we only do a CREATE SCHEMA sql instruction, then we create the app user and password and grant him full privileges on this database.
  Any other database objects (tables) will be created on the fly by the application (when methods of this class are called).

 */
public class JdbcDAO extends AbstractDAO{


  // Holds the relevant properties (column names) of the table
  protected List<PropertyInfo> properties_;

  private ThreadLocal<StringBuilder> threadLocalBuilder_ = new ThreadLocal<StringBuilder>(){

    @Override
    protected StringBuilder initialValue() {
      return new StringBuilder();
    }

    @Override
    public StringBuilder get() {
      StringBuilder builder = super.get();
      builder.setLength(0);
      return builder;
    }

  };

  protected String tableName_;

  // Holds a reference to the connection pool ( .getConnection() )
  protected static DataSource dataSource_;

  public JdbcDAO(X x, ClassInfo of) throws java.sql.SQLException, ClassNotFoundException {
    setX(x);
    setOf(of);

    // Get the system global dataSource with its system global pool
    dataSource_ = JDBCPooledDataSource.getDataSource(x);

    tableName_ = of.getObjClass().getSimpleName().toLowerCase();

    getObjectProperties(of);

    if ( ! createTable(of) ) {
      // Table already created (may happen after a system restart).
    }

  }

  @Override
  public FObject put_(X x, FObject obj) {
    Connection c = null;
    IndexedPreparedStatement stmt = null;
    ResultSet resultSet = null;

    try {
      c = dataSource_.getConnection();
      StringBuilder builder = threadLocalBuilder_.get()
        .append("insert into ")
        .append(tableName_);

      buildFormattedColumnNames(obj, builder);
      builder.append(" values");
      buildFormattedColumnPlaceholders(obj, builder);
      builder.append(" on duplicate key ")
        // .append(getPrimaryKey().createStatement()) ... Not in MySQL
        .append(" update ");
      buildUpdateFormattedColumnNames(obj, builder);   // Specific to MySQL

      stmt = new IndexedPreparedStatement(c.prepareStatement(builder.toString(),
        Statement.RETURN_GENERATED_KEYS));

      setStatementValues(stmt, obj);

      int inserted = stmt.executeUpdate();
      if ( inserted == 0 ) {
        throw new SQLException("Error performing put_ command");
      }

      // get auto-generated postgres keys
/*       resultSet = stmt.getGeneratedKeys();
      if ( resultSet.next() ) {
        obj.setProperty(getPrimaryKey().getName(), resultSet.getObject(1));
      } */

      return obj;
    } catch (Throwable e) {
      e.printStackTrace();
      return null;
    } finally {
      closeAllQuietly(resultSet, stmt);
    }
  }

  /**
   * Prepare the formatted column names. Appends column names like: (c1,c2,c3)
   * @param builder builder to append to
   */
  public void buildFormattedColumnNames(FObject obj, StringBuilder builder) {
    // collect columns list into comma delimited string
    builder.append("(");
    Iterator i = properties_.iterator();
    while ( i.hasNext() ) {
      PropertyInfo prop = (PropertyInfo) i.next();
/*       if ( "id".equals(prop.getName()) )
        continue; */

      builder.append(prop.createStatement());
      if ( i.hasNext() ) {
        builder.append(",");
      }
    }
    builder.append(")");
  }

  /**
   * Prepare the formatted UPDATE string like :  description='etc', name='etc'
   * @param builder builder to append to
   */
  public void buildUpdateFormattedColumnNames(FObject obj, StringBuilder builder) {
    // collect columns list into comma delimited string
    Iterator i = properties_.iterator();
    while ( i.hasNext() ) {

      PropertyInfo prop = (PropertyInfo) i.next();
      if ( "id".equals(prop.getName()) )
        continue;

      builder.append(prop.createStatement());
      builder.append("='");
      builder.append(prop.get(obj));  //add the new property value
      builder.append("'");
      if ( i.hasNext() ) {
        builder.append(",");
      }
    }

  }

  /**
   * Prepare the formatted value placeholders. Appends value placeholders like: (?,?,?)
   * @param builder builder to append to
   */
  public void buildFormattedColumnPlaceholders(FObject obj, StringBuilder builder) {
    // map columns into ? and collect into comma delimited string
    builder.append("(");
    Iterator i = properties_.iterator();
    while ( i.hasNext() ) {
      PropertyInfo prop = (PropertyInfo) i.next();
/*       if ( "id".equals(prop.getName()) )
        continue; */

      builder.append("?");
      if ( i.hasNext() ) {
        builder.append(",");
      }
    }
    builder.append(")");
  }

  /**
   * Sets the value of the PrepareStatement
   * @param stmt statement to set values
   * @param obj object to get values from
   * @return the updated index
   * @throws SQLException
   */
  public void setStatementValues(IndexedPreparedStatement stmt, FObject obj) throws SQLException {
    Iterator i = properties_.iterator();
    while ( i.hasNext() ) {
      PropertyInfo prop = (PropertyInfo) i.next();
      prop.setStatementValue(stmt, obj);
    }
  }


  //TODO
  @Override
  public FObject remove_(X x, FObject obj) {
    return null;
  }

  //TODO
  @Override
  public FObject find_(X x, Object id) {
    return null;
  }

  //TODO: refine
  @Override
  public Sink select_(X x, Sink sink, long skip, long limit, Comparator order, Predicate predicate) {
    sink = prepareSink(sink);

    Connection               c         = null;
    IndexedPreparedStatement stmt      = null;
    ResultSet                resultSet = null;

    try {
      c = dataSource_.getConnection();

      StringBuilder builder = threadLocalBuilder_.get()
        .append("select * from ")
        .append(tableName_);

      if ( predicate != null ) {
        builder.append(" where ")
          .append(predicate.createStatement());
      }

      if ( order != null ) {
        builder.append(" order by ")
          .append(order.createStatement());
      }

      if ( limit > 0 && limit < this.MAX_SAFE_INTEGER ) {
        builder.append(" limit ").append(limit);
      }

      if ( skip > 0 && skip < this.MAX_SAFE_INTEGER ) {
        builder.append(" offset ").append(skip);
      }

      stmt = new IndexedPreparedStatement(c.prepareStatement(builder.toString()));

      if ( predicate != null ) {
        predicate.prepareStatement(stmt);
      }

      resultSet = stmt.executeQuery(); // ???
      while ( resultSet.next() ) {
        sink.put(createFObject(resultSet), null);
      }

      return sink;
    } catch (Throwable e) {
      e.printStackTrace();
      return null;
    } finally {
      closeAllQuietly(resultSet, stmt);
    }
  }

  /**
   * Creates an FObject with the appropriate meta-properties.
   * @param resultSet
   * @return
   * @throws Exception
   */
  private FObject createFObject(ResultSet resultSet) throws Exception {
    if ( getOf() == null ) {
      throw new Exception("`Of` is not set");
    }

    FObject obj = (FObject) getOf().getObjClass().newInstance();
    ResultSetMetaData metaData = resultSet.getMetaData();

    int index = 1;
    Iterator i = properties_.iterator();
    while ( i.hasNext() ) {
      // prevent reading out of bounds of result set
      if ( index > metaData.getColumnCount() )
        break;
      // get the property and set the value
      PropertyInfo prop = (PropertyInfo) i.next();
      prop.setFromResultSet(resultSet, index++, obj);
//      prop.set(obj, resultSet.getObject(index++));
    }

    return obj;
  }

  /**
   * Returns list of properties of a metaclass
   * @param of ClassInfo
   */
  private void getObjectProperties(ClassInfo of){

    if(properties_ == null) {
      List<PropertyInfo> allProperties = of.getAxiomsByClass(PropertyInfo.class);
      properties_ = new ArrayList<PropertyInfo>();
      for (PropertyInfo prop : allProperties) {
        if (prop.getStorageTransient())
          continue;
        if ("".equals(prop.getSQLType()))
          continue;
        properties_.add(prop);
      }
    }
  }

  /**
   * Create the table in the database and return true if it doesn't already exist otherwise it does nothing and returns false
   * @param of ClassInfo
   */
  public boolean createTable(ClassInfo of) {
    Connection c = null;
    IndexedPreparedStatement stmt = null;
    ResultSet resultSet = null;

    try {
      c = dataSource_.getConnection();
      DatabaseMetaData meta = c.getMetaData();
      resultSet = meta.getTables(null, null, tableName_, new String[]{"TABLE"});
      if ( resultSet.isBeforeFirst() ) {
        // found a table, don't create
        return false;
      }

      StringBuilder builder =threadLocalBuilder_.get()
        .append("CREATE TABLE ")
        .append(tableName_)
        .append("(")
        .append(getPrimaryKey().createStatement())
        .append(" ")
        .append(getPrimaryKey().getSQLType())
        .append(" primary key,");

      Iterator i = properties_.iterator();
      while ( i.hasNext() ) {
        PropertyInfo prop = (PropertyInfo) i.next();

        // Why you skip the primary key? (Ask Kevin)
        if ( getPrimaryKey().getName().equals(prop.getName()) )
          continue;

        builder.append(prop.createStatement())
          .append(" ")
          .append(prop.getSQLType()); // TODO: is getSQLType guaranteed to return something?

        if ( i.hasNext() ) {
          builder.append(",");
        }
      }
      builder.append(")");

      // execute statement
      stmt = new IndexedPreparedStatement(c.prepareStatement(builder.toString()));
      stmt.executeUpdate();
      return true;
    } catch (Throwable e) {
      e.printStackTrace();
      return false;
    } finally {
      closeAllQuietly(resultSet, stmt);
    }
  }

  /**
   * Closes resources without throwing exceptions
   * @param resultSet ResultSet
   * @param stmt IndexedPreparedStatement
   */
  public void closeAllQuietly(ResultSet resultSet, IndexedPreparedStatement stmt) {
    if ( resultSet != null )
      try { resultSet.close(); } catch (Throwable ignored) {}
    if ( stmt != null )
      try { stmt.close(); } catch (Throwable ignored) {}
  }


}
