package com.banking.account_service.domain.model;

/** Products the bank offers and the customers each one accepts. */
public enum AccountProduct {
    SAVINGS(AccountType.SAVINGS),
    VIP_SAVINGS(AccountType.SAVINGS),
    CHECKING(AccountType.CHECKING),
    SME_CHECKING(AccountType.CHECKING),
    FIXED_TERM(AccountType.FIXED_TERM);

    private final AccountType type;

    AccountProduct(AccountType type) {
        this.type = type;
    }

    /** Account family of the product. */
    public AccountType type() {
        return type;
    }

    /**
     * Whether a customer of the given type and profile can open this product.
     * BUSINESS customers only open checking products; VIP_SAVINGS needs a VIP
     * personal customer and SME_CHECKING an SME business customer.
     */
    public boolean isAllowedFor(CustomerType customerType, CustomerProfile profile) {
        return switch (this) {
            case SAVINGS, FIXED_TERM -> customerType == CustomerType.PERSONAL;
            case VIP_SAVINGS -> customerType == CustomerType.PERSONAL && profile == CustomerProfile.VIP;
            case CHECKING -> true;
            case SME_CHECKING -> customerType == CustomerType.BUSINESS && profile == CustomerProfile.SME;
        };
    }

    /** Whether the customer must have an active credit card when the account is opened. */
    public boolean requiresCreditCard() {
        return this == VIP_SAVINGS || this == SME_CHECKING;
    }
}
