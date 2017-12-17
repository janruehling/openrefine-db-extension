
package com.google.refine.extension.database.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.extension.database.DatabaseConfiguration;
import com.google.refine.extension.database.DatabaseService;
import com.google.refine.extension.database.DatabaseServiceException;
import com.google.refine.extension.database.DatabaseUtils;
import com.google.refine.extension.database.SQLType;
import com.google.refine.extension.database.model.DatabaseColumn;
import com.google.refine.extension.database.model.DatabaseInfo;
import com.google.refine.extension.database.model.DatabaseRow;
import com.mysql.jdbc.ResultSetMetaData;

public class MySQLDatabaseService extends DatabaseService {
  

    private final static Logger logger = LoggerFactory.getLogger("MySQLDatabaseService");
    
    public static final String DB_NAME = "mysql";
    public static final String DB_DRIVER = "com.mysql.jdbc.Driver";

    private static MySQLDatabaseService instance;

    private MySQLDatabaseService() {
    }

    public static MySQLDatabaseService getInstance() {
        if (instance == null) {
            SQLType.registerSQLDriver(DB_NAME, DB_DRIVER, false);
            instance = new MySQLDatabaseService();
            logger.debug("MySQLDatabaseService Instance: {}", instance);
        }
        return instance;
    }

    @Override
    public boolean testConnection(DatabaseConfiguration dbConfig) throws DatabaseServiceException{
        return MySQLConnectionManager.getInstance().testConnection(dbConfig);
      
    }

