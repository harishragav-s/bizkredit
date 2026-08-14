import React from 'react';
import { formatINR } from '../utils/currency';

// Unified amount entry: type any exact number OR pick a quick preset.
// Shows formatted  value live below the input.

const PRESETS = [
  { label: '₹1Cr',   value: 10000000 },
  { label: '₹2Cr',   value: 20000000 },
  { label: '₹5Cr',   value: 50000000 },
  { label: '₹10Cr',  value: 100000000 },
  { label: '₹25Cr',  value: 250000000 },
  { label: '₹50Cr',  value: 500000000 },
];

export default function SmartAmountInput({
  value,
  onChange,
  min,
  max,
  required,
  placeholder = 'Type amount in ₹',
  showPresets = true,
}) {
  const num = Number(value);

  // Filter presets to min/max range if provided
  const visiblePresets = showPresets
    ? PRESETS.filter((p) => {
        if (min != null && p.value < Number(min)) return false;
        if (max != null && p.value > Number(max)) return false;
        return true;
      })
    : [];

  const handleType = (e) => {
    onChange({ target: { value: e.target.value } });
  };

  const handlePreset = (val) => {
    onChange({ target: { value: String(val) } });
  };

  return (
    <div>
      <input
        type="number"
        className="form-control bk-input"
        placeholder={placeholder}
        required={required}
        value={value}
        onChange={handleType}
        min={min}
        max={max}
      />
      {/* Live formatted display */}
      {value !== '' && value !== null && value !== undefined && (
        <div style={{ marginTop: '4px',
        }}>
          = {formatINR(value)}
        </div>
      )}
    </div>
  );
}
