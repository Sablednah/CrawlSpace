package com.sablednah.crawlspace.plan;

/**
 * How a corridor is routed. All four come from the same router with different
 * costs, which is what keeps a level from reading as a grid: the grid is only
 * where the path is searched, not how it is drawn.
 */
public enum CorridorStyle {
    /** Square turns only. HeroQuest's board. */
    STRAIGHT,
    /** 45-degree runs allowed. */
    ANGLED,
    /** Cheap turns over a noise field: a worn tunnel that wanders. */
    WINDING,
    /** Routed as ANGLED, then smoothed into curves where the smoothing stays clear of rooms. */
    CURVED
}
