import React from 'react';

export default function LogoMark({ size = 32 }) {
  const fontSize = size * 0.55;
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" style={{ flexShrink: 0 }}>
      <rect width="32" height="32" rx="9" fill="url(#bkLogoGradient)" />

      <circle cx="16" cy="16" r="12" stroke="#ffffff" strokeOpacity="0.18" strokeWidth="1.4" fill="none" />
      <text
        x="16" y="17.5"
        textAnchor="middle"
        dominantBaseline="middle"
        fontSize={fontSize}
        fontWeight="800"
        fontFamily="'DejaVu Sans', Arial, sans-serif"
        fill="#ffffff"
      >
        ₹
      </text>
      <defs>
        <linearGradient id="bkLogoGradient" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#16a34a" />
          <stop offset="55%" stopColor="#22c55e" />
          <stop offset="100%" stopColor="#f59e0b" />
        </linearGradient>
      </defs>
    </svg>
  );
}
