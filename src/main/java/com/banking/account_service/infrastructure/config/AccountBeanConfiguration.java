package com.banking.account_service.infrastructure.config;

import com.banking.account_service.application.handler.command.CloseAccountHandler;
import com.banking.account_service.application.handler.command.CustomerEligibilityVerifier;
import com.banking.account_service.application.handler.command.DepositHandler;
import com.banking.account_service.application.handler.command.IdempotentOperationExecutor;
import com.banking.account_service.application.handler.command.OpenAccountHandler;
import com.banking.account_service.application.handler.command.SingleAccountPosting;
import com.banking.account_service.application.handler.command.TransferHandler;
import com.banking.account_service.application.handler.command.UpdateAccountHandler;
import com.banking.account_service.application.handler.command.WithdrawHandler;
import com.banking.account_service.application.handler.query.GenerateProductReportHandler;
import com.banking.account_service.application.handler.query.GetAccountBalanceHandler;
import com.banking.account_service.application.handler.query.GetAccountHandler;
import com.banking.account_service.application.handler.query.GetDailyAverageBalanceHandler;
import com.banking.account_service.application.handler.query.ListAccountMovementsHandler;
import com.banking.account_service.application.handler.query.ListAccountsHandler;
import com.banking.account_service.application.port.in.CloseAccountUseCase;
import com.banking.account_service.application.port.in.DepositUseCase;
import com.banking.account_service.application.port.in.GenerateProductReportUseCase;
import com.banking.account_service.application.port.in.GetAccountBalanceUseCase;
import com.banking.account_service.application.port.in.GetAccountUseCase;
import com.banking.account_service.application.port.in.GetDailyAverageBalanceUseCase;
import com.banking.account_service.application.port.in.ListAccountMovementsUseCase;
import com.banking.account_service.application.port.in.ListAccountsUseCase;
import com.banking.account_service.application.port.in.OpenAccountUseCase;
import com.banking.account_service.application.port.in.TransferUseCase;
import com.banking.account_service.application.port.in.UpdateAccountUseCase;
import com.banking.account_service.application.port.in.WithdrawUseCase;
import com.banking.account_service.application.port.out.AccountPolicyPort;
import com.banking.account_service.application.port.out.AccountReadPort;
import com.banking.account_service.application.port.out.AccountWritePort;
import com.banking.account_service.application.port.out.CreditCardPort;
import com.banking.account_service.application.port.out.CustomerDirectoryPort;
import com.banking.account_service.application.port.out.MovementReadPort;
import com.banking.account_service.application.port.out.MovementWritePort;
import com.banking.account_service.application.port.out.OperationLogPort;
import com.banking.account_service.application.port.out.ProductReportPort;
import com.banking.account_service.application.port.out.TransactionPort;
import com.banking.account_service.application.support.BusinessCalendar;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the application handlers without putting Spring annotations on them. Command
 * handlers use write ports; query handlers use read ports only.
 */
@Configuration
@EnableConfigurationProperties(AccountProperties.class)
public class AccountBeanConfiguration {

    @Bean
    public Clock accountClock() {
        return Clock.systemUTC();
    }

    @Bean
    public BusinessCalendar businessCalendar(Clock clock, AccountProperties properties) {
        return new BusinessCalendar(clock, properties.businessZone());
    }

    @Bean
    public CustomerEligibilityVerifier customerEligibilityVerifier(
            CustomerDirectoryPort customers,
            CreditCardPort creditCards
    ) {
        return new CustomerEligibilityVerifier(customers, creditCards);
    }

    @Bean
    public IdempotentOperationExecutor idempotentOperationExecutor(
            OperationLogPort operations,
            TransactionPort transactions
    ) {
        return new IdempotentOperationExecutor(operations, transactions);
    }

    @Bean
    public SingleAccountPosting singleAccountPosting(
            AccountWritePort accounts,
            MovementWritePort movements,
            AccountPolicyPort policies,
            BusinessCalendar calendar
    ) {
        return new SingleAccountPosting(accounts, movements, policies, calendar);
    }

    @Bean
    public OpenAccountUseCase openAccountUseCase(
            AccountWritePort accounts,
            MovementWritePort movements,
            OperationLogPort operations,
            TransactionPort transactions,
            AccountPolicyPort policies,
            CustomerEligibilityVerifier eligibility,
            BusinessCalendar calendar
    ) {
        return new OpenAccountHandler(accounts, movements, operations, transactions, policies, eligibility, calendar);
    }

    @Bean
    public UpdateAccountUseCase updateAccountUseCase(
            AccountWritePort accounts,
            TransactionPort transactions,
            CustomerEligibilityVerifier eligibility,
            BusinessCalendar calendar
    ) {
        return new UpdateAccountHandler(accounts, transactions, eligibility, calendar);
    }

    @Bean
    public CloseAccountUseCase closeAccountUseCase(
            AccountWritePort accounts,
            TransactionPort transactions,
            BusinessCalendar calendar
    ) {
        return new CloseAccountHandler(accounts, transactions, calendar);
    }

    @Bean
    public DepositUseCase depositUseCase(IdempotentOperationExecutor executor, SingleAccountPosting posting) {
        return new DepositHandler(executor, posting);
    }

    @Bean
    public WithdrawUseCase withdrawUseCase(IdempotentOperationExecutor executor, SingleAccountPosting posting) {
        return new WithdrawHandler(executor, posting);
    }

    @Bean
    public TransferUseCase transferUseCase(
            IdempotentOperationExecutor executor,
            AccountWritePort accounts,
            MovementWritePort movements,
            AccountPolicyPort policies,
            BusinessCalendar calendar
    ) {
        return new TransferHandler(executor, accounts, movements, policies, calendar);
    }

    @Bean
    public GetAccountUseCase getAccountUseCase(AccountReadPort accounts) {
        return new GetAccountHandler(accounts);
    }

    @Bean
    public ListAccountsUseCase listAccountsUseCase(AccountReadPort accounts) {
        return new ListAccountsHandler(accounts);
    }

    @Bean
    public GetAccountBalanceUseCase getAccountBalanceUseCase(
            AccountReadPort accounts,
            AccountPolicyPort policies,
            BusinessCalendar calendar
    ) {
        return new GetAccountBalanceHandler(accounts, policies, calendar);
    }

    @Bean
    public ListAccountMovementsUseCase listAccountMovementsUseCase(
            AccountReadPort accounts,
            MovementReadPort movements
    ) {
        return new ListAccountMovementsHandler(accounts, movements);
    }

    @Bean
    public GetDailyAverageBalanceUseCase getDailyAverageBalanceUseCase(
            AccountReadPort accounts,
            MovementReadPort movements,
            AccountPolicyPort policies,
            BusinessCalendar calendar
    ) {
        return new GetDailyAverageBalanceHandler(accounts, movements, policies, calendar);
    }

    @Bean
    public GenerateProductReportUseCase generateProductReportUseCase(
            ProductReportPort reports,
            BusinessCalendar calendar
    ) {
        return new GenerateProductReportHandler(reports, calendar);
    }
}