    @Override
    public DatabaseInfo connect(DatabaseConfiguration dbConfig) throws DatabaseServiceException{
        return getMetadata(dbConfig);
    }
 
   
    @Override
    public DatabaseInfo executeQuery(DatabaseConfiguration dbConfig, String query) throws DatabaseServiceException{
       try {
                Connection connection = MySQLConnectionManager.getInstance().getConnection(dbConfig, false);
                Statement statement = connection.createStatement();
                ResultSet queryResult = statement.executeQuery(query);
                ResultSetMetaData metadata = (ResultSetMetaData)queryResult.getMetaData();
                int columnCount = metadata.getColumnCount();
                ArrayList<DatabaseColumn> columns = new ArrayList<DatabaseColumn>(columnCount);
                
                for (int i = 1; i <= columnCount; i++) {
                    DatabaseColumn dc = new DatabaseColumn(
                            metadata.getColumnName(i), 
                            metadata.getColumnLabel(i),
                            DatabaseUtils.getDbColumnType(metadata.getColumnType(i)),
                            metadata.getColumnDisplaySize(i));
                    columns.add(dc);  
                }
                int index = 0; 
                List<DatabaseRow> rows = new ArrayList<DatabaseRow>();
                
                while (queryResult.next()) {
                    DatabaseRow row = new DatabaseRow();
                    row.setIndex(index);
                    List<String> values = new ArrayList<String>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        
                        values.add(queryResult.getString(i));
                              
                    }
                    row.setValues(values);
                    rows.add(row);
                    index++;
                 
                }
                
                DatabaseInfo dbInfo = new DatabaseInfo();
                dbInfo.setColumns(columns);
                dbInfo.setRows(rows);
                return dbInfo;
        
        } catch (SQLException e) {
            logger.error("SQLException::", e);
            throw new DatabaseServiceException(true, e.getSQLState(), e.getErrorCode(), e.getMessage());
        }finally {
            MySQLConnectionManager.getInstance().shutdown();
        }
    }
    
    /**
     * 
     * @param connectionInfo
     * @return
     * @throws DatabaseServiceException
     */
    private DatabaseInfo getMetadata(DatabaseConfiguration connectionInfo)  throws DatabaseServiceException {
       
        try {
            Connection connection = MySQLConnectionManager.getInstance().getConnection(connectionInfo, true);
            if(connection != null) {
                java.sql.DatabaseMetaData metadata;

                metadata = connection.getMetaData();

                int dbMajorVersion = metadata.getDatabaseMajorVersion();
                int dbMinorVersion = metadata.getDatabaseMinorVersion();
                String dbProductVersion = metadata.getDatabaseProductVersion();
                String dbProductName = metadata.getDatabaseProductName();
                DatabaseInfo dbInfo = new DatabaseInfo();

                dbInfo.setDatabaseMajorVersion(dbMajorVersion);
                dbInfo.setDatabaseMinorVersion(dbMinorVersion);
                dbInfo.setDatabaseProductVersion(dbProductVersion);
                dbInfo.setDatabaseProductName(dbProductName);
                return dbInfo;
            }
            
        } catch (SQLException e) {
            logger.error("SQLException::", e);
            throw new DatabaseServiceException(true, e.getSQLState(), e.getErrorCode(), e.getMessage());
        }

       
        return null;
        
    }

    @Override
    public String buildLimitQuery(Integer limit, Integer offset, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append(query);
        
        if(limit != null) {
            sb.append(" LIMIT" + " " + limit);
        }
        
        if(offset != null) {
            sb.append(" OFFSET" + " " + offset);
        }
        
        return sb.toString();
    }

    @Override
    public ArrayList<DatabaseColumn> getColumns(DatabaseConfiguration dbConfig, String query) throws DatabaseServiceException{
      
        try {
            Connection connection = MySQLConnectionManager.getInstance().getConnection(dbConfig, true);
            Statement statement = connection.createStatement();

            ResultSet queryResult = statement.executeQuery(query);
            ResultSetMetaData metadata = (ResultSetMetaData) queryResult.getMetaData();
            int columnCount = metadata.getColumnCount();
            ArrayList<DatabaseColumn> columns = new ArrayList<DatabaseColumn>(columnCount);

            for (int i = 1; i <= columnCount; i++) {
                DatabaseColumn dc = new DatabaseColumn(metadata.getColumnName(i), metadata.getColumnLabel(i),
                        DatabaseUtils.getDbColumnType(metadata.getColumnType(i)), metadata.getColumnDisplaySize(i));
                columns.add(dc);
            }
            
            return columns;

        } catch (SQLException e) {
            logger.error("SQLException::", e);
            throw new DatabaseServiceException(true, e.getSQLState(), e.getErrorCode(), e.getMessage());
        }

      
    }

    @Override
    public List<DatabaseRow> getRows(DatabaseConfiguration dbConfig, String query)
            throws DatabaseServiceException {
        
        try {
                Connection connection = MySQLConnectionManager.getInstance().getConnection(dbConfig, false);
               
                Statement statement = connection.createStatement();
                statement.setFetchSize(10);
                ResultSet queryResult = statement.executeQuery(query);
                ResultSetMetaData metadata = (ResultSetMetaData)queryResult.getMetaData();
                int columnCount = metadata.getColumnCount();

                int index = 0; 
                List<DatabaseRow> rows = new ArrayList<DatabaseRow>();
                
                while (queryResult.next()) {
                    DatabaseRow row = new DatabaseRow();
                    row.setIndex(index);
                    List<String> values = new ArrayList<String>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        
                        values.add(queryResult.getString(i));
                              
                    }
                    row.setValues(values);
                    rows.add(row);
                    index++;
                 
                }
           
                return rows;
        
        } catch (SQLException e) {
            logger.error("SQLException::", e);
            throw new DatabaseServiceException(true, e.getSQLState(), e.getErrorCode(), e.getMessage());
        }
    }

    @Override
    protected String getDatabaseUrl(DatabaseConfiguration dbConfig) {
       
            int port = dbConfig.getDatabasePort();
            return "jdbc:" + dbConfig.getDatabaseType() + "://" + dbConfig.getDatabaseHost()
                    + ((port == 0) ? "" : (":" + port)) + "/" + dbConfig.getDatabaseName() + "?useSSL=" + dbConfig.isUseSSL();
        
    }
    
    
    @Override
    public Connection getConnection(DatabaseConfiguration dbConfig)
            throws DatabaseServiceException {
        // TODO Auto-generated method stub
        return  MySQLConnectionManager.getInstance().getConnection(dbConfig, true);
    }

    @Override
    public DatabaseInfo testQuery(DatabaseConfiguration dbConfig, String query)
            throws DatabaseServiceException {
        Statement statement  = null;
        ResultSet queryResult = null;
        try {
            Connection connection = MySQLConnectionManager.getInstance().getConnection(dbConfig, true);
            statement = connection.createStatement();
            queryResult = statement.executeQuery(query);

            DatabaseInfo dbInfo = new DatabaseInfo();

            return dbInfo;

        } catch (SQLException e) {
            logger.error("SQLException::", e);
            throw new DatabaseServiceException(true, e.getSQLState(), e.getErrorCode(), e.getMessage());
        } finally {
            try {
                if (queryResult != null) {
                    queryResult.close();

                }
                if (statement != null) { 
                    statement.close();

                }
            } catch (SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            MySQLConnectionManager.getInstance().shutdown();
        }
    }
    
 
}
