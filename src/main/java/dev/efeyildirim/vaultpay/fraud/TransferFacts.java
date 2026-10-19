package dev.efeyildirim.vaultpay.fraud;

/**
 * Everything the fraud rules are allowed to know about a transfer, gathered once by {@link FraudService}
 * before scoring. Keeping this a plain value means every rule is a pure function and can be unit tested
 * without a database. "History" never includes the transfer being scored.
 *
 * @param amountMinor               the amount being transferred, in pence
 * @param toOwnAccount              the destination belongs to the same customer
 * @param knownPayee                money has moved from the source to this destination before
 * @param recentTransferCount       outgoing transfers from the source within {@link VelocityRule#WINDOW}
 * @param historyTransferCount      outgoing transfers from the source within {@link LargeAmountRule#HISTORY}
 * @param historyTransferTotalMinor total of those transfers, in pence
 * @param recentDepositsMinor       deposits into the source within {@link DepositThenTransferRule#WINDOW}
 */
public record TransferFacts(
        long amountMinor,
        boolean toOwnAccount,
        boolean knownPayee,
        long recentTransferCount,
        long historyTransferCount,
        long historyTransferTotalMinor,
        long recentDepositsMinor) {
}
