-- ---------------------------------------------------------------------
-- The sign-in invite.
--
-- The only template in the catalogue that is email-only, and necessarily so:
-- somebody who cannot sign in yet will never see an in-app notification. Every
-- other template stays in-app by default, because turning on a mail server
-- should not silently start emailing fifteen kinds of message to everybody.
--
-- {link} is the reset path; the email channel completes it with app.base-url,
-- so the same row works in every environment without an edit.
-- ---------------------------------------------------------------------

INSERT INTO notification_templates
    (company_id, event_key, name, description, title_template, body_template, link_template,
     severity, in_app, email, is_active, created_at)
VALUES
(1, 'ACCOUNT_INVITE', 'Sign-in invite',
 'Emailed to a new joiner, and again whenever an administrator re-issues it',
 'Set your password for KRAPulse',
 'Hello {employeeName}, an account has been created for you.\n\nYour sign-in name is {signInName}. Choose your own password using the link below — it works once and expires on {expiresAt}, {validDays} days from now.\n\nNobody, including your administrator, can see the password you choose.',
 '{link}', 'INFO', 0, 1, 1, NOW());
