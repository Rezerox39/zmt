package dev.abhi.zmt.domain.usecase

import android.net.Uri
import dev.abhi.zmt.domain.model.Spec
import dev.abhi.zmt.domain.model.Track
import dev.abhi.zmt.data.repository.TrackMediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetTrackTechUseCase @Inject constructor(
    private val trackMediaRepository: TrackMediaRepository,
) {
    suspend operator fun invoke(uri: Uri, track: Track?): List<Spec> =
        withContext(Dispatchers.IO) {
            trackMediaRepository.techSpecs(uri, track)
        }
}
