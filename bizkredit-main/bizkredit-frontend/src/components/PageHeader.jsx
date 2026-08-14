import React from 'react';

export default function PageHeader({ eyebrow, title, subtitle, action }) {
  return (
    <div className="d-flex align-items-start justify-content-between mb-4">
      <div>
        {eyebrow && <div className="bk-page-eyebrow">{eyebrow}</div>}
        <h2 className="bk-page-title">{title}</h2>
        {subtitle && <p className="bk-page-sub mb-0">{subtitle}</p>}
      </div>
      {action && <div className="flex-shrink-0 ms-3">{action}</div>}
    </div>
  );
}
