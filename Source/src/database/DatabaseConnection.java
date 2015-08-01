package database;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import server.ServerProperties;

/**
 * All servers maintain a Database Connection. This class therefore
 * "singletonices" the connection per process.
 *
 *
 * @author Frz
 */
public class DatabaseConnection {

    private static final ThreadLocal<Connection> con = new DatabaseConnection.ThreadLocalConnection();
    private static final PoolProperties poolProps = new PoolProperties();
    private static final DataSource dataSource = new DataSource();
    public static final int CLOSE_CURRENT_RESULT = 1;
    /**
     * The constant indicating that the current <code>ResultSet</code> object
     * should not be closed when calling <code>getMoreResults</code>.
     *
     * @since 1.4
     */
    public static final int KEEP_CURRENT_RESULT = 2;
    /**
     * The constant indicating that all <code>ResultSet</code> objects that have
     * previously been kept open should be closed when calling
     * <code>getMoreResults</code>.
     *
     * @since 1.4
     */
    public static final int CLOSE_ALL_RESULTS = 3;
    /**
     * The constant indicating that a batch statement executed successfully but
     * that no count of the number of rows it affected is available.
     *
     * @since 1.4
     */
    public static final int SUCCESS_NO_INFO = -2;
    /**
     * The constant indicating that an error occured while executing a batch
     * statement.
     *
     * @since 1.4
     */
    public static final int EXECUTE_FAILED = -3;
    /**
     * The constant indicating that generated keys should be made available for
     * retrieval.
     *
     * @since 1.4
     */
    public static final int RETURN_GENERATED_KEYS = 1;
    /**
     * The constant indicating that generated keys should not be made available
     * for retrieval.
     *
     * @since 1.4
     */
    public static final int NO_GENERATED_KEYS = 2;

    public static Connection getConnection() {
        return con.get();
    }

    public static void closeAll() throws SQLException {
        for (final Connection connection : DatabaseConnection.ThreadLocalConnection.allConnections) {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static final class ThreadLocalConnection extends ThreadLocal<Connection> {

        public static final Collection<Connection> allConnections = new LinkedList<>();

        @Override
        protected final Connection initialValue() {
//            try {
//                Class.forName("com.mysql.jdbc.Driver"); // touch the mysql driver
//            } catch (final ClassNotFoundException e) {
//                System.err.println("ERROR" + e);
//            }

            try {
//                final Connection con = DriverManager.getConnection("jdbc:mysql://" + ServerConstants.SQL_IP + ":" + ServerConstants.SQL_PORT + "/" + ServerConstants.SQL_DATABASE + "?autoReconnect=true&characterEncoding=UTF8", ServerConstants.SQL_USER, ServerConstants.SQL_PASSWORD);
                final Connection con = dataSource.getConnection();
                allConnections.add(con);
                return con;
            } catch (SQLException e) {
                System.err.println("ERROR" + e);
                return null;
            }
        }
    }

    static {
        try {

            String db = ServerProperties.getProperty("server.settings.db.name", "twms");
            String ip = ServerProperties.getProperty("server.settings.db.ip", "127.0.0.1");
            poolProps.setUrl("jdbc:mysql://" + ip + ":3306/" + db + "?autoReconnect=true&characterEncoding=UTF8");
            poolProps.setDriverClassName("com.mysql.jdbc.Driver");
            poolProps.setUsername(ServerProperties.getProperty("server.settings.db.user", "root"));
            poolProps.setPassword(ServerProperties.getProperty("server.settings.db.password", "root"));

            poolProps.setMinIdle(20);
            poolProps.setInitialSize(30);
            poolProps.setMaxIdle(100);

            dataSource.setPoolProperties(poolProps);

        } catch (Exception e) {
            System.out.println("[數據庫訊息] 找不到JDBC驅動.");
            System.exit(0);
        }
    }
}
