package kz.lvk.languagelearning.core.update

import org.json.JSONObject

class UpdateManifestParser {
    fun parse(json: String): UpdateManifest {
        val root = JSONObject(json)
        val packageObject = root.getJSONObject("package")
        val notesArray = root.optJSONArray("notes")

        val notes = buildList {
            if (notesArray != null) {
                for (index in 0 until notesArray.length()) {
                    add(notesArray.optString(index))
                }
            }
        }

        return UpdateManifest(
            schemaVersion = root.optInt("schemaVersion", 1),
            appId = root.getString("appId"),
            name = root.getString("name"),
            latestVersion = root.getString("latestVersion"),
            versionCode = if (root.has("versionCode")) root.optLong("versionCode") else null,
            channel = root.optString("channel", "stable"),
            mandatory = root.optBoolean("mandatory", false),
            packageName = root.optString("packageName").takeIf(String::isNotBlank),
            packageInfo = UpdatePackage(
                url = packageObject.getString("url"),
                sha256 = packageObject.optString("sha256").takeIf(String::isNotBlank),
                size = if (packageObject.has("size")) packageObject.optLong("size") else null,
            ),
            notes = notes,
        )
    }
}
