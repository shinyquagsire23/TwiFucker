package icu.nullptr.twifucker.hook

import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XC_MethodHook
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

// root
fun JSONObject.jsonGetTweets(): JSONObject? =
    optJSONObject("globalObjects")?.optJSONObject("tweets")

fun JSONObject.jsonGetThreads(): JSONArray? =
    optJSONArray("threads")

fun JSONObject.jsonGetInstructions(): JSONArray? =
    optJSONObject("timeline")?.optJSONArray("instructions")

fun JSONObject.jsonGetData(): JSONObject? = optJSONObject("data")

// data
fun JSONObject.dataGetInstructions(): JSONArray? =
    optJSONObject("user_result")?.optJSONObject("result")?.optJSONObject("timeline_response")
        ?.optJSONObject("timeline")?.optJSONArray("instructions") ?: optJSONObject(
        "timeline_response"
    )?.optJSONArray("instructions")

fun JSONObject.dataCheckAndRemove() {
    dataGetInstructions()?.forEach<JSONObject> { instruction ->
        instruction.instructionCheckAndRemove()
    }
    dataGetLegacy()?.legacyCheckAndRemove()
    dataGetUserLegacy()?.userLegacyCheckAndRemove()
}

fun JSONObject.dataGetLegacy(): JSONObject? =
    optJSONObject("tweet_result")?.optJSONObject("result")?.optJSONObject("legacy")

fun JSONObject.dataGetUserLegacy(): JSONObject? =
    optJSONObject("user_result")?.optJSONObject("result")?.optJSONObject("legacy")


// tweets
fun JSONObject.tweetsForEach(action: (JSONObject) -> Unit) {
    for (i in keys()) {
        optJSONObject(i)?.let { action(it) }
    }
}

// tweet
fun JSONObject.tweetGetExtendedEntitiesMedias(): JSONArray? =
    optJSONObject("extended_entities")?.optJSONArray("media")

fun JSONObject.tweetCheckAndRemove() {
    tweetGetExtendedEntitiesMedias()?.forEach<JSONObject> { media ->
        media.mediaCheckAndRemove()
    }
}

fun JSONObject.threadCheckAndRemove() {

}

// entry
fun JSONObject.entryHasPromotedMetadata(): Boolean =
    optJSONObject("content")?.optJSONObject("item")?.optJSONObject("content")
        ?.optJSONObject("tweet")
        ?.has("promotedMetadata") == true || optJSONObject("content")?.optJSONObject("content")
        ?.has("tweetPromotedMetadata") == true

fun JSONObject.entryIsWhoToFollow(): Boolean = optString("entryId").startsWith("whoToFollow-")

fun JSONObject.entryIsTopicsModule(): Boolean = optString("entryId").startsWith("TopicsModule-")

fun JSONObject.entryGetContentItems(): JSONArray? =
    optJSONObject("content")?.optJSONArray("items")
        ?: optJSONObject("content")?.optJSONObject("timelineModule")?.optJSONArray("items")

fun JSONObject.entryIsTweet(): Boolean = optString("entryId").startsWith("tweet-")
fun JSONObject.entryIsConversationThread(): Boolean =
    optString("entryId").startsWith("conversationthread-")

fun JSONObject.entryGetLegacy(): JSONObject? {
    val temp = when {
        has("content") -> {
            optJSONObject("content")
        }
        has("item") -> {
            optJSONObject("item")
        }
        else -> return null
    }
    return temp?.optJSONObject("content")?.optJSONObject("tweetResult")
        ?.optJSONObject("result")?.optJSONObject("legacy")
}

fun JSONObject.entryGetTrends(): JSONArray? =
    optJSONObject("content")?.optJSONObject("timelineModule")?.optJSONArray("items")

fun JSONObject.entryGetEvent(): JSONObject? {
    return optJSONObject("content")
}

// trend
fun JSONObject.trendHasPromotedMetadata(): Boolean =
    optJSONObject("item")?.optJSONObject("content")?.optJSONObject("trend")
        ?.has("promotedMetadata") == true

