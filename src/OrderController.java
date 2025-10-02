import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.awt.event.ActionListener;
import javax.swing.*;
//import joptionPane;

public class OrderController implements ActionListener {
    private OrderView view;
    private Order order = null;

    public OrderController(OrderView view) {
        this.view = view;

        view.getBtnAdd().addActionListener(this);
        view.getBtnPay().addActionListener(this);

        order = new Order();

    }


    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == view.getBtnAdd())
            addProduct();
        else
        if (e.getSource() == view.getBtnPay())
            makeOrder();
    }

    private void makeOrder() {
        if (order.getLines().isEmpty()) {
            JOptionPane.showMessageDialog(null, "No items in the order.");
            return;
        }

        // Set buyer if logged in
        User current = Application.getInstance().getCurrentUser();
        if (current != null) {
            order.setBuyerID(current.getUserID());
        }

        // Set date/time and simple tax (adjust rate if needed)
        order.setDate(LocalDateTime.now().toString());
        double taxRate = 0.0; // set to e.g., 0.09 for 9% if you need tax
        order.setTotalTax(order.getTotalCost() * taxRate);

        boolean ok = Application.getInstance().getDataAdapter().saveOrder(order);
        if (ok) {
            JOptionPane.showMessageDialog(null, "Order saved successfully. OrderID: " + order.getOrderID());
            // reset UI for a new order
            this.view.getLabTotal().setText("Total: $");
            // Create a fresh order for next transaction
            this.order = new Order();
        } else {
            JOptionPane.showMessageDialog(null, "Failed to save order. See logs for details.");
        }

    }

    private void addProduct() {
        String id = JOptionPane.showInputDialog("Enter ProductID: ");
        Product product = Application.getInstance().getDataAdapter().loadProduct(Integer.parseInt(id));
        if (product == null) {
            JOptionPane.showMessageDialog(null, "This product does not exist!");
            return;
        }

        double quantity = Double.parseDouble(JOptionPane.showInputDialog(null,"Enter quantity: "));

        if (quantity < 0 || quantity > product.getQuantity()) {
            JOptionPane.showMessageDialog(null, "This quantity is not valid!");
            return;
        }

        OrderLine line = new OrderLine();
        line.setOrderID(this.order.getOrderID());
        line.setProductID(product.getProductID());
        line.setQuantity(quantity);
        line.setCost(quantity * product.getPrice());
        order.getLines().add(line);
        order.setTotalCost(order.getTotalCost() + line.getCost());



        Object[] row = new Object[5];
        row[0] = line.getProductID();
        row[1] = product.getName();
        row[2] = product.getPrice();
        row[3] = line.getQuantity();
        row[4] = line.getCost();

        this.view.addRow(row);
        this.view.getLabTotal().setText("Total: $" + order.getTotalCost());
        this.view.invalidate();
    }

}