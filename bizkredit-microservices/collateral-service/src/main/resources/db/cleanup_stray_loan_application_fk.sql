-- One-time cleanup for existing databases created before this fix.
--
-- Same root cause as the credit-service cleanup script: collateral-service
-- had dead JPA entities (LoanApplication, and a CreditProposal/UnderwritingDecision
-- chain pointing at it) mapped with schema="bizkredit_sme_db" / "bizkredit_credit_db".
-- MySQL/InnoDB cannot enforce foreign keys across two different databases, so
-- Hibernate (ddl-auto=update) silently created the constraint against a local,
-- empty shadow `loan_application` table inside bizkredit_collateral_db instead.
-- That made every collateral_record insert fail with:
--   Cannot add or update a child row: a foreign key constraint fails
--   (`bizkredit_collateral_db`.`collateral_record`, CONSTRAINT
--   `FKb41m835ncj8qlv6kwgidxyka0` ...)
--
-- The dead entities/repositories that caused this have been removed from the
-- codebase. Run this script ONCE against bizkredit_collateral_db to clean up
-- any database that already has the stray constraint/table.
--
-- Usage:
--   mysql -u root -p bizkredit_collateral_db < cleanup_stray_loan_application_fk.sql

USE bizkredit_collateral_db;

-- Drop any FK in this database that references the stray local loan_application
-- table (covers collateral_record and any other table Hibernate may have
-- attached one to, e.g. facility_account).
SET @fk_cursor = (
    SELECT GROUP_CONCAT(
        CONCAT('ALTER TABLE `', TABLE_NAME, '` DROP FOREIGN KEY `', CONSTRAINT_NAME, '`')
        SEPARATOR ';\n'
    )
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'bizkredit_collateral_db'
      AND REFERENCED_TABLE_NAME = 'loan_application'
);

-- Print the statements this script is about to run, for visibility/audit
SELECT @fk_cursor AS statements_to_run;

-- Run them one at a time (adjust/re-run manually if you have more than a
-- couple of stray FKs - GROUP_CONCAT + PREPARE only executes one statement,
-- so for multiple FKs prefer copying the printed statements above and running
-- them directly instead of relying on this single PREPARE call).
SET @first_stmt = (
    SELECT CONCAT('ALTER TABLE `', TABLE_NAME, '` DROP FOREIGN KEY `', CONSTRAINT_NAME, '`')
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'bizkredit_collateral_db'
      AND REFERENCED_TABLE_NAME = 'loan_application'
    LIMIT 1
);

SET @drop_fk_sql := IFNULL(@first_stmt, 'SELECT "No stray FK found, skipping"');
PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Drop the local shadow loan_application table living inside
-- bizkredit_collateral_db (the real, authoritative table lives in
-- bizkredit_sme_db and is untouched by this script).
SET @shadow_table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'bizkredit_collateral_db'
      AND TABLE_NAME = 'loan_application'
);

SET @drop_table_sql := IF(@shadow_table_exists > 0,
    'DROP TABLE loan_application',
    'SELECT "Shadow table already absent, skipping"');

PREPARE stmt FROM @drop_table_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
