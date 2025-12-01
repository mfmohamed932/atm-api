-- Sample test data for ATM API
-- This file will be automatically executed by Spring Boot on startup

-- Insert sample accounts with 16-digit card numbers
-- Note: version column added for optimistic locking (will be initialized to 0 by JPA)
INSERT INTO accounts (id, card_number, customer_name, pin, balance, available_balance, daily_withdrawal_limit, daily_withdrawn_amount, last_withdrawal_date, active, version)
VALUES
    (1, '4532015112830366', 'John Doe', '1234', 5000.00, 5000.00, 1000.00, 0.00, CURRENT_DATE, true, 0),
    (2, '5425233430109903', 'Jane Smith', '5678', 10000.00, 10000.00, 2000.00, 0.00, CURRENT_DATE, true, 0),
    (3, '4916338506082832', 'Bob Johnson', '9012', 2500.00, 2500.00, 500.00, 0.00, CURRENT_DATE, true, 0),
    (4, '4024007134564842', 'Alice Williams', '3456', 15000.00, 15000.00, 3000.00, 0.00, CURRENT_DATE, true, 0),
    (5, '5200828282828210', 'Charlie Brown', '7890', 7500.00, 7500.00, 1500.00, 0.00, CURRENT_DATE, true, 0);

-- Insert some sample transactions (optional)
INSERT INTO transactions (id, account_id, type, amount, balance_after, timestamp, description)
VALUES
    (1, 1, 'DEPOSIT', 1000.00, 5000.00, CURRENT_TIMESTAMP, 'Initial deposit'),
    (2, 2, 'DEPOSIT', 5000.00, 10000.00, CURRENT_TIMESTAMP, 'Initial deposit'),
    (3, 3, 'DEPOSIT', 2500.00, 2500.00, CURRENT_TIMESTAMP, 'Initial deposit');

