package eu.kanade.tachiyomi.extension.ru.mangabuff

import android.annotation.SuppressLint
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaBuff : ParsedHttpSource() {

    override val name = "MangaBuff"
    override val baseUrl = "https://mangabuff.ru"
    override val lang = "ru"
    override val versionId: Int = 1
    override val supportsLatest = false

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", baseUrl)

    override val id: Long = 1

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
//        Log.d("MangaBuff", "searchMangaRequest")
        var pageNum = 1
        when {
            page < 1 -> pageNum = 1
            page >= 1 -> pageNum = page
        }
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search?q=$query&page=$pageNum"
        } else {
            "$baseUrl/manga?page=$pageNum"
        }

        return GET(url, headers)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(2)
        .build()

    override fun popularMangaRequest(page: Int): Request {
//        Log.d("MangaBuff", "popularMangaRequest, page=$page")
        return GET("$baseUrl/manga?page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
//        Log.d("MangaBuff", "latestUpdatesRequest")
        return GET("$baseUrl/manga?sort=updated_at}")
    }

    override fun popularMangaSelector() = "a.cards__item"

//    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
//        Log.d("MangaBuff", "MangaFromElement") // \n" + element.toString())
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + element.select("div.cards__img").first()!!
            .attr("style")
            .replace("background-image: url('", "")
            .replace("')", "")
        // .substring(6)
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("div.cards__name").first()!!.text()
//        Log.d("MangaBuff", "Manga parsed\n" + manga.toString())
//        Log.d("MangaBuff", "Manga thumbnail_url " + manga.thumbnail_url)
//        Log.d("MangaBuff", "Manga title " + manga.title)
//        Log.d("MangaBuff", "Manga url " + manga.url)
        return manga
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

//    override fun latestUpdatesFromElement(element: Element): SManga {
//        val manga = SManga.create()
//        manga.thumbnail_url = element.select("a.updates__img > img").first()!!.attr("src")
//        manga.title = element.select("a.updates__name").first()!!.text()
//        return manga
//    }

    override fun latestUpdatesSelector(): String {
        return ".updates__list.updates--all-list"
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaNextPageSelector() = "a:contains(Вперёд)"

    override fun searchMangaNextPageSelector() = "a:contains(Вперёд)"

//    private fun searchGenresNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var hasNextPage = false

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val nextSearchPage = document.select(searchMangaNextPageSelector())
        if (nextSearchPage.isNotEmpty()) {
//            val query = document.select("input#searchinput").first()!!.attr("value")
//            val pageNum = nextSearchPage.let { selector ->
//                val onClick = selector.attr("onclick")
//                onClick.split("""\\d+""")
//            }
//            nextSearchPage.attr("href", "$baseUrl/?do=search&subaction=search&story=$query&search_start=$pageNum")
            hasNextPage = true
        }

//        val nextGenresPage = document.select(searchGenresNextPageSelector())
//        if (nextGenresPage.isNotEmpty()) {
//            hasNextPage = true
//        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(document: Document): SManga {
//        Log.d("MangaBuff", "mangaDetailsParse") // , document:\n" + document.toString())
        val descElement = document.select("div.manga__description").first()!!
        // Log.d("MangaBuff", "descElement:\n" + descElement)
        val status = document.select("a.manga__middle-link").get(2)!!.text()
//        Log.d("MangaBuff", "status:\n" + status)
//        Log.d("MangaBuff", "title:\n" + document.select("h1.manga__name").text()) // .first()!!.text())
//        Log.d("MangaBuff", "thumbnail_url:\n" + baseUrl + document.select(".manga__img > img").first()!!.attr("src"))
        val manga = SManga.create()
        manga.title = document.select("h1.manga__name").text()
        manga.author = ""
        manga.genre = ""
        manga.status = parseStatus(status)
        manga.description = descElement.text().trim()
        manga.thumbnail_url = baseUrl + document.select(".manga__img > img").first()!!.attr("src")
//        Log.d(
//            "MangaBuff",
//            "manga:\n" +
//                "title: " + manga.title + "\n" +
//                "author: " + manga.author + "\n" +
//                "genre: " + manga.genre + "\n" +
//                "status: " + manga.status + "\n" +
//                "description: " + manga.description + "\n" +
//                "thumbnail_url: " + manga.thumbnail_url + "\n",
//        )
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Завершен") -> SManga.COMPLETED
        element.contains("Продолжается") -> SManga.ONGOING
        element.contains("Заморожен") -> SManga.ON_HIATUS
        element.contains("Заброшен") -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".chapters__item"

    @SuppressLint("SetJavaScriptEnabled")
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
//        Log.d("MangaBuff", "fetchChapterList") // , element:\n" + element.toString())

        val response = client.newCall(chapterListRequest(manga)).execute()

        val document = response.asJsoup()
//        val script = document.selectFirst("script:containsData(const CHAPTER_ID)")?.data()
//            ?: throw java.lang.Exception("Failed to get chapter id")

        val mangaId = document.select(".manga").attr("data-id")
        val csrfToken = document.select("meta[name=csrf-token]").attr("content")

        val requestHeaders = headersBuilder().apply {
//            add("Accept", "*/*")
//            add("Host", baseUrl.toHttpUrl().host)
//            set("Referer", response.request.url.toString())
//            add("X-Requested-With", "XMLHttpRequest")
            add("X-CSRF-TOKEN", csrfToken)
        }.build()

        val requestBody = "manga_id=$mangaId".toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaTypeOrNull())

        val ajaxResponse = client.newCall(
            POST("$baseUrl/chapters/load", requestHeaders, requestBody),
        ).execute()

        var data = ajaxResponse
//        Log.d("MangaBuff", "ajaxResponse.asJsoup():\n" + ajaxResponse.asJsoup())
        val jsonData: String = data.body.string()
//        Log.d("MangaBuff", "jsonData:\n" + jsonData)
        val Jobject = JSONObject(jsonData)
//        Log.d("MangaBuff", "Jobject:\n" + Jobject.toString())
//        Log.d("MangaBuff", "Jobject.has(\"content\"):\n" + Jobject.has("content"))
        val content = Jobject.get("content").toString()
//        Log.d("MangaBuff", "Jobject.get(content):\n" + content)
        val additionalDocument = Jsoup.parse(content, "", Parser.xmlParser())

        var newerChapters: Elements = document.select(".chapters__item")
        var olderChapters: Elements = additionalDocument.select(".chapters__item")

        val chapters = mutableListOf<SChapter>()

        for (el: Element in newerChapters) {
            chapters.add(chapterFromElement(el))
        }
        for (el: Element in olderChapters) {
            chapters.add(chapterFromElement(el))
        }

        return Observable.just(chapters)

//        Log.d("MangaBuff", "csrfToken:\n" + csrfToken) // , element:\n" + element.toString())
//        Log.d("MangaBuff", "csrfToken:\n" + csrfToken) // , element:\n" + element.toString())
//        Log.d("MangaBuff", "ajaxResponse:\n" + data) // , element:\n" + element.toString())
//        Log.d("MangaBuff", "ajaxResponse body:\n" + data.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()).toString()) // , element:\n" + element.toString())
    }

    override fun chapterFromElement(element: Element): SChapter {
//        Log.d("MangaBuff", "chapterFromElement") // , element:\n" + element.toString())
//        Log.d("MangaBuff", "setUrlWithoutDomain:\n" + element.attr("href"))
//        Log.d(
//            "MangaBuff",
//            "name:\n" + element.select(".chapters__volume").first()!!.text() +
//                " " + element.select(".chapters__value").first()!!.text() +
//                " " + element.select(".chapters__name").first()!!.text(),
//        )
//        Log.d("MangaBuff", "chapter_number:\n" + element.select("div.chapters__value > span").first()!!.text().toFloat())
//        Log.d("MangaBuff", "date_upload:\n" + element.select("div.chapters__add-date").first()!!.text())

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.select(".chapters__volume").first()!!.text() +
            " " + element.select(".chapters__value").first()!!.text() +
            " " + element.select(".chapters__name").first()!!.text()
        chapter.chapter_number = element.select("div.chapters__value > span").first()!!.text().toFloat()
        val dataText = element.select("div.chapters__add-date").first()!!.text()
        try {
            chapter.date_upload = simpleDateFormat.parse(dataText).time
        } catch (e: ParseException) {
            chapter.date_upload = 0L
        }
//        Log.d(
//            "MangaBuff",
//            "chapter:\n" +
//                "name: " + chapter.name + "\n" +
//                "chapter_number: " + chapter.chapter_number + "\n" +
//                "date_upload: " + chapter.date_upload + "\n" +
//                "url: " + chapter.url + "\n",
//        )
        return chapter
    }

    override fun pageListParse(response: Response): List<Page> {
//        Log.d("MangaBuff", "pageListParse, response:\n" + response.toString())
        val body = response.asJsoup()
        val urlElems = body.select(".reader__item > img")

        val pageUrls = ArrayList<String>()
        for (urlElem in urlElems) {
//            Log.d(
//                "MangaBuff",
//                "urlElem: " + urlElem.toString() +
//                    "\n url: " + urlElem.attr("src"),
//            )

            if (urlElem.hasAttr("src")) {
                pageUrls.add(
                    urlElem.attr("src"),
                )
            } else {
                pageUrls.add(
                    urlElem.attr("data-src"),
                )
            }
        }
//        Log.d(
//            "MangaBuff",
//            "pageUrls (" + pageUrls.size + ":\n" +
//                "pageUrls: " + pageUrls.toString(),
//        )

        return pageUrls.mapIndexed { i, url -> Page(i, "", url) }
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlParse(document: Document) = ""

    companion object {
        private val simpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }
}
