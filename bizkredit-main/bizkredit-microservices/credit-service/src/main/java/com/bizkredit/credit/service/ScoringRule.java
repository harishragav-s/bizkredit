package com.bizkredit.credit.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Parsed shape of {@code ScorecardModel.ScorecardParameter#scoringRules}.
 *
 * Two supported shapes (a parameter uses exactly one):
 *
 * 1) Numeric / band-based - for ratios, ranges, counts, amounts etc.
 *    {
 *      "bands": [
 *        { "min": 0,    "max": 1.0,  "points": 20 },
 *        { "min": 1.0,  "max": 1.5,  "points": 60 },
 *        { "min": 1.5,  "max": null, "points": 100 }
 *      ]
 *    }
 *    - min is inclusive, max is exclusive. A null/absent "max" means "and above".
 *    - A null/absent "min" means "and below" (only meaningful on the first band).
 *    - Bands are evaluated in order; the first matching band wins.
 *
 * 2) Categorical / lookup-based - for text fields like industry, entity type, kyc status.
 *    {
 *      "values": { "Manufacturing": 90, "Trading": 70, "Services": 80 },
 *      "default": 40
 *    }
 *    - Exact, case-insensitive match against "values". Falls back to "default" if present,
 *      otherwise 0.
 *
 * "points" / values in both shapes are on a 0-100 scale; the engine multiplies by the
 * parameter's weight (which sums to 100 across a scorecard) to get the weighted contribution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoringRule {

    private List<Band> bands;
    private Map<String, BigDecimal> values;
    private BigDecimal _default; // "default" - renamed because default is a reserved word

    public List<Band> getBands() {
        return bands;
    }

    public void setBands(List<Band> bands) {
        this.bands = bands;
    }

    public Map<String, BigDecimal> getValues() {
        return values;
    }

    public void setValues(Map<String, BigDecimal> values) {
        this.values = values;
    }

    public BigDecimal getDefault() {
        return _default;
    }

    public void setDefault(BigDecimal _default) {
        this._default = _default;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Band {
        private BigDecimal min;
        private BigDecimal max;
        private BigDecimal points;

        public BigDecimal getMin() {
            return min;
        }

        public void setMin(BigDecimal min) {
            this.min = min;
        }

        public BigDecimal getMax() {
            return max;
        }

        public void setMax(BigDecimal max) {
            this.max = max;
        }

        public BigDecimal getPoints() {
            return points;
        }

        public void setPoints(BigDecimal points) {
            this.points = points;
        }
    }
}
