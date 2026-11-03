package com.qingping.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.qingping.app.util.PackageUtils
import java.util.Calendar
import java.util.concurrent.Executors

object StatsRepository {

    const val TYPE_SPLASH = "splash"
    const val TYPE_JUMP = "jump"

    private const val TAG = "StatsRepository"

    private var dbHelper: StatsDbHelper? = null
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Synchronized
    private fun db(context: Context): StatsDbHelper {
        dbHelper?.let { return it }
        return StatsDbHelper(context.applicationContext).also { dbHelper = it }
    }

    fun record(context: Context, packageName: String, type: String) {
        if (packageName.isBlank()) return
        val appContext = context.applicationContext
        io.execute {
            try {
                val values = ContentValues().apply {
                    put("package_name", packageName)
                    put("app_label", PackageUtils.appLabel(appContext, packageName))
                    put("type", type)
                    put("created_at", System.currentTimeMillis())
                }
                db(appContext).writableDatabase.insert(StatsDbHelper.TABLE, null, values)
            } catch (e: Exception) {
                Log.w(TAG, "record failed", e)
            }
        }
    }

    fun load(context: Context, callback: (StatsSnapshot) -> Unit) {
        val appContext = context.applicationContext
        io.execute {
            val snapshot = try {
                query(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                StatsSnapshot(StatsSummary(0, 0, 0), emptyList())
            }
            main.post { callback(snapshot) }
        }
    }

    fun clear(context: Context, callback: () -> Unit) {
        val appContext = context.applicationContext
        io.execute {
            try {
                db(appContext).writableDatabase.delete(StatsDbHelper.TABLE, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "clear failed", e)
            }
            main.post { callback() }
        }
    }

    private fun query(context: Context): StatsSnapshot {
        val database = db(context).readableDatabase

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val today = database.rawQuery(
            "SELECT COUNT(*) FROM ${StatsDbHelper.TABLE} WHERE created_at >= ?",
            arrayOf(startOfToday.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

        val apps = mutableListOf<AppStat>()
        val sql = """
            SELECT i.package_name,
                   (SELECT x.app_label
                      FROM ${StatsDbHelper.TABLE} x
                     WHERE x.package_name = i.package_name
                     ORDER BY x.created_at DESC
                     LIMIT 1) AS app_label,
                   COUNT(*) AS total,
                   SUM(CASE WHEN i.type = ? THEN 1 ELSE 0 END) AS splash_count,
                   SUM(CASE WHEN i.type = ? THEN 1 ELSE 0 END) AS jump_count,
                   MAX(i.created_at) AS last_at
              FROM ${StatsDbHelper.TABLE} i
             GROUP BY i.package_name
             ORDER BY total DESC, last_at DESC
        """.trimIndent()

        database.rawQuery(sql, arrayOf(TYPE_SPLASH, TYPE_JUMP)).use { cursor ->
            val idxPackage = cursor.getColumnIndexOrThrow("package_name")
            val idxLabel = cursor.getColumnIndexOrThrow("app_label")
            val idxTotal = cursor.getColumnIndexOrThrow("total")
            val idxSplash = cursor.getColumnIndexOrThrow("splash_count")
            val idxJump = cursor.getColumnIndexOrThrow("jump_count")
            val idxLast = cursor.getColumnIndexOrThrow("last_at")

            while (cursor.moveToNext()) {
                val pkg = cursor.getString(idxPackage)
                apps.add(
                    AppStat(
                        packageName = pkg,
                        label = cursor.getString(idxLabel) ?: pkg,
                        total = cursor.getInt(idxTotal),
                        splash = cursor.getInt(idxSplash),
                        jump = cursor.getInt(idxJump),
                        lastAt = cursor.getLong(idxLast)
                    )
                )
            }
        }

        return StatsSnapshot(
            summary = StatsSummary(
                total = apps.sumOf { it.total },
                today = today,
                appCount = apps.size
            ),
            apps = apps
        )
    }
}
