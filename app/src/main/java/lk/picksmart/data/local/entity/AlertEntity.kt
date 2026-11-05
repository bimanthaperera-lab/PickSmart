package lk.picksmart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import lk.picksmart.domain.model.Alert
import lk.picksmart.domain.model.AlertLink
import lk.picksmart.domain.model.AlertType

/**
 * Room row for [Alert], the in-app inbox (CLAUDE.md section 9a). Alerts stay on the phone: they
 * have no sync columns and aren't backed up.
 */
@Entity(tableName = "alerts", indices = [Index("created_at"), Index(value = ["alert_key"], unique = true)])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AlertType,
    val title: String,
    val body: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    val read: Boolean,
    @ColumnInfo(name = "deep_link") val link: AlertLink?,
    @ColumnInfo(name = "alert_key") val key: String?,
)

fun AlertEntity.toDomain() = Alert(
    id = id,
    type = type,
    title = title,
    body = body,
    createdAtMillis = createdAtMillis,
    read = read,
    link = link,
    key = key,
)

fun Alert.toEntity() = AlertEntity(
    id = id,
    type = type,
    title = title,
    body = body,
    createdAtMillis = createdAtMillis,
    read = read,
    link = link,
    key = key,
)
