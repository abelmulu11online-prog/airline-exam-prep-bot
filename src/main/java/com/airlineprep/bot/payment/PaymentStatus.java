package com.airlineprep.bot.payment;
import com.airlineprep.bot.common.ExamException;
public enum PaymentStatus {
 SELECT_METHOD, AWAITING_REFERENCE, AWAITING_RECEIPT, PENDING_REVIEW, APPROVED, REJECTED, CANCELLED;
 public boolean open() { return ordinal()<=PENDING_REVIEW.ordinal(); }
 public void require(PaymentStatus expected) { if(this!=expected) throw new ExamException("payment.state"); }
 public boolean cancellable() { return this==SELECT_METHOD||this==AWAITING_REFERENCE||this==AWAITING_RECEIPT; }
}
