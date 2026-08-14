-- One-time cleanup for existing databases created before this fix.
--
-- Background: credit-service used to have a JPA entity mapping
-- `loan_application` with schema="bizkredit_sme_db". MySQL/InnoDB does not
-- support foreign keys across two different databases, so when Hibernate
-- (ddl-auto=update) generated a constraint on financial_statement.application_id
-- it silently created it against a local, empty shadow `loan_application`
-- table inside bizkredit_credit_db instead of the real table in
-- bizkredit_sme_db. That made every financial-statement insert fail with:
--   Cannot add or update a child row: a foreign key constraint fails
--   (`bizkredit_credit_db`.`financial_statement`, CONSTRAINT
--   `FKe6qcxqpar34n4xhlhtw546t3p` ...)
--
-- The dead entity/repository that caused this have been removed from the
-- codebase (see entity/LoanApplication.java, repository/LoanApplicationRepository.java
-- in git history). Run this script ONCE against bizkredit_credit_db to clean
-- up any database that already has the stray constraint/table.
--
-- Usage:
--   mysql -u root -p bizkredit_credit_db < cleanup_stray_loan_application_fk.sql

USE bizkredit_credit_db;

-- Drop the stray FK if it exists (name may vary per environment; adjust if needed)
SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = 'bizkredit_credit_db'
      AND TABLE_NAME = 'financial_statement'
      AND CONSTRAINT_NAME = 'FKe6qcxqpar34n4xhlhtw546t3p'
);

SET @drop_fk_sql := IF(@fk_exists > 0,
    'ALTER TABLE financial_statement DROP FOREIGN KEY FKe6qcxqpar34n4xhlhtw546t3p',
    'SELECT "FK already absent, skipping"');

PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop the local shadow loan_application table living inside
-- bizkredit_credit_db (the real, authoritative table lives in
-- bizkredit_sme_db and is untouched by this script).
SET @shadow_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'bizkredit_credit_db'
      AND TABLE_NAME = 'loan_application'
);

SET @drop_table_sql := IF(@shadow_table_exists > 0,
    'DROP TABLE loan_application',
    'SELECT "Shadow table already absent, skipping"');

PREPARE stmt FROM @drop_table_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
