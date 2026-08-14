// Shared reference data for financial figures across the app

export const RATIO_BENCHMARKS = {
  dscr: {
    label: 'DSCR (Debt Service Coverage Ratio)',
    healthy: 1.2,
    watch: 1.0,
    description: 'Can the business\'s income cover its debt payments? Above 1.2x is healthy; below 1.0x means income doesn\'t fully cover debt obligations.',
    evaluate: (value) => {
      if (value >= 1.2) return 'success';
      if (value >= 1.0) return 'warning';
      return 'danger';
    },
  },
  currentRatio: {
    label: 'Current Ratio',
    healthy: 1.5,
    watch: 1.0,
    description: 'Can short-term assets cover short-term liabilities? Above 1.5x is healthy; below 1.0x signals a liquidity risk.',
    evaluate: (value) => {
      if (value >= 1.5) return 'success';
      if (value >= 1.0) return 'warning';
      return 'danger';
    },
  },
  debtEquityRatio: {
    label: 'Debt-to-Equity Ratio',
    healthy: 1.0,
    watch: 2.0,
    description: 'How leveraged is the business? Below 1.0x is conservative; above 2.0x is highly leveraged and higher risk.',
    // Lower is better for this one - inverted evaluation
    evaluate: (value) => {
      if (value <= 1.0) return 'success';
      if (value <= 2.0) return 'warning';
      return 'danger';
    },
  },
};



export const EBITDA_MARGIN_BENCHMARK = {
  healthy: 15,
  watch: 8,
  description: 'EBITDA as a percentage of revenue. Above 15% is healthy for most SME sectors; below 8% suggests thin operating margins.',
  evaluate: (percent) => {
    if (percent >= 15) return 'success';
    if (percent >= 8) return 'warning';
    return 'danger';
  },
};
