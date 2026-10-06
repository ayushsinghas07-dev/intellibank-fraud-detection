package com.intellibank.util.dsa;

import com.intellibank.model.FraudAlert;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * PriorityQueue (Max-Heap) implementation that prioritizes fraud alerts
 * and suspicious transactions by risk score descending.
 * Powers the Priority Review Queue in the analyst dashboard.
 */
public class RiskPriorityQueue {
    private final PriorityQueue<FraudAlert> maxHeap;

    public RiskPriorityQueue() {
        this.maxHeap = new PriorityQueue<>((a, b) -> Integer.compare(b.getRiskScore(), a.getRiskScore()));
    }

    public synchronized void addAlert(FraudAlert alert) {
        if (alert != null) {
            maxHeap.add(alert);
        }
    }

    public synchronized FraudAlert pollHighestRisk() {
        return maxHeap.poll();
    }

    public synchronized FraudAlert peekHighestRisk() {
        return maxHeap.peek();
    }

    public synchronized List<FraudAlert> getTopN(int n) {
        List<FraudAlert> result = new ArrayList<>();
        PriorityQueue<FraudAlert> tempCopy = new PriorityQueue<>(maxHeap);
        int count = 0;
        while (!tempCopy.isEmpty() && count < n) {
            result.add(tempCopy.poll());
            count++;
        }
        return result;
    }

    public synchronized int size() {
        return maxHeap.size();
    }

    public synchronized void clear() {
        maxHeap.clear();
    }
}
