package com.example.myapplication.data.v8.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** إعدادات التطبيق العامة؛ يحافظ على بنية جدول settings الموجودة في V7. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String?,
    val description: String?
)
