package com.qingping.app.data

data class AppStat(
    val packageName: String,
    val label: String,
    val total: Int,
    val splash: Int,
    val jump: Int,
    val lastAt: Long
)

data class StatsSummary(
    val total: Int,
    val today: Int,
    val appCount: Int
)

data class StatsSnapshot(
    val summary: StatsSummary,
    val apps: List<AppStat>
)
