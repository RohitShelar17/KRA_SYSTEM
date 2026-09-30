/**
 * Mirrors the backend password policy so the user sees the rule before the
 * server rejects them. The server remains the authority.
 */
export const PASSWORD_RULE_TEXT =
  'At least 10 characters, with upper and lower case letters, a number and a symbol.';

const RULES = [
  /[a-z]/,
  /[A-Z]/,
  /[0-9]/,
  /[^A-Za-z0-9]/,
];

export function isStrongPassword(value: string): boolean {
  return value.length >= 10 && RULES.every((rule) => rule.test(value));
}

/** 0-4, for the strength meter on password fields. */
export function passwordStrength(value: string): number {
  if (!value) {
    return 0;
  }
  const met = RULES.filter((rule) => rule.test(value)).length;
  return value.length >= 10 ? met : Math.max(met - 1, 0);
}
