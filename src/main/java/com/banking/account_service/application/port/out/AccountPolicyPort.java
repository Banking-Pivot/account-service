package com.banking.account_service.application.port.out;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.ProductPolicy;

/** Product terms loaded from the Config Server. Every product has terms for every currency. */
public interface AccountPolicyPort {

    ProductPolicy policyFor(AccountProduct product, CurrencyCode currency);
}
