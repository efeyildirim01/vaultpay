package dev.efeyildirim.vaultpay.fraud;

/** Test builder for {@link TransferFacts}. Starts from a harmless transfer; each test changes one thing. */
final class Facts {

    private long amountMinor = 1_000;
    private boolean toOwnAccount = false;
    private boolean knownPayee = true;
    private long recentTransferCount = 0;
    private long historyTransferCount = 0;
    private long historyTransferTotalMinor = 0;
    private long recentDepositsMinor = 0;

    static Facts harmless() {
        return new Facts();
    }

    Facts amount(long minor) {
        this.amountMinor = minor;
        return this;
    }

    Facts toOwnAccount() {
        this.toOwnAccount = true;
        return this;
    }

    Facts newPayee() {
        this.knownPayee = false;
        return this;
    }

    Facts recentTransfers(long count) {
        this.recentTransferCount = count;
        return this;
    }

    Facts history(long count, long totalMinor) {
        this.historyTransferCount = count;
        this.historyTransferTotalMinor = totalMinor;
        return this;
    }

    Facts recentDeposits(long minor) {
        this.recentDepositsMinor = minor;
        return this;
    }

    TransferFacts build() {
        return new TransferFacts(amountMinor, toOwnAccount, knownPayee, recentTransferCount,
                historyTransferCount, historyTransferTotalMinor, recentDepositsMinor);
    }
}