fun JSONObject.trendIsSports(): Boolean {
    val sports_names = listOf("Sports", "NBA", "NFL", "MLB", "NHL", "MLS", "NLL", "PLL", "WNBA", "WNSL", "CWHL", "NWHL", "ESPN", "Football", "Basketball", "Soccer",
        "Hockey", "Tennis", "Lacrosse", "Golf")
    if (optJSONObject("item")?.optJSONObject("content")?.has("tile") == true) {
        return true
    }
    var topicFull = optJSONObject("item")?.optJSONObject("content")?.optJSONObject("trend")?.optJSONObject("trendMetadata")?.getStringOrDefault("domainContext", "no domain context")
    if (topicFull == null) {
        topicFull = optJSONObject("item")?.optJSONObject("content")?.optJSONObject("eventSummary")?.getStringOrDefault("supportingText", "no supporting text")
    }
    var topic = ""
    if (topicFull != null) {
        if (topicFull.contains("Trending in ")) {
            topic = topicFull.split("Trending in ")[1]
        }
        else {
            topic = topicFull.split(" ")[0]
        }
    }

    if (sports_names.contains(topic)) {
        return true
    }
    return false
}

fun JSONArray.trendRemoveAds() {
    if (!modulePrefs.getBoolean("disable_promoted_trends", true)) return
    val trendRemoveIndex = mutableListOf<Int>()
    forEachIndexed<JSONObject> { trendIndex, trend ->
        if (trend.trendHasPromotedMetadata()) {
            //Log.d("Handle trends ads $trendIndex $trend")
            trendRemoveIndex.add(trendIndex)
        }
        if (trend.trendIsSports()) {
            trendRemoveIndex.add(trendIndex)
        }
    }
    for (i in trendRemoveIndex.asReversed()) {
        remove(i)
    }
}

// legacy
fun JSONObject.legacyGetRetweetedStatusLegacy(): JSONObject? =
    optJSONObject("retweeted_status_result")?.optJSONObject("result")?.optJSONObject("legacy")

fun JSONObject.legacyGetExtendedEntitiesMedias(): JSONArray? =
    optJSONObject("extended_entities")?.optJSONArray("media")

fun JSONObject.legacyHasDescription(): Boolean =
    has("description")

fun JSONObject.legacyGetDescription(): String? =
    getStringOrDefault("description", "!! missing description !!")

fun JSONObject.legacyCheckAndRemove() {
    legacyGetExtendedEntitiesMedias()?.forEach<JSONObject> { media ->
        media.mediaCheckAndRemove()
    }
    legacyGetRetweetedStatusLegacy()?.legacyGetExtendedEntitiesMedias()
        ?.forEach<JSONObject> { media ->
            media.mediaCheckAndRemove()
        }
}

@Throws(IOException::class, JSONException::class)
fun getJSONObjectFromURL(urlString: String?): JSONObject? {
    var urlConnection: HttpURLConnection? = null
    val url = URL(urlString)
    urlConnection = url.openConnection() as HttpURLConnection
    urlConnection.setRequestMethod("GET")
    urlConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
    urlConnection.setReadTimeout(1000 /* milliseconds */)
    urlConnection.setConnectTimeout(1500 /* milliseconds */)
    //urlConnection.setDoOutput(true)
    urlConnection.connect()
    val resp = urlConnection.getResponseCode()
    if (resp != 200) {
        return JSONObject("{\"errorcode\":" + resp.toString() + "}")
    }
    //val stream = urlConnection.getErrorStream()
    val stream = urlConnection.getInputStream()
    //val stream = url.openStream()
    val br = BufferedReader(InputStreamReader(stream))
    val jsonString = br.use(BufferedReader::readText)
    //Log.d("JSON: $jsonString")
    urlConnection.disconnect()
    return JSONObject(jsonString)
}

