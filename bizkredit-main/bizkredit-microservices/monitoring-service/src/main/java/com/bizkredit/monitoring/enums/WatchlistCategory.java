package com.bizkredit.monitoring.enums;

// BP2-39 - watchlist classification driven by covenant breach frequency,
// reusing the SMA naming convention already established for overdue-based
// NPA classification (see NPARecord/NPAClassificationService) since both
// describe the same underlying idea: escalating severity of a
// deteriorating account.
public enum WatchlistCategory {
    NONE,
    SMA_0,
    SMA_1,
    SMA_2
}
