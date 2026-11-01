package com.example.unifiedreader.core.model

/**
 * The country a city or a state the app knows is in ("New York" → "United States"), or null for a
 * country, a region or a name it doesn't know. Filters counts a city under its country, so
 * picking the country takes the city's books too. Places whose country is disputed stay on their own.
 */
fun libraryPlaceCountry(name: String): String? = CountryOfPlace[libraryTagKey(name)]

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
