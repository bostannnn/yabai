package com.example.unifiedreader.core.model

/** What a tag says about a book: where it is set, when, or what it is about. */
enum class LibraryTagKind {
    PLACE,
    ERA,
    THEME,
}

/**
 * The app's guess at where [tag] goes, or null when it has none. Servers send tags as one flat list
 * ("Japan", "1930s", "Espionage"), so places come from a list of names that ships with the app and
 * eras from their shape (a decade, a century) or a list of named periods. A guess is only offered:
 * a tag goes where you put it ([LibraryTagSorting]) and stays in About until then. Case doesn't matter.
 */
fun libraryTagGuess(tag: String): LibraryTagChoice? {
    val key = libraryTagKey(tag)
    LibraryPlaceRegionByName[key]?.let { region -> return LibraryTagChoice(LibraryTagKind.PLACE, region) }
    return if (key in NamedSlots || key in NamedPeriods || DecadePattern.matches(key) || CenturyPattern.matches(key)) {
        LibraryTagChoice(LibraryTagKind.ERA)
    } else {
        null
    }
}

/** The part of the world a place is in, for grouping places in Settings › Tags. */
enum class LibraryPlaceRegion(val label: String) {
    AMERICAS("Americas"),
    EUROPE("Europe"),
    RUSSIA_AND_AROUND("Russia and around"),
    ASIA("Asia"),
    MIDDLE_EAST_AND_AFRICA("Middle East and Africa"),
    OCEANIA("Oceania"),
    MADE_UP("Made-up places"),
    ELSEWHERE("Elsewhere"),
}

/** The app's guess at the region of a place (a tag or a Kavita Location), or null when it doesn't know the name. */
fun libraryPlaceRegionGuess(name: String): LibraryPlaceRegion? = LibraryPlaceRegionByName[libraryTagKey(name)]

/**
 * A pile under When in Filters: decades and centuries in time order. A named period ("World War II",
 * "Cold War") and a whole century that spans many bars ("20th century") have no bar; they are
 * chips under the timeline.
 */
data class LibraryEraSlot(
    val label: String,
    val order: Int,
)

/** The timeline bar an era tag falls in, or null for a named period or a tag that isn't an era. */
fun libraryEraSlot(tag: String): LibraryEraSlot? {
    val key = libraryTagKey(tag)
    NamedSlots[key]?.let { return it }
    DecadePattern.matchEntire(key)?.let { match ->
        val year = match.groupValues[1].toInt()
        // From 1900 a tag like "1930s" is a decade; before, "1800s" means the century.
        return if (year >= 1900) LibraryEraSlot(label = "${year}s", order = 2000 + year) else centurySlot(year / 100 + 1)
    }
    CenturyPattern.matchEntire(key)?.let { match ->
        val century = match.groupValues[1].toInt()
        if (match.groupValues[2].isNotEmpty()) return LibraryEraSlot(label = "Ancient", order = 100)
        // The 20th century is too wide for one bar beside its own decades.
        return if (century == 20) null else centurySlot(century)
    }
    return null
}

/** The pile a When label names ("1800s", "Ancient", "21st c."), or null for a named period ("World War II"). */
fun libraryEraPile(label: String): LibraryEraSlot? =
    libraryEraSlot(label)?.takeIf { slot -> slot.label.equals(label, ignoreCase = true) } ?: PileByLabel[libraryTagKey(label)]

private val PileByLabel: Map<String, LibraryEraSlot> by lazy {
    (NamedSlots.values + listOfNotNull(centurySlot(21))).associateBy { slot -> libraryTagKey(slot.label) }
}

private fun centurySlot(century: Int): LibraryEraSlot? = when {
    century <= 5 -> LibraryEraSlot(label = "Ancient", order = 100)
    century <= 14 -> LibraryEraSlot(label = "Middle Ages", order = 200)
    century in 15..19 -> LibraryEraSlot(label = "${(century - 1) * 100}s", order = 1000 + century)
    century == 21 -> LibraryEraSlot(label = "21st c.", order = 5000)
    century > 21 -> FutureSlot
    else -> null
}

private val DecadePattern = Regex("""(\d{3}0)s""")
private val CenturyPattern = Regex("""(\d{1,2})(?:st|nd|rd|th) century( bc| bce)?""")
private val FutureSlot = LibraryEraSlot(label = "Future", order = 9000)

