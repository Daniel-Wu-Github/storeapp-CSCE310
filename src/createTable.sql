SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS orderLine;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    userID INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    userPassword VARCHAR(255) NOT NULL,
    fullName VARCHAR(100) NOT NULL,
    isManager BOOLEAN DEFAULT FALSE
);

CREATE TABLE products (
    productID INT PRIMARY KEY AUTO_INCREMENT,
    productName VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    quantity DOUBLE NOT NULL,
    sellerID INT,
    FOREIGN KEY (sellerID) REFERENCES users(userID)
);

CREATE TABLE orders (
    orderID INT PRIMARY KEY AUTO_INCREMENT,
    buyerID INT,
    totalCost DECIMAL(10, 2) NOT NULL,
    totalTax DECIMAL(10, 2) NOT NULL,
    date VARCHAR(255),
    FOREIGN KEY (buyerID) REFERENCES users(userID)
);

CREATE TABLE orderLine (
    orderID INT,
    productID INT,
    quantity DOUBLE NOT NULL,
    cost DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (orderID, productID),
    FOREIGN KEY (orderID) REFERENCES orders(orderID),
    FOREIGN KEY (productID) REFERENCES products(productID)
);

-- Insert a default manager user
INSERT INTO users (username, userPassword, fullName, isManager) VALUES ('manager', 'manager', 'global manager', TRUE);