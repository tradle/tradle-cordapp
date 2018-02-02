package com.template;

import net.corda.core.transactions.SignedTransaction;

public class TransactionIdWrapper {
    public final String txId;

    public TransactionIdWrapper(SignedTransaction tx) {
        this.txId = tx.getId().toString();
    }

    public TransactionIdWrapper(String txId) {
        this.txId = txId;
    }

    public String getTxId() {
        return txId;
    }
}
