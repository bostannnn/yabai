package com.example.unifiedreader.core.model

/**
 * A country on Browse's Set in: how many books are set in it or in one of its cities, those cities,
 * and the first few of its books, for its covers.
 */
data class LibraryPlaceCountry(
    val name: String,
    val region: LibraryPlaceRegion,
    val count: Int,
    /** The cities in it that books are set in, most books first. */
    val cities: List<LibraryBrowseFacet>,
    val books: List<ReadableItem>,
)

/**
 * The country a city or a state the app knows is in ("New York" → "United States"), or null for a
 * country, a region or a name it doesn't know. Browse counts a city under its country, so the
 * country's card holds the city's books too. Places whose country is disputed stay on their own.
 */
fun libraryPlaceCountry(name: String): String? = CountryOfPlace[libraryTagKey(name)]

/**
 * Set in's places with each city folded into its country, most books first, tags placed by
 * [sorting]; a book counts once per country. A country is named as your tag spells it, or as the
 * app does when only its cities are tagged, and keeps up to [bookCount] of its books.
 */
fun libraryPlaceCountries(
    items: List<ReadableItem>,
    sorting: LibraryTagSorting,
    bookCount: Int = 3,
): List<LibraryPlaceCountry> {
    val tallies = linkedMapOf<String, CountryTally>()
    items.forEach { item ->
        val facets = item.facetMetadata()
        val places = facets.tags.filter { tag -> sorting.kindOf(tag) == LibraryTagKind.PLACE }
            .map { tag -> tag to sorting.regionOfTag(tag) } +
            facets.locations.map { location -> location to sorting.regionOfLocation(location) }
        places.distinctBy { (place, _) -> libraryTagKey(place) }
            .groupBy { (place, _) -> libraryTagKey(libraryPlaceCountry(place) ?: place) }
            .forEach { (key, countryPlaces) ->
                val own = countryPlaces.firstOrNull { (place, _) -> libraryPlaceCountry(place) == null }
                val tally = tallies.getOrPut(key) {
                    val (place, region) = own ?: countryPlaces.first()
                    CountryTally(name = own?.first ?: libraryPlaceCountry(place) ?: place, region = region, named = own != null)
                }
                if (own != null && !tally.named) {
                    tally.name = own.first
                    tally.region = own.second
                    tally.named = true
                }
                tally.count += 1
                tally.cities += countryPlaces.map { (place, _) -> place }.filter { place -> libraryPlaceCountry(place) != null }
                if (tally.books.size < bookCount) tally.books += item
            }
    }
    return tallies.values
        .map { tally ->
            LibraryPlaceCountry(
                name = tally.name,
                region = tally.region,
                count = tally.count,
                cities = countValues(tally.cities),
                books = tally.books.toList(),
            )
        }
        .sortedWith(compareByDescending<LibraryPlaceCountry> { country -> country.count }.thenBy { country -> libraryTagKey(country.name) })
}

private class CountryTally(
    var name: String,
    var region: LibraryPlaceRegion,
    var named: Boolean,
) {
    var count = 0
    val cities = mutableListOf<List<String>>()
    val books = mutableListOf<ReadableItem>()
}

private fun inCountry(country: String, places: String): List<Pair<String, String>> =
    places.split(',').map { place -> libraryTagKey(place) to country }

