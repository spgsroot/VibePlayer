package ru.spgsroot.vibeplayer.data.db

import androidx.room.Entity

@Entity(tableName = "tag_blacklist", primaryKeys = ["kind", "value"])
data class TagBlacklistEntity(val kind: String, val value: String)
