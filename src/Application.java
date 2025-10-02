import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

public class Application {

    private static Application instance;   // Singleton pattern

    public static Application getInstance() {
        if (instance == null) {
            instance = new Application();
        }
        return instance;
    }
    // Main components of this application

    private Connection connection;

    public Connection getDBConnection() {
        return connection;
    }

    private DataAdapter dataAdapter;

    private User currentUser = null;

    public User getCurrentUser() { return currentUser; }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    private ProductView productView = new ProductView();

    private OrderView orderView = new OrderView();

    private MainScreen mainScreen = new MainScreen();

    public MainScreen getMainScreen() {
        return mainScreen;
    }

    public ProductView getProductView() {
        return productView;
    }

    public OrderView getOrderView() {
        return orderView;
    }

    public LoginScreen loginScreen = new LoginScreen();

    public LoginScreen getLoginScreen() {
        return loginScreen;
    }

    public LoginController loginController;

    private ProductController productController;

    public ProductController getProductController() {
        return productController;
    }

    private OrderController orderController;

    public OrderController getOrderController() {
        return orderController;
    }

    public DataAdapter getDataAdapter() {
        return dataAdapter;
    }


    private Application() {
        try {
            // Load MySQL Driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Connect to MySQL
            connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/storeapp", 
                "root",                               
                "1234"                                 
            );
            System.out.println("MySQL connection established successfully!");
            dataAdapter = new DataAdapter(connection);
        }
        catch (ClassNotFoundException ex) {
            System.out.println("MySQL JDBC Driver is not installed. System exits with error!");
            ex.printStackTrace();
            System.exit(1);
        }
        catch (SQLException ex) {
            System.out.println("MySQL database is not ready. System exits with error! " + ex.getMessage());
            ex.printStackTrace();
            System.exit(2);
        }

        productController = new ProductController(productView);
        orderController = new OrderController(orderView);
        loginController = new LoginController(loginScreen);
    }

    public void runSqlScript(String scriptPath) {
        try {
            String sql = new String(Files.readAllBytes(Paths.get(scriptPath)));

            // Strip block comments and line comments
            sql = sql.replaceAll("(?s)/\\*.*?\\*/", "");
            sql = sql.replaceAll("(?m)^\\s*--.*$", "");

            String[] statements = sql.split("(?m);\\s*(?=\\r?\\n|$)");
            try (Statement stmt = connection.createStatement()) {
                for (String s : statements) {
                    String trimmed = s.trim();
                    if (trimmed.isEmpty()) continue;
                    stmt.execute(trimmed);
                }
            }
            System.out.println("SQL script executed: " + scriptPath);
        } catch (IOException | SQLException ex) {
            throw new RuntimeException("Failed to run SQL script: " + scriptPath, ex);
        }
    }



    public static void main(String[] args) {
        Application app = Application.getInstance();
        app.runSqlScript("src/createTable.sql");
        String sqlitePath = detectSQLitePath(args);
        if (sqlitePath != null) {
            try {
                System.out.println("Running SQLite -> MySQL migration from: " + sqlitePath);
                SQLiteToMySQLMigrator.migrate(sqlitePath, app.getDBConnection());
            } catch (Exception ex) {
                System.out.println("Migration failed: " + ex.getMessage());
                ex.printStackTrace();
            }
        } else {
            System.out.println("No SQLite database found to migrate. You can pass a path as the first argument.");
        }
        app.getLoginScreen().setVisible(true);
    }
    //helper method to detect sqlite path
    private static String detectSQLitePath(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            return args[0];
        }
        String[] candidates = new String[] {
                "store.db",
                "../store.db",
                "../../store.db",
                "./store.db"
        };
        for (String c : candidates) {
            try {
                if (java.nio.file.Files.exists(java.nio.file.Paths.get(c))) {
                    return java.nio.file.Paths.get(c).toAbsolutePath().toString();
                }
            } catch (Exception ignore) {}
        }
        return null;
    }
}
