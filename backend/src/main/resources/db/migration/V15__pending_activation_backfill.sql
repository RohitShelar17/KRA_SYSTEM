-- ---------------------------------------------------------------------
-- Accounts created before PENDING_ACTIVATION meant anything.
--
-- Until V14 a new employee's account was written as ACTIVE while holding an
-- unused invite, so the directory showed people as open who could not sign in
-- at all. New accounts are now created PENDING_ACTIVATION; this brings the
-- ones already in the table into line with what was always true of them.
--
-- The predicate is deliberately narrow — never signed in, still owes a
-- password, and still holding an unspent invite. All three together describe
-- an account that cannot be signed into today, so nobody who can currently
-- work loses access; the row simply stops claiming otherwise. An account with
-- a real password, or one somebody deactivated, or one that has ever been used
-- is left exactly as it is.
--
-- The bootstrap administrator is excluded by the same test: it carries no
-- reset token, and on a fresh database it is still PENDING_ACTIVATION here
-- anyway, because BootstrapAdminInitializer runs after Flyway rather than
-- before it. On a new deployment this statement matches nothing, which is the
-- correct outcome rather than a sign it did not run.
-- ---------------------------------------------------------------------

UPDATE users
SET status = 'PENDING_ACTIVATION'
WHERE status = 'ACTIVE'
  AND is_active = 1
  AND last_login_at IS NULL
  AND must_change_password = 1
  AND reset_token IS NOT NULL;
