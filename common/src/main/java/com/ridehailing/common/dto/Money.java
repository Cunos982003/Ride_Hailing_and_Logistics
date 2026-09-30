package com.ridehailing.common.dto;

import java.util.Objects;

public record Money(long amount, String currency) {
  public Money {
    if (amount < 0) {
      throw new IllegalArgumentException("Amount must not be negative");
    }
    if (!Objects.equals("VND", currency)) {
      throw new IllegalArgumentException("MVP supports VND only");
    }
  }

  public static Money vnd(long amount) {
    return new Money(amount, "VND");
  }
}
