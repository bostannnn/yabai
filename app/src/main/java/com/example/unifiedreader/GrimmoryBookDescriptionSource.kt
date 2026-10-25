package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** A Grimmory book's description, which the catalog sync leaves out to stay light. */
class GrimmoryBookDescriptionSource @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) : RemoteBookDescriptionSource {
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override suspend fun fetch(item: ReadableItem): FetchedDescription? {
        val bookId = item.remoteId?.takeIf(String::isNotBlank) ?: return null
        val session = grimmoryCredentialStore.sessionFlow.value ?: return null
        return try {
            val text = runWithStoredSessionAddressFailover(
                sessionStore = grimmoryCredentialStore,
                session = session,
                createApi = grimmoryApiFactory::create,
                withActiveBaseUrl = { currentSession, activeBaseUrl ->
                    currentSession.copy(activeBaseUrl = activeBaseUrl)
                },
            ) { api, activeSession ->
                sessionRequestRunner.run(api = api, session = activeSession) { accessToken ->
                    api.getBookDescription(accessToken = accessToken, bookId = bookId)
                }
            }
            FetchedDescription(text)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
}
