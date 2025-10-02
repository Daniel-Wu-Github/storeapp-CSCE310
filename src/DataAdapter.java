import java.sql.*;

public class DataAdapter {
    private final Connection connection;

    public DataAdapter(Connection connection) {
        this.connection = connection;
    }

    public Product loadProduct(int id) {
        String sql = "SELECT productID, productName, price, quantity, sellerID FROM products WHERE productID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Product product = new Product();
                product.setProductID(rs.getInt("productID"));
                product.setName(rs.getString("productName"));
                product.setPrice(rs.getDouble("price"));
                product.setQuantity(rs.getDouble("quantity"));
                try { product.setSellerID(rs.getInt("sellerID")); } catch (Exception ignore) {}
                return product;
            }
        } catch (SQLException e) {
            System.out.println("Database access error!");
            e.printStackTrace();
            return null;
        }
    }

    public boolean saveProduct(Product product) {
        String existsSql = "SELECT 1 FROM products WHERE productID = ?";
        try (PreparedStatement check = connection.prepareStatement(existsSql)) {
            check.setInt(1, product.getProductID());
            boolean exists;
            try (ResultSet rs = check.executeQuery()) {
                exists = rs.next();
            }

            if (exists) {
                String updateSql = "UPDATE products SET productName = ?, price = ?, quantity = ?, sellerID = ? WHERE productID = ?";
                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, product.getName());
                    ps.setDouble(2, product.getPrice());
                    ps.setDouble(3, product.getQuantity());
                    ps.setInt(4, product.getSellerID());
                    ps.setInt(5, product.getProductID());
                    return ps.executeUpdate() == 1;
                }
            } else {
                String insertSql = "INSERT INTO products (productID, productName, price, quantity, sellerID) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setInt(1, product.getProductID());
                    ps.setString(2, product.getName());
                    ps.setDouble(3, product.getPrice());
                    ps.setDouble(4, product.getQuantity());
                    ps.setInt(5, product.getSellerID());
                    return ps.executeUpdate() == 1;
                }
            }
        } catch (SQLException e) {
            System.out.println("Database access error!");
            e.printStackTrace();
            return false;
        }
    }

    public Order loadOrder(int id) {
        String orderSql = "SELECT orderID, buyerID, totalCost, totalTax, date FROM orders WHERE orderID = ?";
        try (PreparedStatement ps = connection.prepareStatement(orderSql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Order order = new Order();
                order.setOrderID(rs.getInt("orderID"));
                order.setBuyerID(rs.getInt("buyerID"));
                order.setTotalCost(rs.getDouble("totalCost"));
                order.setTotalTax(rs.getDouble("totalTax"));
                order.setDate(rs.getString("date"));

                // load order lines
                String linesSql = "SELECT orderID, productID, quantity, cost FROM orderLine WHERE orderID = ?";
                try (PreparedStatement lps = connection.prepareStatement(linesSql)) {
                    lps.setInt(1, id);
                    try (ResultSet lrs = lps.executeQuery()) {
                        while (lrs.next()) {
                            OrderLine line = new OrderLine();
                            line.setOrderID(lrs.getInt("orderID"));
                            line.setProductID(lrs.getInt("productID"));
                            line.setQuantity(lrs.getDouble("quantity"));
                            line.setCost(lrs.getDouble("cost"));
                            order.addLine(line);
                        }
                    }
                }

                return order;
            }
        } catch (SQLException e) {
            System.out.println("Database access error!");
            e.printStackTrace();
            return null;
        }
    }

    public boolean saveOrder(Order order) {
        // We'll insert the order first, then the lines, and update product quantities in a single transaction
        boolean originalAutoCommit;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            Integer orderIdToUse = order.getOrderID();
            // If orderID is 0, let DB assign one (AUTO_INCREMENT)
            if (orderIdToUse == 0) {
                String insertOrderSql = "INSERT INTO orders (buyerID, totalCost, totalTax, date) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, order.getBuyerID());
                    ps.setDouble(2, order.getTotalCost());
                    ps.setDouble(3, order.getTotalTax());
                    ps.setString(4, order.getDate());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            orderIdToUse = keys.getInt(1);
                            order.setOrderID(orderIdToUse);
                        }
                    }
                }
            } else {
                String insertOrderSql = "INSERT INTO orders (orderID, buyerID, totalCost, totalTax, date) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insertOrderSql)) {
                    ps.setInt(1, orderIdToUse);
                    ps.setInt(2, order.getBuyerID());
                    ps.setDouble(3, order.getTotalCost());
                    ps.setDouble(4, order.getTotalTax());
                    ps.setString(5, order.getDate());
                    ps.executeUpdate();
                }
            }

            // Insert lines
            String insertLineSql = "INSERT INTO orderLine (orderID, productID, quantity, cost) VALUES (?, ?, ?, ?)";
            try (PreparedStatement lps = connection.prepareStatement(insertLineSql)) {
                for (OrderLine line : order.getLines()) {
                    lps.setInt(1, orderIdToUse);
                    lps.setInt(2, line.getProductID());
                    lps.setDouble(3, line.getQuantity());
                    lps.setDouble(4, line.getCost());
                    lps.executeUpdate();

                    // Decrease product quantity
                    String updateQtySql = "UPDATE products SET quantity = quantity - ? WHERE productID = ?";
                    try (PreparedStatement ups = connection.prepareStatement(updateQtySql)) {
                        ups.setDouble(1, line.getQuantity());
                        ups.setInt(2, line.getProductID());
                        ups.executeUpdate();
                    }
                }
            }

            connection.commit();
            connection.setAutoCommit(originalAutoCommit);
            return true;
        } catch (SQLException e) {
            System.out.println("Database access error!");
            e.printStackTrace();
            try { connection.rollback(); } catch (SQLException ignore) {}
            try { connection.setAutoCommit(true); } catch (SQLException ignore) {}
            return false;
        }
    }

    public User loadUser(String username, String password) {
        String sql = "SELECT userID, username, userPassword, fullName, isManager FROM users WHERE username = ? AND userPassword = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                User user = new User();
                user.setUserID(rs.getInt("userID"));
                user.setUsername(rs.getString("username"));
                user.setPassword(rs.getString("userPassword"));
                user.setFullName(rs.getString("fullName"));
                // Note: User class currently has no setter for isManager
                return user;
            }
        } catch (SQLException e) {
            System.out.println("Database access error!");
            e.printStackTrace();
            return null;
        }
    }
}