private val NamedSlots: Map<String, LibraryEraSlot> = mapOf(
    "prehistory" to LibraryEraSlot(label = "Prehistory", order = 0),
    "stone age" to LibraryEraSlot(label = "Prehistory", order = 0),
    "bronze age" to LibraryEraSlot(label = "Prehistory", order = 0),
    "iron age" to LibraryEraSlot(label = "Prehistory", order = 0),
    "ancient world" to LibraryEraSlot(label = "Ancient", order = 100),
    "antiquity" to LibraryEraSlot(label = "Ancient", order = 100),
    "classical antiquity" to LibraryEraSlot(label = "Ancient", order = 100),
    "middle ages" to LibraryEraSlot(label = "Middle Ages", order = 200),
    "medieval" to LibraryEraSlot(label = "Middle Ages", order = 200),
    "viking age" to LibraryEraSlot(label = "Middle Ages", order = 200),
    "renaissance" to LibraryEraSlot(label = "Renaissance", order = 300),
    "future" to FutureSlot,
    "near future" to FutureSlot,
    "far future" to FutureSlot,
)

private val NamedPeriods: Set<String> = setOf(
    "world war i", "world war ii", "first world war", "second world war", "cold war", "interwar period",
    "victorian era", "edwardian era", "regency era", "georgian era", "tudor era", "elizabethan era",
    "soviet era", "russian revolution", "russian civil war", "perestroika", "napoleonic wars",
    "french revolution", "industrial revolution", "enlightenment", "great depression", "gilded age",
    "belle époque", "belle epoque", "roaring twenties", "jazz age", "space age", "crusades",
    "hundred years' war", "thirty years' war", "american civil war", "american revolution",
    "vietnam war", "korean war", "spanish civil war", "edo period", "meiji era", "heian period",
    "reconstruction era", "wild west", "holocaust",
)

private fun regionOf(region: LibraryPlaceRegion, names: String): List<Pair<String, LibraryPlaceRegion>> =
    names.split(',').map { name -> libraryTagKey(name) to region }

