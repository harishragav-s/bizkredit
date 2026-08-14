// Converts every '' (empty string) value in a form object to null.

export function nullifyEmptyStrings(obj) {
  const cleaned = { ...obj };
  for (const key of Object.keys(cleaned)) {
    if (cleaned[key] === '') {
      cleaned[key] = null;
    }
  }
  return cleaned;
}
