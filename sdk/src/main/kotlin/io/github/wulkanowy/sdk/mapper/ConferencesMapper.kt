package io.github.wulkanowy.sdk.mapper

import io.github.wulkanowy.sdk.pojo.Conference
import java.time.ZoneId
import io.github.wulkanowy.sdk.scrapper.conferences.Conference as ScrapperConference

internal fun List<ScrapperConference>.mapConferences(zoneId: ZoneId) = map {
    Conference(
        title = it.title,
        subject = it.subject,
        agenda = it.agenda,
        presentOnConference = it.presentOnConference,
        online = it.online,
        date = it.date.atZone(zoneId),
        id = it.id,
    )
}
