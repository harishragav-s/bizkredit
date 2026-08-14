// Formats a number as Indian currency using the lakh/cr convention

export function formatINR(value) {
  if (value === null || value === undefined || value === '') return '—';
  const num = Number(value);
  if (Number.isNaN(num)) return String(value);

  const abs = Math.abs(num);
  const sign = num < 0 ? '-' : '';

  if (abs >= 1_00_00_000) {
    return `${sign}₹${(abs / 1_00_00_000).toFixed(2)} Cr`;
  }
  if (abs >= 1_00_000) {
    return `${sign}₹${(abs / 1_00_000).toFixed(2)} L`;
  }
  // Below 1 lakh, show with Indian-style comma grouping (ex :85,000)
  return `${sign}₹${abs.toLocaleString('en-IN')}`;
}
