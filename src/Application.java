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
                "jdbc:mysql://localhost:3306/storeapp", // Your DB name
                "root",                                // Username
                "1234"                                 // Password
            );
            System.out.println("MySQL connection established successfully!");
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



    public static void main(String[] args) {
        Application.getInstance().getLoginScreen().setVisible(true);
    }
}