private val LibraryPlaceRegionByName: Map<String, LibraryPlaceRegion> = (
    regionOf(
        LibraryPlaceRegion.AMERICAS,
        "Americas, North America, South America, Central America, Latin America, Caribbean, United States, USA, " +
            "America, New York, New York City, Brooklyn, Manhattan, Los Angeles, Hollywood, San Francisco, " +
            "Silicon Valley, California, Chicago, Boston, New Orleans, Washington, Washington D.C., Miami, " +
            "Florida, Texas, Las Vegas, Seattle, Detroit, Philadelphia, New England, Alaska, Hawaii, " +
            "American South, American West, Appalachia, Canada, Toronto, Montreal, Vancouver, Quebec, Mexico, " +
            "Mexico City, Cuba, Havana, Jamaica, Haiti, Bahamas, Puerto Rico, Dominican Republic, Guatemala, " +
            "Honduras, Nicaragua, Panama, Costa Rica, El Salvador, Colombia, Venezuela, Peru, Lima, Chile, " +
            "Argentina, Buenos Aires, Brazil, Rio de Janeiro, São Paulo, Sao Paulo, Bolivia, Ecuador, Uruguay, " +
            "Paraguay, Amazon, Patagonia",
    ) + regionOf(
        LibraryPlaceRegion.EUROPE,
        "Europe, Western Europe, Eastern Europe, Central Europe, Scandinavia, Nordic countries, Balkans, " +
            "Mediterranean, England, United Kingdom, Britain, Great Britain, UK, London, Oxford, Cambridge, " +
            "Manchester, Liverpool, Yorkshire, Cornwall, Scotland, Edinburgh, Glasgow, Highlands, Wales, " +
            "Ireland, Northern Ireland, Dublin, Belfast, France, Paris, Provence, Normandy, Marseille, Germany, " +
            "Berlin, Munich, Hamburg, Bavaria, Austria, Vienna, Switzerland, Geneva, Zurich, Italy, Rome, " +
            "Venice, Florence, Milan, Naples, Sicily, Tuscany, Spain, Madrid, Barcelona, Seville, Andalusia, " +
            "Portugal, Lisbon, Porto, Netherlands, Amsterdam, Belgium, Brussels, Luxembourg, Liechtenstein, " +
            "Monaco, Denmark, Copenhagen, Norway, Oslo, Sweden, Stockholm, Finland, Helsinki, Iceland, " +
            "Greenland, Poland, Warsaw, Krakow, Kraków, Czech Republic, Czechia, Prague, Slovakia, Hungary, " +
            "Budapest, Romania, Bucharest, Transylvania, Bulgaria, Sofia, Serbia, Belgrade, Croatia, " +
            "Bosnia, Sarajevo, Slovenia, Montenegro, Albania, North Macedonia, Macedonia, Greece, Athens, " +
            "Crete, Cyprus, Malta, Vatican City, Vatican, Estonia, Latvia, Riga, Lithuania, Vilnius, " +
            "Yugoslavia, Prussia",
    ) + regionOf(
        LibraryPlaceRegion.RUSSIA_AND_AROUND,
        "Russia, Soviet Union, USSR, Moscow, Saint Petersburg, St. Petersburg, Petersburg, Leningrad, " +
            "Petrograd, Siberia, Urals, Volga, Crimea, Ukraine, Kyiv, Kiev, Odesa, Odessa, Kharkiv, Lviv, " +
            "Belarus, Minsk, Moldova, Caucasus, Georgia, Tbilisi, Armenia, Yerevan, Azerbaijan, Baku, " +
            "Chechnya, Central Asia, Kazakhstan, Uzbekistan, Samarkand, Turkmenistan, Kyrgyzstan, Tajikistan",
    ) + regionOf(
        LibraryPlaceRegion.ASIA,
        "Asia, East Asia, Southeast Asia, South Asia, Japan, Tokyo, Kyoto, Osaka, Hiroshima, Nagasaki, " +
            "Hokkaido, Okinawa, China, Beijing, Peking, Shanghai, Hong Kong, Macau, Taiwan, Taipei, Tibet, " +
            "Manchuria, Korea, South Korea, Seoul, North Korea, Pyongyang, Mongolia, India, Delhi, New Delhi, " +
            "Mumbai, Bombay, Kolkata, Calcutta, Kashmir, Pakistan, Karachi, Lahore, Bangladesh, Sri Lanka, " +
            "Nepal, Kathmandu, Himalayas, Bhutan, Afghanistan, Kabul, Myanmar, Burma, Thailand, Bangkok, " +
            "Vietnam, Hanoi, Saigon, Ho Chi Minh City, Cambodia, Laos, Malaysia, Kuala Lumpur, Singapore, " +
            "Indonesia, Jakarta, Bali, Java, Philippines, Manila",
    ) + regionOf(
        LibraryPlaceRegion.MIDDLE_EAST_AND_AFRICA,
        "Middle East, Near East, Arabia, Israel, Jerusalem, Tel Aviv, Palestine, Gaza, Lebanon, Beirut, " +
            "Syria, Damascus, Jordan, Iraq, Baghdad, Iran, Persia, Tehran, Turkey, Istanbul, Constantinople, " +
            "Ottoman Empire, Anatolia, Saudi Arabia, Mecca, Yemen, Oman, Kuwait, Qatar, Dubai, " +
            "United Arab Emirates, Africa, North Africa, West Africa, East Africa, Southern Africa, Sahara, " +
            "Egypt, Cairo, Alexandria, Libya, Tunisia, Algeria, Algiers, Morocco, Casablanca, Tangier, Sudan, " +
            "Ethiopia, Eritrea, Somalia, Kenya, Nairobi, Tanzania, Zanzibar, Uganda, Rwanda, Burundi, Congo, " +
            "DR Congo, Nigeria, Lagos, Ghana, Senegal, Mali, Ivory Coast, Sierra Leone, Liberia, Cameroon, " +
            "Angola, Mozambique, Zimbabwe, Zambia, Botswana, Namibia, South Africa, Johannesburg, Cape Town, " +
            "Madagascar",
    ) + regionOf(
        LibraryPlaceRegion.OCEANIA,
        "Oceania, Australia, Sydney, Melbourne, Outback, New Zealand, Pacific Islands, Polynesia, Tahiti, " +
            "Papua New Guinea, Fiji",
    ) + regionOf(
        LibraryPlaceRegion.ELSEWHERE,
        "Arctic, Antarctica, Antarctic, North Pole, South Pole, Atlantic Ocean, Pacific Ocean, Indian Ocean, " +
            "At sea",
    )
    ).toMap()
