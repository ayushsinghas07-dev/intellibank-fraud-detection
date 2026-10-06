package com.intellibank.util.dsa;

import com.intellibank.model.Transaction;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Binary search utility for fast location of time-window boundaries
 * in chronologically sorted transaction lists.
 */
public class BinarySearchUtils {

    /**
     * Finds the index of the first transaction that occurred at or after targetTime.
     * Uses Collections.binarySearch with a custom Comparator.
     */
    public static int findFirstIndexAtOrAfter(List<Transaction> sortedTransactions, LocalDateTime targetTime) {
        if (sortedTransactions == null || sortedTransactions.isEmpty() || targetTime == null) {
            return 0;
        }

        Transaction dummyKey = new Transaction();
        dummyKey.setTransactionTimestamp(targetTime);

        Comparator<Transaction> timeComparator = Comparator.comparing(Transaction::getTransactionTimestamp);

        int index = Collections.binarySearch(sortedTransactions, dummyKey, timeComparator);

        if (index >= 0) {
            // Found exact timestamp match; rewind to first occurrence if duplicates exist
            while (index > 0 && sortedTransactions.get(index - 1).getTransactionTimestamp().equals(targetTime)) {
                index--;
            }
            return index;
        } else {
            // Insertion point is -index - 1
            int insertionPoint = -index - 1;
            return Math.min(insertionPoint, sortedTransactions.size());
        }
    }
}
