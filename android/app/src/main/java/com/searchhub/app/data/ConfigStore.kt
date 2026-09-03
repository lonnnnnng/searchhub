package com.searchhub.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ConfigStore {
    private const val FILE = "config.json"

    data class Persist(
        val sites: List<SiteConfig>,
        val proxy: ProxyConfig,
    )

    fun load(context: Context): Pair<List<SiteConfig>, ProxyConfig> {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return SiteDefaults.DEFAULT_SITES to ProxyConfig()
        return try {
            val root = JSONObject(f.readText())
            val arr = root.optJSONArray("sites") ?: JSONArray()
            val sites = mutableListOf<SiteConfig>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                sites += SiteConfig(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    baseUrl = o.optString("baseUrl"),
                    enabled = o.optBoolean("enabled", true),
                    searchPath = o.optString("searchPath"),
                )
            }
            // 增量合并: 补上 defaults 里有但本地没保存的新站点(方便升级后直接接入新站)
            // 同时过滤掉默认配置中已移除的站点(如被下线的适配器)
            val defaultIds = SiteDefaults.DEFAULT_SITES.map { it.id }.toSet()
            val merged = sites.filter { it.id in defaultIds }.toMutableList()  // 保留存在于 defaults 的已保存站点
            val mergedIds = merged.map { it.id }.toSet()
            SiteDefaults.DEFAULT_SITES.forEach { def ->
                if (def.id !in mergedIds) merged += def
            }
            val p = root.optJSONObject("proxy") ?: JSONObject()
            val proxy = ProxyConfig(
                enabled = p.optBoolean("enabled", false),
                host = p.optString("host", "127.0.0.1"),
                port = p.optInt("port", 7890),
            )
            (if (merged.isEmpty()) SiteDefaults.DEFAULT_SITES else merged) to proxy
        } catch (e: Exception) {
            SiteDefaults.DEFAULT_SITES to ProxyConfig()
        }
    }

    fun save(context: Context, sites: List<SiteConfig>, proxy: ProxyConfig) {
        val root = JSONObject()
        val arr = JSONArray()
        sites.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("baseUrl", s.baseUrl)
                put("enabled", s.enabled); put("searchPath", s.searchPath)
            })
        }
        root.put("sites", arr)
        root.put("proxy", JSONObject().apply {
            put("enabled", proxy.enabled); put("host", proxy.host); put("port", proxy.port)
        })
        File(context.filesDir, FILE).writeText(root.toString())
    }

    fun reset(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}