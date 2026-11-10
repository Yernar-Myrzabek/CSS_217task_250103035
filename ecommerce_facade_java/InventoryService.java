package com.ecommerce;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Subsystem Class 1: InventoryService
 * Manages inventory using lock-free atomic operations to handle concurrent checkouts.
 */
public class InventoryService {

    private final ConcurrentHashMap<String, AtomicInteger> stockDatabase;

    public InventoryService() {
        this.stockDatabase = new ConcurrentHashMap<>();
        this.stockDatabase.put("Laptop-X1", new AtomicInteger(50));
    }

    /**
     * Step 1: Atomically checks and reserves stock in a single JVM instruction cycle.
     * Prevents race conditions where two threads verify stock simultaneously before reserving.
     */
    public boolean atomicReserve(String productId) {
        AtomicInteger stock = stockDatabase.get(productId);

        if (stock == null) return false;

        int currentStock;
        do {
            currentStock = stock.get();
            if (currentStock <= 0) {
                System.out.println("[InventoryService] Aborting: Insufficient stock for " + productId);
                return false;
            }
        } while (!stock.compareAndSet(currentStock, currentStock - 1));

        System.out.println("[InventoryService] Stock reserved successfully. Remaining: " + (currentStock - 1));
        return true;
    }
}