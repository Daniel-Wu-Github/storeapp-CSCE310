import java.sql.*;
import java.util.*;

public class SQLiteToMySQLMigrator {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java SQLiteToMySQLMigrator <path-to-store.db>");
            System.exit(1);
        }
        String sqlitePath = args[0];

        // Standalone: create own MySQL connection and run migration
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection mysql = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/storeapp", "root", "1234")) {
            migrate(sqlitePath, mysql);
        }
    }

    // Public API to run migration using an existing MySQL connection
    public static void migrate(String sqlitePath, Connection mysql) throws Exception {
        // Connect to SQLite and copy rows into provided MySQL connection
        Class.forName("org.sqlite.JDBC");
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath)) {
            boolean originalAutoCommit = true;
            try {
                originalAutoCommit = mysql.getAutoCommit();
            } catch (SQLException ignore) {}

            mysql.setAutoCommit(false);
            try {
                // Discover the products table and column names from SQLite dynamically
                String productsTable = findProductsTable(sqlite);
                if (productsTable == null) {
                    throw new SQLException("Could not find a products table in SQLite. Checked tables: " + listTables(sqlite));
                }

                Map<String, String> colMap = mapProductColumns(sqlite, productsTable);
                validateRequiredColumns(colMap, Arrays.asList("productID", "productName", "price", "quantity"));

                String selectSql = String.format(
                    "SELECT \"%s\" AS productID, \"%s\" AS productName, \"%s\" AS price, \"%s\" AS quantity FROM \"%s\"",
                    colMap.get("productID"), colMap.get("productName"), colMap.get("price"), colMap.get("quantity"), productsTable
                );

                System.out.println("SQLite detect: table='" + productsTable + "' columns=" + colMap);

                try (
                    Statement s = sqlite.createStatement();
                    ResultSet rs = s.executeQuery(selectSql);
                    PreparedStatement upsert = mysql.prepareStatement(
                        "INSERT INTO products (productID, productName, price, quantity, sellerID) " +
                        "VALUES (?, ?, ?, ?, NULL) " +
                        "ON DUPLICATE KEY UPDATE productName=VALUES(productName), price=VALUES(price), quantity=VALUES(quantity), sellerID=VALUES(sellerID)"
                    )
                ) {
                    int count = 0;
                    while (rs.next()) {
                        int id = rs.getInt("productID");
                        String name = rs.getString("productName");
                        double price = rs.getDouble("price");
                        double qty = rs.getDouble("quantity");

                        upsert.setInt(1, id);
                        upsert.setString(2, name);
                        upsert.setDouble(3, price);
                        upsert.setDouble(4, qty);
                        upsert.addBatch();

                        if (++count % 500 == 0) {
                            upsert.executeBatch();
                        }
                    }
                    upsert.executeBatch();
                    mysql.commit();
                    System.out.println("Migrated " + count + " products from SQLite to MySQL.");
                }
            } catch (Exception ex) {
                try { mysql.rollback(); } catch (SQLException ignore) {}
                throw ex;
            } finally {
                try { mysql.setAutoCommit(originalAutoCommit); } catch (SQLException ignore) {}
            }
        }
    }

    private static String findProductsTable(Connection sqlite) throws SQLException {
        // Prefer common names like products/product
        List<String> tables = listTables(sqlite);
        Optional<String> preferred = tables.stream()
                .filter(t -> t.equalsIgnoreCase("products") || t.equalsIgnoreCase("product"))
                .findFirst();
        if (preferred.isPresent()) return preferred.get();

        // Fallback: any table that contains name+price columns
        for (String t : tables) {
            Set<String> cols = getLowercaseColumns(sqlite, t);
            if (cols.contains("name") && (cols.contains("price") || cols.contains("cost") || cols.contains("unitprice"))) {
                return t;
            }
        }
        return null;
    }

    private static Map<String, String> mapProductColumns(Connection sqlite, String table) throws SQLException {
        // Map canonical names -> actual column names in SQLite
        Set<String> colsLower = getLowercaseColumns(sqlite, table);
        Map<String, String> resolved = new HashMap<>();

        resolved.put("productID", pick(sqlite, table, colsLower, new String[]{"productid", "id"}));
        resolved.put("productName", pick(sqlite, table, colsLower, new String[]{"productname", "name", "title"}));
        resolved.put("price", pick(sqlite, table, colsLower, new String[]{"price", "unitprice", "cost"}));
        resolved.put("quantity", pick(sqlite, table, colsLower, new String[]{"quantity", "qty", "stock"}));

        return resolved;
    }

    private static String pick(Connection sqlite, String table, Set<String> colsLower, String[] candidates) throws SQLException {
        for (String c : candidates) {
            if (colsLower.contains(c)) {
                // Return actual cased name
                return getActualColumnName(sqlite, table, c);
            }
        }
        return null;
    }

    private static void validateRequiredColumns(Map<String, String> colMap, List<String> required) throws SQLException {
        List<String> missing = new ArrayList<>();
        for (String r : required) {
            if (colMap.get(r) == null || colMap.get(r).isEmpty()) missing.add(r);
        }
        if (!missing.isEmpty()) {
            throw new SQLException("Missing required columns in SQLite products table: " + missing + " (found map=" + colMap + ")");
        }
    }

    private static List<String> listTables(Connection sqlite) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement ps = sqlite.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private static Set<String> getLowercaseColumns(Connection sqlite, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (PreparedStatement ps = sqlite.prepareStatement("PRAGMA table_info(\"" + table + "\")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null) cols.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return cols;
    }

    private static String getActualColumnName(Connection sqlite, String table, String lowerName) throws SQLException {
        try (PreparedStatement ps = sqlite.prepareStatement("PRAGMA table_info(\"" + table + "\")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null && name.equalsIgnoreCase(lowerName)) return name;
                }
            }
        }
        return lowerName; // fallback to provided
    }
}