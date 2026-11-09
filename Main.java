import java.util.PriorityQueue;
import java.util.Random;
import java.time.LocalTime;

abstract class Order implements Comparable<Order> {

    double price;
    int quantity;
    boolean isBuy;
    LocalTime timestamp;

    Order(double price, int quantity, boolean isBuy, LocalTime timestamp) {
        this.price = price;
        this.quantity = quantity;
        this.isBuy = isBuy;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {

        if (isBuy) {
            return "BUY Price: " + price + "      Qty: " + quantity + "    Time: " + timestamp;
        } else {
            return "SELL Price: " + price + "      Qty: " + quantity + "     Time: " + timestamp;
        }

    }

    abstract public int compareTo(Order other);
}
class buyOrder extends Order{

    buyOrder(double price, int quantity, boolean isBuy, LocalTime timestamp) {
        super(price,quantity,isBuy,timestamp);
    }

    @Override
    public int compareTo(Order other) {

        int priceComparison = Double.compare(other.price,this.price);

        if (priceComparison != 0) {
            return priceComparison;
        }

        return this.timestamp.compareTo(other.timestamp);
    }
}
class sellOrder extends Order{

    sellOrder(double price, int quantity, boolean isBuy, LocalTime timestamp) {
        super(price,quantity,isBuy,timestamp);
    }

    @Override
    public int compareTo(Order other) {

        int priceComparison = Double.compare(this.price,other.price);

        if (priceComparison != 0) {
            return priceComparison;
        }

        return this.timestamp.compareTo(other.timestamp);
    }
}

public class Main {

    static PriorityQueue<buyOrder> buyOrders = new PriorityQueue<>();
    static PriorityQueue<sellOrder> sellOrders = new PriorityQueue<>();

    public static void main(String[] args) {

        for (int i = 0; i < 5; i++) {
            createRandomBuyOrder();
        }
        for (int i = 0; i < 5; i++) {
            createRandomSellOrder();
        }
        PrintOrderBook();
        matchOrder();

    }

    static void PrintOrderBook() {

        PriorityQueue<buyOrder> tempBuy = new PriorityQueue<>(buyOrders);
        PriorityQueue<sellOrder> tempSell = new PriorityQueue<>(sellOrders);

        System.out.println("===== BUY BOOK =====");
        while (!tempBuy.isEmpty()) {
            System.out.println(tempBuy.poll());
        }

        System.out.println();

        System.out.println("===== SELL BOOK =====");
        while (!tempSell.isEmpty()) {
            System.out.println(tempSell.poll());
        }

        System.out.println();
    }
    
    static void createRandomBuyOrder() {

        Random rn = new Random();

        double randomBuyOrderPrice = rn.nextDouble(98.0, 105.0);
        int randomBuyOrderQty = rn.nextInt(1, 5);

        buyOrder order = new buyOrder(randomBuyOrderPrice, randomBuyOrderQty, true, LocalTime.now());
        buyOrders.add(order);

    }

    static void createRandomSellOrder() {

        Random rn = new Random();

        double randomSellOrderPrice = rn.nextDouble(98.0, 105.0);
        int randomSellOrderQty = rn.nextInt(1, 5);

        sellOrder order = new sellOrder(randomSellOrderPrice, randomSellOrderQty, false, LocalTime.now());
        sellOrders.add(order);
    }

    static void matchOrder() {

        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {

            buyOrder buy = buyOrders.peek();
            sellOrder sell = sellOrders.peek();

            // No more possible trades
            if (buy.price < sell.price) {
                System.out.println("\nNo more matching orders.\n");
                break;
            }

            int tradedQty = Math.min(buy.quantity, sell.quantity);

            System.out.println("------------------------------------");
            System.out.println("TRADE EXECUTED");
            System.out.println("Buyer Price : " + buy.price);
            System.out.println("Seller Price: " + sell.price);
            System.out.println("Trade Qty   : " + tradedQty);
            System.out.println("------------------------------------");

            // Reduce quantities
            buy.quantity -= tradedQty;
            sell.quantity -= tradedQty;

            // Remove completed orders
            if (buy.quantity == 0) {
                buyOrders.poll();
            }

            if (sell.quantity == 0) {
                sellOrders.poll();
            }

            PrintOrderBook();
        }

        if (buyOrders.isEmpty()) {
            System.out.println("Buy order book is empty.");
        }

        if (sellOrders.isEmpty()) {
            System.out.println("Sell order book is empty.");
        }
    }
}