fun JSONObject.userLegacyCheckAndRemove(): Boolean {
    var should_filter = false

    //Log.d("Legacy info: " + toString())
    if (legacyHasDescription()) {
        val oldDesc = legacyGetDescription()
        //Log.d("Found a user description: " + oldDesc)
        remove("description");
        var newDesc = oldDesc + " || Snooping | "
        var id_str = "unk"
        if (has("id_str")) {
            newDesc += "ID "
            id_str =  getStringOrDefault("id_str", "unk")
            newDesc += id_str
            newDesc += " || "

            try {
                val info = getJSONObjectFromURL("https://memory.lol/tw/id/" + id_str)
                //val info = getJSONObjectFromURL("https://gist.githubusercontent.com/shinyquagsire23/19f581c2c7eaf1d80005fe42b448f9bf/raw/4a36431ba02445ab11e479239a52ed93c2e13f52/gistfile1.txt")

                if (info?.has("errorcode") == true) {
                    newDesc += "memory.lol err? " + info.getString("errorcode")
                    newDesc += " || "
                }

                //Log.d("info " + info.toString())

                info?.optJSONArray("accounts")?.forEachIndexed<JSONObject> { index, item ->
                    //Log.d("item " + item.toString())
                    if (item.has("id")) {
                        //Log.d(item.getString("id") + " " + id_str)
                        if (item.getString("id").equals(id_str)) {
                            newDesc += "Previously: "
                            item.optJSONObject("screen-names")?.names()?.forEachIndexed<String> { index, name ->
                                //Log.d("prev " + name.toString())
                                if (index != 0) {
                                    newDesc += " | "
                                }
                                newDesc += "@" + name
                            }
                            newDesc += " || "
                            return@forEachIndexed
                        }
                    }
                }
            }
            catch(e: Throwable) {
                Log.e(e)
            }
        }

        put("description", newDesc);
    }

    return should_filter
}

fun JSONObject.tweetLegacyCheckAndRemove(): Boolean {
    var should_filter = false

    optJSONObject("entities")?.optJSONArray("urls")
        ?.forEachIndexed<JSONObject> { idx, urlEnt ->
            //Log.d(urlEnt.getString("display_url").toString())
            val uri = URI(urlEnt.getString("expanded_url").lowercase())
            val domain = uri.host
            val stripped = if (domain.startsWith("www.")) domain.substring(4) else domain

            val spammy_names = listOf("suctional.com", "dentrie.com", "sunsetic.com",
                "oceangalaxylight.com", "owlprojector.com", "shp.ee" ,"oceangalaxylight.shop")
            if (spammy_names.contains(stripped)) {
                should_filter = true
            }

            if (should_filter) {
                put("full_text", "vibrator ad")
                if (optJSONObject("extended_entities")?.has("media") == true) {
                    optJSONObject("extended_entities")?.put("media",
                        JSONArray(ArrayList<JSONObject?>()))
                }
                return@forEachIndexed
            }
        }
    //optJSONObject("extended_entities")?.optJSONArray("media")

    if (optJSONObject("core")?.optJSONObject("user_result")?.optJSONObject("result")?.optJSONObject("legacy")?.userLegacyCheckAndRemove() == true) {
        should_filter = true
    }
    return should_filter
}

// item
fun JSONObject.itemContainsPromotedUser(): Boolean = optJSONObject("item")?.optJSONObject("content")
    ?.has("userPromotedMetadata") == true || optJSONObject("item")?.optJSONObject("content")
    ?.optJSONObject("user")
    ?.has("userPromotedMetadata") == true || optJSONObject("item")?.optJSONObject("content")
    ?.optJSONObject("user")?.has("promotedMetadata") == true

// instruction
fun JSONObject.instructionTimelinePinEntry(): JSONObject? = optJSONObject("entry")
fun JSONObject.instructionTimelineAddEntries(): JSONArray? = optJSONArray("entries")

fun JSONObject.instructionGetAddEntries(): JSONArray? =
    optJSONObject("addEntries")?.optJSONArray("entries")

fun JSONObject.instructionCheckAndRemove() {
    instructionTimelinePinEntry()?.entryRemoveSensitiveMediaWarning()
    instructionTimelinePinEntry()?.entryChangeDescription()
    instructionTimelineAddEntries()?.entriesRemoveAnnoyance()
    instructionGetAddEntries()?.entriesRemoveAnnoyance()
}

// media
fun JSONObject.mediaHasSensitiveMediaWarning(): Boolean =
    has("sensitive_media_warning") || (has("ext_sensitive_media_warning") && optJSONObject("ext_sensitive_media_warning") != null)

