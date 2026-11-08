-- Account Service schema (PostgreSQL, exclusive to this service).
--
-- Money is NUMERIC(19, 2). Instants are TIMESTAMPTZ and the application writes them in UTC.
-- Business dates (DATE) follow the business time zone (account.business-zone) and drive
-- the monthly quota, the fixed-term day and the reports.
-- Customer IDs reference Customer Service: no foreign key leaves this database.

CREATE TABLE accounts (
    id                UUID           NOT NULL,
    primary_holder_id UUID           NOT NULL,
    customer_type     VARCHAR(20)    NOT NULL,
    customer_profile  VARCHAR(20)    NOT NULL,
    account_type      VARCHAR(20)    NOT NULL,
    product           VARCHAR(20)    NOT NULL,
    currency          CHAR(3)        NOT NULL,
    balance           NUMERIC(19, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    -- Monthly counter: first day of the business month it belongs to, and movements counted.
    usage_month       DATE           NOT NULL,
    usage_movements   INTEGER        NOT NULL,
    -- Sequence number of the last movement of the account.
    movement_sequence BIGINT         NOT NULL,
    opened_at         TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    closed_at         TIMESTAMPTZ,
    -- Revision exposed as ETag; it changes with every change, balance included.
    version           BIGINT         NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_accounts_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT ck_accounts_closed_balance CHECK (status = 'ACTIVE' OR balance = 0),
    CONSTRAINT ck_accounts_currency CHECK (currency IN ('PEN', 'USD')),
    CONSTRAINT ck_accounts_customer_type CHECK (customer_type IN ('PERSONAL', 'BUSINESS')),
    CONSTRAINT ck_accounts_customer_profile CHECK (customer_profile IN ('STANDARD', 'VIP', 'SME')),
    CONSTRAINT ck_accounts_product_type CHECK (
        (product IN ('SAVINGS', 'VIP_SAVINGS') AND account_type = 'SAVINGS')
        OR (product IN ('CHECKING', 'SME_CHECKING') AND account_type = 'CHECKING')
        OR (product = 'FIXED_TERM' AND account_type = 'FIXED_TERM')),
    CONSTRAINT ck_accounts_product_customer CHECK (
        (product IN ('SAVINGS', 'FIXED_TERM') AND customer_type = 'PERSONAL')
        OR (product = 'VIP_SAVINGS' AND customer_type = 'PERSONAL' AND customer_profile = 'VIP')
        OR product = 'CHECKING'
        OR (product = 'SME_CHECKING' AND customer_type = 'BUSINESS' AND customer_profile = 'SME')),
    CONSTRAINT ck_accounts_usage_month CHECK (EXTRACT(DAY FROM usage_month) = 1),
    CONSTRAINT ck_accounts_counters CHECK (usage_movements >= 0 AND movement_sequence >= 0 AND version >= 0)
);

-- A personal customer has at most one ACTIVE savings account (VIP savings included) and one
-- ACTIVE checking account. Fixed-term accounts and business accounts are not limited.
-- The index decides when two openings race; closed accounts do not count.
CREATE UNIQUE INDEX uk_accounts_personal_active_type
    ON accounts (primary_holder_id, account_type)
    WHERE customer_type = 'PERSONAL' AND status = 'ACTIVE' AND account_type IN ('SAVINGS', 'CHECKING');

CREATE INDEX ix_accounts_product_status ON accounts (product, status);

CREATE TABLE account_holders (
    account_id  UUID    NOT NULL,
    customer_id UUID    NOT NULL,
    is_primary  BOOLEAN NOT NULL,
    CONSTRAINT pk_account_holders PRIMARY KEY (account_id, customer_id),
    CONSTRAINT fk_account_holders_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE UNIQUE INDEX uk_account_holders_primary ON account_holders (account_id) WHERE is_primary;
CREATE INDEX ix_account_holders_customer ON account_holders (customer_id);

CREATE TABLE account_signers (
    account_id  UUID NOT NULL,
    customer_id UUID NOT NULL,
    CONSTRAINT pk_account_signers PRIMARY KEY (account_id, customer_id),
    CONSTRAINT fk_account_signers_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX ix_account_signers_customer ON account_signers (customer_id);

-- One row per operationId, the client's idempotency key. The primary key coordinates
-- concurrent requests with the same key: the second INSERT waits for the first transaction.
-- The row is inserted as PROCESSING at the start of the transaction and completed before it
-- commits, so other transactions only ever see COMPLETED rows.
--
-- No foreign key to accounts on purpose: the key is claimed before the account rows are
-- locked, and a foreign key check would take a share lock on them out of order.
CREATE TABLE operations (
    id                      UUID           NOT NULL,
    operation_type          VARCHAR(20)    NOT NULL,
    account_id              UUID           NOT NULL,
    counterparty_account_id UUID,
    amount                  NUMERIC(19, 2) NOT NULL,
    description             VARCHAR(140),
    status                  VARCHAR(20)    NOT NULL,
    currency                CHAR(3),
    transfer_kind           VARCHAR(20),
    fee                     NUMERIC(19, 2),
    balance_after           NUMERIC(19, 2),
    occurred_at             TIMESTAMPTZ,
    business_date           DATE,
    CONSTRAINT pk_operations PRIMARY KEY (id),
    CONSTRAINT ck_operations_type CHECK (operation_type IN ('OPENING_DEPOSIT', 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_operations_status CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_operations_amount CHECK (amount > 0),
    CONSTRAINT ck_operations_counterparty CHECK ((operation_type = 'TRANSFER') = (counterparty_account_id IS NOT NULL)),
    CONSTRAINT ck_operations_distinct_accounts CHECK (counterparty_account_id IS NULL OR counterparty_account_id <> account_id),
    CONSTRAINT ck_operations_transfer_kind CHECK (transfer_kind IS NULL OR operation_type = 'TRANSFER'),
    CONSTRAINT ck_operations_result CHECK (
        status = 'PROCESSING'
        OR (currency IS NOT NULL AND fee IS NOT NULL AND balance_after IS NOT NULL
            AND occurred_at IS NOT NULL AND business_date IS NOT NULL
            AND (operation_type <> 'TRANSFER' OR transfer_kind IS NOT NULL)))
);

CREATE INDEX ix_operations_account ON operations (account_id);

-- Account history. Rows are only inserted, never updated or deleted. Each row keeps the
-- amount, the fee charged with it, the balance right after it and its operation.
CREATE TABLE account_movements (
    id                      UUID           NOT NULL,
    account_id              UUID           NOT NULL,
    operation_id            UUID           NOT NULL,
    sequence_number         BIGINT         NOT NULL,
    movement_type           VARCHAR(20)    NOT NULL,
    amount                  NUMERIC(19, 2) NOT NULL,
    fee                     NUMERIC(19, 2) NOT NULL,
    balance_after           NUMERIC(19, 2) NOT NULL,
    counterparty_account_id UUID,
    description             VARCHAR(140),
    occurred_at             TIMESTAMPTZ    NOT NULL,
    business_date           DATE           NOT NULL,
    CONSTRAINT pk_account_movements PRIMARY KEY (id),
    CONSTRAINT fk_account_movements_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_account_movements_operation FOREIGN KEY (operation_id) REFERENCES operations (id),
    CONSTRAINT uk_account_movements_sequence UNIQUE (account_id, sequence_number),
    CONSTRAINT uk_account_movements_operation UNIQUE (operation_id, account_id),
    CONSTRAINT ck_account_movements_type CHECK (
        movement_type IN ('OPENING_DEPOSIT', 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT')),
    CONSTRAINT ck_account_movements_amounts CHECK (amount > 0 AND fee >= 0 AND balance_after >= 0),
    CONSTRAINT ck_account_movements_sequence CHECK (sequence_number >= 1),
    CONSTRAINT ck_account_movements_counterparty CHECK (
        (movement_type IN ('TRANSFER_IN', 'TRANSFER_OUT')) = (counterparty_account_id IS NOT NULL))
);

CREATE INDEX ix_account_movements_account_date ON account_movements (account_id, business_date);
CREATE INDEX ix_account_movements_date ON account_movements (business_date);