private val CountryOfPlace: Map<String, String> = (
    inCountry(
        "United States",
        "New York, New York City, Brooklyn, Manhattan, Los Angeles, Hollywood, San Francisco, Silicon Valley, " +
            "California, Chicago, Boston, New Orleans, Washington D.C., Miami, Florida, Texas, Las Vegas, Seattle, " +
            "Detroit, Philadelphia, New England, Alaska, Hawaii, American South, American West, Appalachia",
    ) + inCountry("Canada", "Toronto, Montreal, Vancouver, Quebec") +
        inCountry("Mexico", "Mexico City") +
        inCountry("Cuba", "Havana") +
        inCountry("Peru", "Lima") +
        inCountry("Argentina", "Buenos Aires") +
        inCountry("Brazil", "Rio de Janeiro, São Paulo, Sao Paulo") +
        inCountry("England", "London, Oxford, Cambridge, Manchester, Liverpool, Yorkshire, Cornwall") +
        inCountry("Scotland", "Edinburgh, Glasgow, Highlands") +
        inCountry("Northern Ireland", "Belfast") +
        inCountry("Ireland", "Dublin") +
        inCountry("France", "Paris, Provence, Normandy, Marseille") +
        inCountry("Germany", "Berlin, Munich, Hamburg, Bavaria") +
        inCountry("Austria", "Vienna") +
        inCountry("Switzerland", "Geneva, Zurich") +
        inCountry("Italy", "Rome, Venice, Florence, Milan, Naples, Sicily, Tuscany") +
        inCountry("Spain", "Madrid, Barcelona, Seville, Andalusia") +
        inCountry("Portugal", "Lisbon, Porto") +
        inCountry("Netherlands", "Amsterdam") +
        inCountry("Belgium", "Brussels") +
        inCountry("Denmark", "Copenhagen") +
        inCountry("Norway", "Oslo") +
        inCountry("Sweden", "Stockholm") +
        inCountry("Finland", "Helsinki") +
        inCountry("Poland", "Warsaw, Krakow, Kraków") +
        inCountry("Czech Republic", "Prague") +
        inCountry("Hungary", "Budapest") +
        inCountry("Romania", "Bucharest, Transylvania") +
        inCountry("Bulgaria", "Sofia") +
        inCountry("Serbia", "Belgrade") +
        inCountry("Bosnia and Herzegovina", "Sarajevo") +
        inCountry("Greece", "Athens, Crete") +
        inCountry("Latvia", "Riga") +
        inCountry("Lithuania", "Vilnius") +
        inCountry(
            "Russia",
            "Moscow, Saint Petersburg, St. Petersburg, Petersburg, Leningrad, Petrograd, Siberia, Urals, Volga, Chechnya",
        ) +
        inCountry("Ukraine", "Kyiv, Kiev, Odesa, Odessa, Kharkiv, Lviv") +
        inCountry("Belarus", "Minsk") +
        inCountry("Georgia", "Tbilisi") +
        inCountry("Armenia", "Yerevan") +
        inCountry("Azerbaijan", "Baku") +
        inCountry("Uzbekistan", "Samarkand") +
        inCountry("Japan", "Tokyo, Kyoto, Osaka, Hiroshima, Nagasaki, Hokkaido, Okinawa") +
        inCountry("China", "Beijing, Peking, Shanghai, Manchuria") +
        inCountry("Taiwan", "Taipei") +
        inCountry("South Korea", "Seoul") +
        inCountry("North Korea", "Pyongyang") +
        inCountry("India", "Delhi, New Delhi, Mumbai, Bombay, Kolkata, Calcutta") +
        inCountry("Pakistan", "Karachi, Lahore") +
        inCountry("Nepal", "Kathmandu") +
        inCountry("Afghanistan", "Kabul") +
        inCountry("Thailand", "Bangkok") +
        inCountry("Vietnam", "Hanoi, Saigon, Ho Chi Minh City") +
        inCountry("Malaysia", "Kuala Lumpur") +
        inCountry("Indonesia", "Jakarta, Bali, Java") +
        inCountry("Philippines", "Manila") +
        inCountry("Israel", "Tel Aviv") +
        inCountry("Lebanon", "Beirut") +
        inCountry("Syria", "Damascus") +
        inCountry("Iraq", "Baghdad") +
        inCountry("Iran", "Tehran") +
        inCountry("Turkey", "Istanbul, Anatolia") +
        inCountry("Saudi Arabia", "Mecca") +
        inCountry("United Arab Emirates", "Dubai") +
        inCountry("Egypt", "Cairo, Alexandria") +
        inCountry("Algeria", "Algiers") +
        inCountry("Morocco", "Casablanca, Tangier") +
        inCountry("Kenya", "Nairobi") +
        inCountry("Tanzania", "Zanzibar") +
        inCountry("Nigeria", "Lagos") +
        inCountry("South Africa", "Johannesburg, Cape Town") +
        inCountry("Australia", "Sydney, Melbourne, Outback")
    ).toMap()