fun JSONObject.mediaRemoveSensitiveMediaWarning() {
    remove("sensitive_media_warning")
    remove("ext_sensitive_media_warning")
}

fun JSONObject.mediaCheckAndRemove() {
    if (!modulePrefs.getBoolean("disable_sensitive_media_warning", false)) return
    if (mediaHasSensitiveMediaWarning()) {
        //Log.d("Handle sensitive media warning $this")
        mediaRemoveSensitiveMediaWarning()
    }
}

// entries
fun JSONArray.entriesRemoveTimelineAds() {
    val removeIndex = mutableListOf<Int>()
    forEachIndexed<JSONObject> { entryIndex, entry ->
        entry.entryGetTrends()?.trendRemoveAds()
        if (entry.entryGetEvent()?.trendIsSports() == true) {
            removeIndex.add(entryIndex)
        }

        if (!modulePrefs.getBoolean("disable_promoted_content", true)) return@forEachIndexed
        if (entry.entryHasPromotedMetadata()) {
            //Log.d("Handle timeline ads $entryIndex $entry")
            removeIndex.add(entryIndex)
        }
    }
    for (i in removeIndex.reversed()) {
        remove(i)
    }
}

fun JSONArray.entriesRemoveWhoToFollow() {
    val entryRemoveIndex = mutableListOf<Int>()
    forEachIndexed<JSONObject> { entryIndex, entry ->
        if (!entry.entryIsWhoToFollow()) return@forEachIndexed

        if (modulePrefs.getBoolean("disable_who_to_follow", false)) {
            //Log.d("Handle whoToFollow $entryIndex $entry")
            entryRemoveIndex.add(entryIndex)
            return@forEachIndexed
        }

        if (!modulePrefs.getBoolean("disable_promoted_user", true)) return@forEachIndexed

        val items = entry.entryGetContentItems()
        val userRemoveIndex = mutableListOf<Int>()
        items?.forEachIndexed<JSONObject> { index, item ->
            item.itemContainsPromotedUser().let {
                if (it) {
                    //Log.d("Handle whoToFollow promoted user $index $item")
                    userRemoveIndex.add(index)
                }
            }
        }
        for (i in userRemoveIndex.reversed()) {
            items?.remove(i)
        }
    }
    for (i in entryRemoveIndex.reversed()) {
        remove(i)
    }
}

fun JSONArray.entriesRemoveTopicsToFollow() {
    val entryRemoveIndex = mutableListOf<Int>()
    forEachIndexed<JSONObject> { entryIndex, entry ->
        if (!entry.entryIsTopicsModule()) return@forEachIndexed

        if (modulePrefs.getBoolean("disable_topics_to_follow", false)) {
            //Log.d("Handle TopicsModule $entryIndex $entry")
            entryRemoveIndex.add(entryIndex)
            return@forEachIndexed
        }
    }
    for (i in entryRemoveIndex.reversed()) {
        remove(i)
    }
}

fun JSONObject.entryRemoveSensitiveMediaWarning() {
    if (entryIsTweet()) {
        entryGetLegacy()?.let {
            it.legacyGetExtendedEntitiesMedias()?.forEach<JSONObject> { media ->
                media.mediaCheckAndRemove()
            }
            it.legacyGetRetweetedStatusLegacy()?.legacyGetExtendedEntitiesMedias()
                ?.forEach<JSONObject> { media ->
                    media.mediaCheckAndRemove()
                }
        }

        optJSONObject("content")?.optJSONObject("content")?.optJSONObject("tweetResult")?.optJSONObject("result")?.optJSONObject("legacy")?.tweetLegacyCheckAndRemove()
        optJSONObject("content")?.optJSONObject("content")?.optJSONObject("tweetResult")?.optJSONObject("result")?.optJSONObject("core")?.optJSONObject("user_result")?.optJSONObject("result")?.optJSONObject("legacy")?.userLegacyCheckAndRemove()

    } else if (entryIsConversationThread()) {
        entryGetContentItems()?.forEach<JSONObject> { item ->
            item.entryGetLegacy()?.let { legacy ->
                legacy.legacyGetExtendedEntitiesMedias()?.forEach<JSONObject> { media ->
                    media.mediaCheckAndRemove()
                }
            }

            //Log.d(item.toString())

            item.optJSONObject("item")?.optJSONObject("content")?.optJSONObject("tweetResult")?.optJSONObject("result")?.optJSONObject("legacy")?.tweetLegacyCheckAndRemove()
            //item.optJSONObject("item")?.optJSONObject("content")?.optJSONObject("tweetResult")?.optJSONObject("result")?.optJSONObject("core")?.optJSONObject("user_result")?.optJSONObject("result")?.optJSONObject("legacy")?.userLegacyCheckAndRemove()
        }
    }
}

fun JSONObject.entryChangeDescription() {
   // if (entryIsTweet() || entryIsConversationThread()) {
        //optJSONObject("content")?.optJSONObject("content")?.optJSONObject("tweetResult")?.optJSONObject("result")?.optJSONObject("legacy")?.userLegacyCheckAndRemove()
        //optJSONObject("content")?.optJSONObject("content")?.optJSONObject("tweetResult")?.optJSONObject("result")?.optJSONObject("core")?.optJSONObject("user_result")?.optJSONObject("result")?.optJSONObject("legacy")?.userLegacyCheckAndRemove()
    //}
}

fun JSONArray.entriesRemoveSensitiveMediaWarning() {
    forEach<JSONObject> { entry ->
        entry.entryRemoveSensitiveMediaWarning()
    }
}

fun JSONArray.entriesChangeDescription() {
    //forEach<JSONObject> { entry ->
    //    entry.entryChangeDescription()
   //}
}

fun JSONArray.entriesRemoveAnnoyance() {
    entriesRemoveTimelineAds()
    entriesRemoveWhoToFollow()
    entriesRemoveTopicsToFollow()
    entriesRemoveSensitiveMediaWarning()
    //entriesChangeDescription()
}


fun handleJson(param: XC_MethodHook.MethodHookParam) {
    val inputStream = param.result as InputStream
    val reader = BufferedReader(inputStream.reader())
    var content: String

    try {
        reader.use { r ->
            content = r.readText()
            //Log.d("Raw JSON: " + content)
        }
    } catch (_: java.net.SocketTimeoutException) {
        return
    }

    try {
        val json = JSONObject(content)

        Log.d("Tweet foreach")
        json.jsonGetTweets()?.tweetsForEach { tweet ->
            tweet.tweetCheckAndRemove()
        }
        Log.d("Instruction foreach")
        json.jsonGetInstructions()?.forEach<JSONObject> { instruction ->
            instruction.instructionCheckAndRemove()
        }
        /*
        json.jsonGetThreads()?.forEach<JSONObject> { thread ->
            thread.threadCheckAndRemove()
        }
        */
        Log.d("Threads")
        if (json.has("threads")) {
            json.put("threads", JSONArray(ArrayList<JSONObject?>()))
        }
        Log.d("Data")
        json.jsonGetData()?.dataCheckAndRemove()
        Log.d("Done")

        content = json.toString()
    } catch (_: JSONException) {
    } catch (e: Throwable) {
        Log.e(e)
        //Log.d(content)
    }

    try {
        val json = JSONArray(content)
        json.forEach<Any> {
            if (it is JSONObject) {
                it.tweetCheckAndRemove()
            }
        }
        content = json.toString()
    } catch (_: JSONException) {
    } catch (e: Throwable) {
        Log.e(e)
        //Log.d(content)
    }

    param.result = content.byteInputStream()
}

fun jsonHook() {
    try {
        val jsonClass =
            findField("com.bluelinelabs.logansquare.LoganSquare") { name == "JSON_FACTORY" }.type
        Log.d("Located json class")
        val jsonMethod = findMethod(jsonClass) {
            isFinal && parameterTypes.size == 2 && parameterTypes[0] == InputStream::class.java && returnType == InputStream::class.java
        }
        Log.d("Located json method")
        jsonMethod.hookAfter { param ->
            handleJson(param)
        }
    } catch (e: Throwable) {
        Log.e("Failed to relocate json method", e)
    }
}
